package cn.ybdata.core.security;

import cn.ybdata.core.audit.AuditService;
import cn.ybdata.core.auth.AuthProperties;
import cn.ybdata.core.auth.AuthService;
import cn.ybdata.core.page.PageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the actor of every {@code /api/v1/**} call and enforces {@link AccessPolicy}.
 *
 * <ol>
 *   <li>public endpoints (login, SMS code, the A1 page payload) pass without credentials;</li>
 *   <li>{@code Authorization: Bearer <token>} → session actor (invalid / expired / revoked → 401);</li>
 *   <li>tests only, {@code yb.auth.dev-header=true} (default false): {@code X-YB-User} naming a known,
 *       enabled user acts as that user's primary identity — access-checked like a session;</li>
 *   <li>anything else → 401. There is no unauthenticated fallback identity.</li>
 * </ol>
 * Actors whose identity may not reach the page / action / API area get 403 JSON, and the refusal is
 * written to the audit trail ({@code accessDenied}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private static final String API = "/api/v1/";

    private final AuthService auth;
    private final AuthProperties props;
    private final ObjectMapper json;
    private final AuditService audit;

    public AuthFilter(AuthService auth, AuthProperties props, ObjectMapper json, AuditService audit) {
        this.auth = auth;
        this.props = props;
        this.json = json;
        this.audit = audit;
    }

    /** decoded, container-normalised path (no ;params, no ..) */
    private static String path(HttpServletRequest req) {
        String p = req.getServletPath();
        if (req.getPathInfo() != null) p += req.getPathInfo();
        return p;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !path(req).startsWith(API);
    }

    static boolean isPublic(String method, String path) {
        return ("POST".equals(method) && (path.equals("auth/login") || path.equals("auth/sms-code")))
                || ("GET".equals(method) && path.equals("pages/A1"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String method = req.getMethod();
        if ("OPTIONS".equals(method)) {
            chain.doFilter(req, res);
            return;
        }
        String path = path(req).substring(API.length());
        if (isPublic(method, path)) {
            chain.doFilter(req, res);
            return;
        }

        Actor actor = null;
        String authz = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authz != null && authz.regionMatches(true, 0, "Bearer ", 0, 7)) {
            actor = auth.resolve(authz.substring(7).trim()).orElse(null);
            if (actor == null) {
                deny(res, HttpServletResponse.SC_UNAUTHORIZED, "会话无效或已过期,请重新登录");
                return;
            }
        } else if (props.devHeader()) {
            actor = auth.devActor(req.getHeader("X-YB-User")).orElse(null);
        }
        if (actor == null) {
            deny(res, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }
        Optional<String> why = AccessPolicy.check(actor.role(), method, path);
        if (why.isPresent()) {
            recordDenied(actor, method, path, why.get(), req);
            deny(res, HttpServletResponse.SC_FORBIDDEN, why.get());
            return;
        }
        req.setAttribute(Actor.REQUEST_ATTR, actor);
        chain.doFilter(req, res);
    }

    /** A14 keeps refused calls too: page = the screen the call aimed at, action {@code accessDenied}. */
    private void recordDenied(Actor actor, String method, String path, String reason, HttpServletRequest req) {
        String page = AccessPolicy.pageOf(path);
        if (page == null || !PageService.CODE.matcher(page).matches()) return;
        String target = path.length() > 120 ? path.substring(0, 120) : path;
        try {
            audit.record(actor.name(), page, AccessPolicy.DENIED_ACTION,
                    json.valueToTree(Map.of("request", method + " " + target, "reason", reason, "role", String.valueOf(actor.role()))),
                    CurrentActor.terminal(req));
        } catch (RuntimeException e) {
            log.warn("could not audit refused call {} {}: {}", method, target, e.toString());
        }
    }

    private void deny(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        if (status == HttpServletResponse.SC_UNAUTHORIZED) res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(res.getOutputStream(), Map.of("error", message));
    }
}
