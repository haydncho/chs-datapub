package cn.ybdata.core.audit;

import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A14 审计日志 API.
 *
 * <pre>
 * GET /api/v1/audit?type=&actor=&page=&action=&from=&to=&offHours=&cursor=&limit=   → {items, nextCursor, today}
 * GET /api/v1/audit/{id}                                                           → one event incl. hashes + diff
 * GET /api/v1/audit/export.csv?(same filters)                                      → UTF-8 BOM CSV, itself audited
 * GET /api/v1/audit/verify                                                         → {valid, checked, brokenAt}
 * </pre>
 * {@code from}/{@code to}: ISO date (to = whole day inclusive) or ISO date-time (platform zone when no offset).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    /** A14 demo identity (安全审计员) until the request carries an authenticated user. */
    static final String DEFAULT_ACTOR = "赵安";

    private final AuditService audit;
    private final ObjectMapper json;

    public AuditController(AuditService audit, ObjectMapper json) {
        this.audit = audit;
        this.json = json;
    }

    @GetMapping
    public AuditService.AuditPage list(@RequestParam(required = false) String type,
                                       @RequestParam(required = false) String actor,
                                       @RequestParam(required = false) String page,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) Boolean offHours,
                                       @RequestParam(required = false) Long cursor,
                                       @RequestParam(defaultValue = "50") int limit) {
        return audit.search(query(type, actor, page, action, from, to, offHours, cursor, limit));
    }

    @GetMapping("/{id:\\d+}")
    public AuditService.AuditView detail(@PathVariable long id) {
        return audit.detail(id);
    }

    @GetMapping("/verify")
    public AuditService.ChainCheck verify() {
        return audit.verify();
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String actor,
                                         @RequestParam(required = false) String page,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) Boolean offHours,
                                         @RequestHeader(value = "X-YB-User", required = false) String user,
                                         HttpServletRequest req) {
        var q = query(type, actor, page, action, from, to, offHours, null, AuditService.MAX_EXPORT);
        List<AuditService.AuditView> rows = audit.exportRows(q);

        // the export is itself an audited event (导出), recorded before the file leaves the server
        ObjectNode filters = json.createObjectNode();
        if (q.type() != null) filters.put("type", q.type());
        if (q.actor() != null) filters.put("actor", q.actor());
        if (q.page() != null) filters.put("page", q.page());
        if (q.action() != null) filters.put("action", q.action());
        if (q.from() != null) filters.put("from", q.from().toString());
        if (q.to() != null) filters.put("to", q.to().toString());
        if (q.offHours() != null) filters.put("offHours", q.offHours());
        ObjectNode payload = json.createObjectNode();
        payload.set("filters", filters);
        payload.put("rows", rows.size());
        payload.put("format", "csv");
        if (!rows.isEmpty()) {
            payload.put("firstId", rows.get(rows.size() - 1).id());
            payload.put("lastId", rows.get(0).id());
        }
        String name = "审计日志-" + LocalDate.now(AuditTypes.ZONE) + ".csv";
        payload.put("fileName", name);
        var ev = audit.record(actorOf(req, user), "A14", "exportAudit", payload, terminal(req));

        byte[] body = AuditCsv.render(rows, AuditService.watermark(ev.hash()));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-export.csv\"; filename*=UTF-8''"
                        + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"))
                .header("X-YB-Audit-Id", String.valueOf(ev.id()))
                .header("X-YB-Watermark", AuditService.watermark(ev.hash()))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-YB-Audit-Id, X-YB-Watermark, Content-Disposition")
                .body(body);
    }

    static AuditService.AuditQuery query(String type, String actor, String page, String action, String from, String to,
                                         Boolean offHours, Long cursor, int limit) {
        return new AuditService.AuditQuery(blank(type), blank(actor), blank(page), blank(action),
                parseTime(from, false), parseTime(to, true), offHours, cursor, limit);
    }

    /** ISO date → start of that day (or of the next day when {@code end}, making the range inclusive). */
    static OffsetDateTime parseTime(String s, boolean end) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try {
            if (v.length() == 10) {
                LocalDate d = LocalDate.parse(v);
                return (end ? d.plusDays(1) : d).atStartOfDay(AuditTypes.ZONE).toOffsetDateTime();
            }
            try {
                return OffsetDateTime.parse(v);
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(AuditTypes.ZONE).toOffsetDateTime();
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("bad time " + s + " (expect yyyy-MM-dd or ISO date-time)");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** the session user resolved by the auth filter; else the legacy header; else the A14 demo auditor */
    private static String actorOf(HttpServletRequest req, String header) {
        Actor a = CurrentActor.get(req);
        if (a != null && a.name() != null && !a.name().isBlank()) return cap(a.name(), 32);
        if (header != null && !header.isBlank()) return cap(header.trim(), 32);
        return DEFAULT_ACTOR;
    }

    private static String terminal(HttpServletRequest req) {
        // keep the browser token (the column is varchar(64); a raw UA would be cut before "Chrome/…")
        return cap(req.getRemoteAddr() + " · " + AuditService.browser(req.getHeader("User-Agent")), 64);
    }

    private static String cap(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
