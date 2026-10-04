package cn.ybdata.core.action;

import cn.ybdata.core.audit.AuditService;
import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.AccessPolicy;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/actions/{page}/{action}: every user action is written to the
 * hash-chained audit trail; actions with a registered {@link ActionHandler}
 * also update domain state in the same transaction.
 *
 * <p>The actor is the session user resolved by {@link cn.ybdata.core.security.AuthFilter}
 * (or, with {@code yb.auth.dev-header=true}, the {@code X-YB-User} header / per-page demo identity).
 */
@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private final AuditService audit;
    private final ObjectMapper json;
    private final Map<String, ActionHandler> handlers;

    public ActionController(AuditService audit, ObjectMapper json, List<ActionHandler> handlers) {
        this.audit = audit;
        this.json = json;
        this.handlers = handlers.stream().collect(Collectors.toMap(h -> h.page() + "/" + h.action(), Function.identity()));
    }

    @PostMapping("/{page}/{action}")
    @Transactional
    public Map<String, Object> act(@PathVariable String page, @PathVariable String action,
                                   @RequestBody(required = false) JsonNode payload,
                                   HttpServletRequest req) {
        if (!cn.ybdata.core.page.PageService.CODE.matcher(page).matches() || !NAME.matcher(action).matches()) {
            throw new IllegalArgumentException("bad action " + page + "/" + action);
        }
        JsonNode body = payload == null ? json.createObjectNode() : payload;
        Actor who = CurrentActor.get(req);
        if (who != null && who.enforced() && !AccessPolicy.canAct(who.role(), page, action)) {
            throw new AccessDeniedException("当前身份无权执行 " + page + "/" + action);
        }
        String actor = who != null && who.name() != null ? who.name() : Actors.defaultFor(page);
        String terminal = CurrentActor.terminal(req);

        ActionHandler h = handlers.get(page + "/" + action);
        Object result = h == null ? null : h.handle(actor, body);
        var ev = audit.record(actor, page, action, body, terminal);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("auditId", ev.id());
        out.put("hash", ev.hash());
        out.put("handled", h != null);
        if (result != null) out.put("result", result);
        return out;
    }
}
