package cn.ybdata.core.auth;

import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified login (A1).
 * <pre>
 * POST /api/v1/auth/sms-code {account}                             → {ok, cooldown, phone?}      (429 within 60s)
 * POST /api/v1/auth/login    {method:'cert', account, pin}         → session
 *                            {method:'sms',  account, code}
 * GET  /api/v1/auth/me                                             → session (user, identity, identities, viewer, pages)
 * POST /api/v1/auth/identity {identity: user_identity.id}          → session with a new token (old one revoked)
 * POST /api/v1/auth/logout                                         → {ok}
 * </pre>
 * A session response is {@code me} plus {@code token} (send as {@code Authorization: Bearer <token>}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/sms-code")
    public Map<String, Object> smsCode(@RequestBody(required = false) JsonNode body) {
        AuthService.SmsSent sent = auth.requestSmsCode(text(body, "account"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("cooldown", sent.cooldownSeconds());
        if (sent.maskedPhone() != null) out.put("phone", sent.maskedPhone());
        return out;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody(required = false) JsonNode body, HttpServletRequest req) {
        String method = text(body, "method");
        String secret = "cert".equals(method) ? text(body, "pin") : text(body, "code");
        AuthService.Issued s = auth.login(method, text(body, "account"), secret, req.getRemoteAddr(), CurrentActor.terminal(req));
        return session(s);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        return auth.me(CurrentActor.get(req));
    }

    @PostMapping("/identity")
    public Map<String, Object> identity(@RequestBody(required = false) JsonNode body, HttpServletRequest req) {
        JsonNode id = body == null ? null : body.get("identity");
        if (id == null || !id.canConvertToLong()) throw new AuthException(HttpStatus.BAD_REQUEST, "缺少 identity");
        return session(auth.switchIdentity(CurrentActor.get(req), id.asLong(), req.getRemoteAddr(), CurrentActor.terminal(req)));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        auth.logout(CurrentActor.get(req), CurrentActor.terminal(req));
        return Map.of("ok", true);
    }

    private Map<String, Object> session(AuthService.Issued s) {
        Actor a = s.actor();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", s.token());
        out.putAll(auth.me(a));
        return out;
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Map<String, Object>> failed(AuthException e) {
        var r = ResponseEntity.status(e.status());
        if (e.retryAfterSeconds() != null) r.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        if (e.retryAfterSeconds() != null) body.put("retryAfter", e.retryAfterSeconds());
        return r.body(body);
    }

    private static String text(JsonNode body, String field) {
        if (body == null) return null;
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
