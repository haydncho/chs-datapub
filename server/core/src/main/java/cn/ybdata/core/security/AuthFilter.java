package cn.ybdata.core.security;

import cn.ybdata.core.auth.AuthProperties;
import cn.ybdata.core.auth.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
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
 *   <li>{@code Authorization: Bearer <token>} → session actor (invalid / expired / revoked → 401);</li>
 *   <li>public endpoints (login, SMS code, the A1 page payload) pass without credentials;</li>
 *   <li>{@code yb.auth.dev-header=true} (default): {@code X-YB-User} naming a known user acts as that
 *       user's primary identity; no credentials at all falls back to the legacy per-page demo identity
 *       (not access-checked) so the existing demo and tests keep working;</li>
 *   <li>{@code yb.auth.dev-header=false}: anything else → 401.</li>
 * </ol>
 * Enforced actors get 403 JSON when their identity may not reach the page / action / API area.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    private static final String API = "/api/v1/";

    private final AuthService auth;
    private final AuthProperties props;
    private final ObjectMapper json;

    public AuthFilter(AuthService auth, AuthProperties props, ObjectMapper json) {
        this.auth = auth;
        this.props = props;
        this.json = json;
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
        boolean open = isPublic(method, path);

        Actor actor = null;
        String authz = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authz != null && authz.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Optional<Actor> s = auth.resolve(authz.substring(7).trim());
            if (s.isEmpty() && !open) {
                deny(res, HttpServletResponse.SC_UNAUTHORIZED, "会话无效或已过期,请重新登录");
                return;
            }
            actor = s.orElse(null);
        }
        if (actor == null && !open) {
            if (!props.devHeader()) {
                deny(res, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
                return;
            }
            String header = req.getHeader("X-YB-User");
            actor = auth.devActor(header).orElseGet(() -> Actor.devDefault(legacyName(header)));
        }
        if (actor != null && actor.enforced() && !open) {
            Optional<String> why = AccessPolicy.check(actor.role(), method, path);
            if (why.isPresent()) {
                deny(res, HttpServletResponse.SC_FORBIDDEN, why.get());
                return;
            }
        }
        if (actor != null) req.setAttribute(Actor.REQUEST_ATTR, actor);
        chain.doFilter(req, res);
    }

    /** legacy free-text X-YB-User (audit_event.actor is varchar(32)) */
    private static String legacyName(String header) {
        if (header == null || header.isBlank()) return null;
        String h = header.trim();
        return h.length() > 32 ? h.substring(0, 32) : h;
    }

    private void deny(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        if (status == HttpServletResponse.SC_UNAUTHORIZED) res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(res.getOutputStream(), Map.of("error", message));
    }
}
