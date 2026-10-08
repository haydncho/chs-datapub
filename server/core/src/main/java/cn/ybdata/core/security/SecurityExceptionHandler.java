package cn.ybdata.core.security;

import cn.ybdata.core.audit.AuditService;
import cn.ybdata.core.page.PageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 403 for refusals raised inside controllers / action handlers (e.g. 申请人不能复核自己). The refusal is
 * audited like the ones {@link AuthFilter} answers itself; the handler's own transaction has already been
 * rolled back, so the audit event is written in a fresh one.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);
    private static final String API = "/api/v1/";

    private final AuditService audit;
    private final ObjectMapper json;

    public SecurityExceptionHandler(AuditService audit, ObjectMapper json) {
        this.audit = audit;
        this.json = json;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e, HttpServletRequest req) {
        record(e.getMessage(), req);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    private void record(String reason, HttpServletRequest req) {
        Actor actor = CurrentActor.get(req);
        String uri = req.getRequestURI();
        if (actor == null || uri == null || !uri.startsWith(API)) return;
        String path = uri.substring(API.length());
        String page = AccessPolicy.pageOf(path);
        if (page == null || !PageService.CODE.matcher(page).matches()) return;
        String target = path.length() > 120 ? path.substring(0, 120) : path;
        try {
            audit.record(actor.name(), page, AccessPolicy.DENIED_ACTION,
                    json.valueToTree(Map.of("request", req.getMethod() + " " + target, "reason", String.valueOf(reason),
                            "role", String.valueOf(actor.role()))),
                    CurrentActor.terminal(req));
        } catch (RuntimeException ex) {
            log.warn("could not audit refused call {}: {}", target, ex.toString());
        }
    }
}
