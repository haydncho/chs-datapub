package cn.ybdata.core.action;

import cn.ybdata.core.audit.AuditService;
import cn.ybdata.core.page.PageService;
import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.AccessPolicy;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/actions/{page}/{action}: user actions, written to the hash-chained audit trail.
 * Actions with a registered {@link ActionHandler} also update domain state in the same transaction.
 *
 * <p>Only known actions are accepted: a registered handler, or one of {@link #RECORD_ONLY} (UI steps
 * the platform keeps a trace of but that change no stored state yet). Anything else is a 400 and is
 * not audited, so the trail cannot be filled with invented events.
 *
 * <p>The payload must be a JSON object of at most {@link #MAX_BODY} bytes (413 otherwise); strings
 * may not contain NUL or exceed {@link #MAX_TEXT} characters, nesting is limited to {@link #MAX_DEPTH},
 * and the integer fields in {@link #INT_FIELDS} must be whole numbers — all 400.
 *
 * <p>The actor is the session user resolved by {@link cn.ybdata.core.security.AuthFilter}.
 */
@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    static final int MAX_BODY = 64 * 1024;
    static final int MAX_TEXT = 8000;
    static final int MAX_DEPTH = 16;
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    /** payload fields that are whole numbers wherever they occur (e.g. A8 toStep 2.9 must not become 2) */
    static final Set<String> INT_FIELDS = Set.of("toStep", "step", "section", "template", "attempt", "count", "identity", "rows");

    /**
     * UI actions without domain state that are still recorded (page/action). Collected from the
     * {@code sendAction} / {@code runAction} calls in web/src; an action that gets a handler later
     * may stay listed (a handler always wins).
     */
    static final Set<String> RECORD_ONLY = Set.of(
            // A3 数据归集中心
            "A3/retryPull", "A3/notifyContact", "A3/completeQualityCheck", "A3/generateMonthlyReport",
            // A4 指标配置
            "A4/addToPackage", "A4/requestTierChange", "A4/submitIndicator",
            // A5 图表与报告模板
            "A5/addChart", "A5/saveTemplateVersion",
            // A6 智能推荐
            "A6/adoptTopic", "A6/skipTopic",
            // A7 病种专题
            "A7/approveSection", "A7/regenerateSection", "A7/resolveComment", "A7/submitReview",
            "A7/exportCommentsExcel", "A7/pushComments",
            // A8 发布工作流
            "A8/urgeSign", "A8/urgeSignAll", "A8/startCorrection", "A8/startWithdraw",
            // A9 流程设计器
            "A9/publishFlowVersion",
            // A11 预警提醒
            "A11/addToTopic",
            // B4 / B6 / C3 / D1
            "B4/exportReport", "B6/completeCourse", "C3/submitSuggestion",
            "D1/markRead", "D1/requestReview",
            // 全景图
            "cockpit/ackAlarm", "cockpit/saveSubscription");

    private final AuditService audit;
    private final ObjectMapper json;
    private final Map<String, ActionHandler> handlers;

    public ActionController(AuditService audit, ObjectMapper json, List<ActionHandler> handlers) {
        this.audit = audit;
        // exact decimals (1e400 or 0.1000000000000000001 survive), single document only
        this.json = json.copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.handlers = handlers.stream().collect(Collectors.toMap(h -> h.page() + "/" + h.action(), Function.identity()));
    }

    /** true when {@code page/action} may be posted (registered handler or record-only). */
    boolean known(String page, String action) {
        String key = page + "/" + action;
        return handlers.containsKey(key) || RECORD_ONLY.contains(key);
    }

    @PostMapping("/{page}/{action}")
    @Transactional
    public ResponseEntity<Map<String, Object>> act(@PathVariable String page, @PathVariable String action,
                                                   HttpServletRequest req) throws IOException {
        if (!PageService.CODE.matcher(page).matches() || !NAME.matcher(action).matches() || !known(page, action)) {
            return error(HttpStatus.BAD_REQUEST, "未知操作: " + clip(page, 16) + "/" + clip(action, 64));
        }
        Actor who = CurrentActor.get(req);
        if (who == null) return error(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!AccessPolicy.canAct(who.role(), page, action)) {
            throw new AccessDeniedException("当前身份无权执行 " + page + "/" + action);
        }
        byte[] raw = readLimited(req.getInputStream());
        if (raw == null) return error(HttpStatus.PAYLOAD_TOO_LARGE, "操作参数过大(上限 " + MAX_BODY / 1024 + " KB)");
        JsonNode body = parse(raw);
        validate(body, 0);

        ActionHandler h = handlers.get(page + "/" + action);
        Object result = h == null ? null : h.handle(who.name(), body);
        var ev = audit.record(who.name(), page, action, body, CurrentActor.terminal(req));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("auditId", ev.id());
        out.put("hash", ev.hash());
        out.put("handled", h != null);
        if (result != null) out.put("result", result);
        return ResponseEntity.ok(out);
    }

    /** @return the body, or null when it exceeds {@link #MAX_BODY} */
    static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (buf.size() + n > MAX_BODY) return null;
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    JsonNode parse(byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.isBlank()) return json.createObjectNode();
        JsonNode node;
        try {
            node = json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("操作参数不是合法的 JSON");
        }
        if (node == null || node.isNull() || node.isMissingNode()) return json.createObjectNode();
        if (!node.isObject()) throw new IllegalArgumentException("操作参数须为 JSON 对象");
        return node;
    }

    /** NUL bytes (PostgreSQL text/jsonb reject them), over-long strings, deep nesting, fractional integers. */
    static void validate(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("操作参数嵌套过深");
        if (node.isTextual()) {
            String s = node.textValue();
            if (s.indexOf('\u0000') >= 0) throw new IllegalArgumentException("操作参数含非法字符");
            if (s.length() > MAX_TEXT) throw new IllegalArgumentException("字段过长(上限 " + MAX_TEXT + " 字)");
        } else if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                var e = it.next();
                if (e.getKey().indexOf('\u0000') >= 0 || e.getKey().length() > 64) throw new IllegalArgumentException("操作参数含非法字段名");
                JsonNode v = e.getValue();
                if (INT_FIELDS.contains(e.getKey()) && v.isNumber() && !isWhole(v)) {
                    throw new IllegalArgumentException(e.getKey() + " 须为整数");
                }
                validate(v, depth + 1);
            }
        } else if (node.isArray()) {
            for (JsonNode v : node) validate(v, depth + 1);
        }
    }

    private static boolean isWhole(JsonNode v) {
        if (v.isIntegralNumber()) return true;
        try {
            v.decimalValue().toBigIntegerExact();
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
