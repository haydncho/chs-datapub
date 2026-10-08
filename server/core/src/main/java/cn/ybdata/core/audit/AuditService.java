package cn.ybdata.core.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit trail. Every event stores SHA-256(prevHash | at | actor | page | action | payload),
 * so any edit or deletion breaks the chain (A14 链式签名校验).
 */
@Service
public class AuditService {

    static final String GENESIS = "0".repeat(64);
    static final int MAX_PAGE = 500;
    static final int MAX_EXPORT = 50_000;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ObjectMapper sorted;

    public AuditService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.sorted = json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** Raw stored event (as returned by {@link #record}). */
    public record AuditEvent(long id, OffsetDateTime at, String actor, String page, String action,
                             JsonNode payload, String terminal, String prevHash, String hash) {}

    public record ChainCheck(boolean valid, long checked, Long brokenAt) {}

    /** Filters for the A14 list / export; null = not filtered. */
    public record AuditQuery(String type, String actor, String page, String action, OffsetDateTime from,
                             OffsetDateTime to, Boolean offHours, Long cursor, int limit) {
        public AuditQuery {
            if (type != null && !AuditTypes.isType(type)) throw new IllegalArgumentException("unknown event type " + type);
            if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        }
    }

    /**
     * One event as A14 shows it.
     *
     * @param chain {@code ok} when the event's own hash recomputes and its prevHash equals the preceding
     *              event's hash; {@code broken} otherwise (edited, or a predecessor was deleted)
     */
    public record AuditView(long id, OffsetDateTime at, String date, String time, String type, String actor,
                            String who, String role, String page, String action, String label, String object,
                            String ip, String terminal, String watermark, String chain, boolean offHours,
                            boolean risk, SettingDiff.Diff diff, JsonNode payload, String prevHash, String hash) {}

    public record AuditPage(List<AuditView> items, Long nextCursor, long today) {}

    // ── write ────────────────────────────────────────────────────────────────

    @Transactional
    public AuditEvent record(String actor, String page, String action, JsonNode payload, String terminal) {
        // serialise appends so the chain stays linear
        jdbc.sql("select pg_advisory_xact_lock(7311)").query().singleRow();
        String prev = jdbc.sql("select hash from audit_event order by id desc limit 1")
                .query(String.class).optional().orElse(GENESIS);
        // microsecond precision matches what PostgreSQL stores, so verify() recomputes the same hash
        OffsetDateTime at = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        // hash exactly what verify() will read back: jsonb normalises numbers (1e2 → 100, 1.0E+2 …) and key
        // order, so the payload is put through PostgreSQL first and the stored text is hashed
        String body = jdbc.sql("select cast(:p as jsonb)::text").param("p", raw(payload)).query(String.class).single();
        String hash = chainHash(prev, at, actor, page, action, canonical(parse(body)));
        long id = jdbc.sql("""
                insert into audit_event (at, actor, page, action, payload, terminal, prev_hash, hash)
                values (:at, :actor, :page, :action, cast(:payload as jsonb), :terminal, :prev, :hash)
                returning id""")
                .param("at", at).param("actor", actor).param("page", page).param("action", action)
                .param("payload", body).param("terminal", terminal).param("prev", prev).param("hash", hash)
                .query(Long.class).single();
        return new AuditEvent(id, at, actor, page, action, parse(body), terminal, prev, hash);
    }

    // ── read ─────────────────────────────────────────────────────────────────

    private static final String SELECT = """
            select e.id, e.at, e.actor, e.page, e.action, e.payload::text as payload, e.terminal, e.prev_hash, e.hash,
                   coalesce((select p.hash from audit_event p where p.id < e.id order by p.id desc limit 1), '%s') as prev_actual,
                   u.name as user_name, r.name as role_name
            from audit_event e
            left join lateral (select x.name, x.role_code from app_user x where x.name = e.actor or x.login = e.actor
                               order by (x.login = e.actor) desc limit 1) u on true
            left join app_role r on r.code = u.role_code
            """.formatted(GENESIS);

    private static final String WHERE = """
            where (cast(:type as text) is null or %s = :type)
              and (cast(:actor as text) is null or position(lower(:actor) in lower(e.actor)) > 0
                   or position(lower(:actor) in lower(coalesce(u.name, ''))) > 0)
              and (cast(:page as text) is null or e.page = :page)
              and (cast(:action as text) is null or e.action = :action)
              and (cast(:from as timestamptz) is null or e.at >= :from)
              and (cast(:to as timestamptz) is null or e.at < :to)
              and (cast(:off as boolean) is null or %s = :off)
              and (cast(:cursor as bigint) is null or e.id < :cursor)
            """.formatted(AuditTypes.sqlCase("e.action"), AuditTypes.sqlOffHours("e.at"));

    public AuditPage search(AuditQuery q) {
        int limit = Math.min(Math.max(q.limit(), 1), MAX_PAGE);
        List<AuditView> rows = query(q, limit + 1);
        Long next = null;
        if (rows.size() > limit) {
            rows = new ArrayList<>(rows.subList(0, limit));
            next = rows.get(limit - 1).id();
        }
        return new AuditPage(rows, next, countToday());
    }

    /** All matching events (newest first) for CSV export, capped at {@link #MAX_EXPORT}. */
    public List<AuditView> exportRows(AuditQuery q) {
        return query(q, MAX_EXPORT);
    }

    private List<AuditView> query(AuditQuery q, int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbc.sql(SELECT + WHERE + " order by e.id desc limit :limit")
                .param("type", q.type()).param("actor", blankToNull(q.actor())).param("page", blankToNull(q.page()))
                .param("action", blankToNull(q.action())).param("from", q.from()).param("to", q.to())
                .param("off", q.offHours()).param("cursor", q.cursor()).param("limit", limit)
                .query((rs, i) -> view(rs, now))
                .list();
    }

    public AuditView detail(long id) {
        return jdbc.sql(SELECT + " where e.id = :id")
                .param("id", id)
                .query((rs, i) -> view(rs, OffsetDateTime.now()))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("audit event " + id + " not found"));
    }

    long countToday() {
        OffsetDateTime start = LocalDate.now(AuditTypes.ZONE).atStartOfDay(AuditTypes.ZONE).toOffsetDateTime();
        return jdbc.sql("select count(*) from audit_event where at >= :s").param("s", start).query(Long.class).single();
    }

    private AuditView view(ResultSet rs, OffsetDateTime now) throws SQLException {
        long id = rs.getLong("id");
        OffsetDateTime at = rs.getObject("at", OffsetDateTime.class);
        String actor = rs.getString("actor");
        String page = rs.getString("page");
        String action = rs.getString("action");
        JsonNode payload = parse(rs.getString("payload"));
        String prevHash = rs.getString("prev_hash");
        String hash = rs.getString("hash");
        boolean selfOk = hash.equals(chainHash(prevHash, at, actor, page, action, canonical(payload)));
        boolean linkOk = prevHash.equals(rs.getString("prev_actual"));
        AuditTypes.Kind kind = AuditTypes.kind(action);
        String[] term = splitTerminal(rs.getString("terminal"));
        String userName = rs.getString("user_name");
        SettingDiff.Diff diff = SettingDiff.applies(page, action) ? diff(id, page, action, payload) : null;
        return new AuditView(id, at, at.atZoneSameInstant(AuditTypes.ZONE).toLocalDate().toString(), displayTime(at, now),
                kind.type(), actor, userName != null ? userName : actor, roleOr(rs.getString("role_name")), page, action,
                kind.label(), describe(page, kind, payload), term[0], term[1], watermark(hash),
                selfOk && linkOk ? "ok" : "broken", AuditTypes.isOffHours(at), AuditTypes.isRisk(kind.type(), at),
                diff, payload, prevHash, hash);
    }

    /** Before/after for A13 / A15 settings: "before" is the value written by the previous event of the same setting. */
    SettingDiff.Diff diff(long id, String page, String action, JsonNode payload) {
        JsonNode prev;
        if ("A13".equals(page)) {
            prev = jdbc.sql("""
                    select payload::text from audit_event
                    where page = 'A13' and action = 'setPolicyRule' and payload->>'key' = :k and id < :id
                    order by id desc limit 1""")
                    .param("k", payload.path("key").asText()).param("id", id)
                    .query(String.class).optional().map(this::parse).orElse(null);
        } else {
            prev = jdbc.sql("""
                    select action, payload::text as payload from audit_event
                    where page = 'A15' and action in ('publishAppearance', 'resetAppearance') and id < :id
                    order by id desc limit 1""")
                    .param("id", id)
                    .query((rs, i) -> "resetAppearance".equals(rs.getString("action")) ? null : parse(rs.getString("payload")))
                    .optional().orElse(null);
        }
        return SettingDiff.compute(page, action, payload, prev);
    }

    /** Re-hash the whole chain in insertion order. */
    public ChainCheck verify() {
        String[] prev = {GENESIS};
        long[] n = {0};
        Long[] broken = {null};
        jdbc.sql("select id, at, actor, page, action, payload::text as payload, prev_hash, hash from audit_event order by id")
                .query(rs -> {
                    if (broken[0] != null) return;
                    n[0]++;
                    String expect = chainHash(prev[0], rs.getObject("at", OffsetDateTime.class), rs.getString("actor"),
                            rs.getString("page"), rs.getString("action"), canonical(parse(rs.getString("payload"))));
                    if (!prev[0].equals(rs.getString("prev_hash")) || !expect.equals(rs.getString("hash"))) {
                        broken[0] = rs.getLong("id");
                    }
                    prev[0] = rs.getString("hash");
                });
        return new ChainCheck(broken[0] == null, n[0], broken[0]);
    }

    // ── derivations ──────────────────────────────────────────────────────────

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);

    /** A14 time column: today "09:12:04", yesterday "昨天 17:40", older "09-28 17:40". */
    static String displayTime(OffsetDateTime at, OffsetDateTime now) {
        var t = at.atZoneSameInstant(AuditTypes.ZONE);
        LocalDate today = now.atZoneSameInstant(AuditTypes.ZONE).toLocalDate();
        if (t.toLocalDate().equals(today)) return t.format(HMS);
        if (t.toLocalDate().equals(today.minusDays(1))) return "昨天 " + t.format(HM);
        return t.format(MDHM);
    }

    /** Watermark trace id derived from the event hash, e.g. WM-7F3A-11C2. */
    static String watermark(String hash) {
        String h = hash.toUpperCase(Locale.ROOT);
        return "WM-" + h.substring(0, 4) + "-" + h.substring(4, 8);
    }

    private static final Map<String, String> PAGE_TITLES = Map.ofEntries(
            Map.entry("cockpit", "全景图"), Map.entry("A1", "登录与身份"),
            Map.entry("A3", "数据归集中心"), Map.entry("A4", "指标配置"), Map.entry("A5", "图表与报告模板"),
            Map.entry("A6", "智能推荐"), Map.entry("A7", "病种专题"), Map.entry("A8", "发布工作流"),
            Map.entry("A9", "流程设计器"), Map.entry("A10", "意见与申诉"), Map.entry("A11", "预警提醒"),
            Map.entry("A12", "用户权限"), Map.entry("A13", "展示策略"), Map.entry("A14", "审计日志"),
            Map.entry("A15", "外观配置"), Map.entry("B1", "本院全景"), Map.entry("B2", "病组下钻"),
            Map.entry("B3", "对标PK"), Map.entry("B4", "报告中心"), Map.entry("B5", "意见核对"),
            Map.entry("B6", "政策培训"), Map.entry("B7", "区域外"), Map.entry("C3", "外部监督"),
            Map.entry("D1", "移动端"));

    public static String pageTitle(String page) {
        return PAGE_TITLES.getOrDefault(page, page);
    }

    /** payload fields that name the object acted on, most specific first. */
    private static final List<String> SUBJECT_KEYS = List.of("login", "request", "report", "name", "title", "taskId", "alertId", "alert",
            "indicator", "flow", "source", "docNo", "org", "institution", "message", "target", "id", "key");

    /** A14 对象 column, e.g. "签收报告 · 2026年8月 本院医保运行报告". */
    static String describe(String page, AuditTypes.Kind kind, JsonNode payload) {
        // actions outside the table carry their raw name as label: prefix the page title so the row still reads
        String head = kind.known() ? kind.label() : pageTitle(page) + " · " + kind.label();
        if (payload != null && "A12".equals(page)) {
            // 用户权限: say what happened to which account
            if (payload.path("enabled").isBoolean()) head = payload.get("enabled").asBoolean() ? "启用账号" : "停用账号";
            if (payload.path("approve").isBoolean()) head = payload.get("approve").asBoolean() ? "复核通过新增用户" : "驳回新增用户";
        }
        String subject = null;
        if ("A13".equals(page) && payload.has("key")) {
            subject = SettingDiff.compute("A13", "setPolicyRule", payload, null).changes().get(0).label();
        } else if (payload != null && (!"A15".equals(page) || payload.has("request"))) { // A15 payload is the whole appearance: its diff says it all
            for (String k : SUBJECT_KEYS) {
                JsonNode v = payload.get(k);
                if (v != null && v.isValueNode() && !v.asText().isBlank()) {
                    subject = v.asText();
                    break;
                }
            }
        }
        if (subject == null && payload != null && payload.path("rows").isNumber()) subject = payload.get("rows").asInt() + " 条";
        if (subject != null && subject.length() > 60) subject = subject.substring(0, 60) + "…";
        return subject == null ? head : head + " · " + subject;
    }

    /** stored terminal is "ip · user-agent". */
    static String[] splitTerminal(String t) {
        if (t == null || t.isBlank()) return new String[] {"—", "—"};
        int i = t.indexOf(" · ");
        String ip = i < 0 ? t : t.substring(0, i);
        String ua = i < 0 ? "" : t.substring(i + 3);
        return new String[] {ip.isBlank() ? "—" : ip, browser(ua)};
    }

    /** "… Chrome/128.0.0.0 …" → "Chrome 128"; falls back to the raw (truncated) string. */
    public static String browser(String ua) {
        if (ua == null || ua.isBlank()) return "—";
        for (var b : BROWSERS) {
            var m = b.getValue().matcher(ua);
            if (m.find()) return b.getKey() + " " + m.group(1);
        }
        return ua;
    }

    /** Edge UAs also contain "Chrome/" and "Safari/", so the most specific token is tried first. */
    private static final List<Map.Entry<String, java.util.regex.Pattern>> BROWSERS = List.of(
            Map.entry("Edge", java.util.regex.Pattern.compile("Edg/(\\d+)")),
            Map.entry("Firefox", java.util.regex.Pattern.compile("Firefox/(\\d+)")),
            Map.entry("Chrome", java.util.regex.Pattern.compile("Chrome/(\\d+)")),
            Map.entry("Safari", java.util.regex.Pattern.compile("Version/(\\d+).*Safari/")));

    private static String roleOr(String role) {
        return role == null ? "—" : role;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ── hashing ──────────────────────────────────────────────────────────────

    static String chainHash(String prev, OffsetDateTime at, String actor, String page, String action, String body) {
        return sha256(prev + "|" + at.toInstant() + "|" + actor + "|" + page + "|" + action + "|" + body);
    }

    JsonNode parse(String s) {
        try {
            return json.readTree(s == null ? "{}" : s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The payload as JSON text, numbers exactly as given (BigDecimal stays plain). */
    private String raw(JsonNode node) {
        try {
            return json.writeValueAsString(node == null ? json.createObjectNode() : node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** jsonb normalises key order, so hash a key-sorted rendering. */
    private String canonical(JsonNode node) {
        try {
            Object tree = json.treeToValue(node == null ? json.createObjectNode() : node, Object.class);
            return sorted.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
