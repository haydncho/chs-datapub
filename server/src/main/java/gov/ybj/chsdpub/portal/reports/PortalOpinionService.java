package gov.ybj.chsdpub.portal.reports;

import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * B5 机构核对与我的意见。
 * <ul>
 *   <li>核对期：逐项「确认 / 有异议」；截止（种子数据为部署次日 18:00）后不再接受操作，未处理项视为确认。</li>
 *   <li>提交意见：必须关联本院的具体指标或报告段落、说明必填；写入第二批意见工单表 opinion_ticket（承办人周婷），
 *       医保局 A10 即可看到并答复。核对期内针对核对稿（或由「有异议」发起）的意见归为「核对期异议」，须在核对截止前答复。</li>
 *   <li>评价：只能对已答复的本院工单评价 1–5 星，写回 opinion_ticket.rating。</li>
 * </ul>
 */
@Service
public class PortalOpinionService {

    public static final String CHECK_CATEGORY = "核对期异议";
    public static final List<String> CATEGORIES = List.of("数据异议", "分组规则", "申诉", "咨询");
    public static final String HANDLER = "周婷";
    /** 一般意见承办时限：10 个工作日。 */
    public static final int WORKDAYS = 10;
    private static final int MAX_TEXT = 1000;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ZoneId zone;
    private final Clock clock;

    public PortalOpinionService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(props.zone());
        this.clock = Clock.system(zone);
    }

    // ---------------------------------------------------------------- 核对
    public record CheckItem(int idx, String label, String value, String decision, String state, String decidedAt, String ticketNo) {}

    public record CheckRound(long id, String title, long reportId, OffsetDateTime deadline, String deadlineLabel, long serverNow,
                             boolean closed, List<CheckItem> items) {}

    private record RoundRow(long id, String title, long reportId, OffsetDateTime deadline) {}

    private RoundRow roundRow(String org) {
        return jdbc.query("select id, title, report_id, deadline from pr_check_round where org = ? order by deadline desc limit 1",
                (rs, i) -> new RoundRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getObject(4, OffsetDateTime.class)), org)
                .stream().findFirst().orElse(null);
    }

    private boolean closed(RoundRow r) {
        return !Instant.now(clock).isBefore(r.deadline().toInstant());
    }

    /** 本院当前核对轮次；无核对任务时返回 null。 */
    public CheckRound round(String org) {
        RoundRow r = roundRow(org);
        if (r == null) return null;
        boolean closed = closed(r);
        List<CheckItem> items = jdbc.query("""
                select idx, label, value, decision, decided_at, ticket_no from pr_check_item where round_id = ? order by idx""", (rs, i) -> {
            String d = rs.getString(4);
            // 状态：OK 已确认 / OBJECT 已提异议 / AUTO 逾期视为确认 / OPEN 待核对
            String state = d != null ? d : closed ? "AUTO" : "OPEN";
            OffsetDateTime at = rs.getObject(5, OffsetDateTime.class);
            return new CheckItem(rs.getInt(1), rs.getString(2), rs.getString(3), d, state,
                    at == null ? null : at.atZoneSameInstant(zone).format(MDHM), rs.getString(6));
        }, r.id());
        return new CheckRound(r.id(), r.title(), r.reportId(), r.deadline().atZoneSameInstant(zone).toOffsetDateTime(),
                r.deadline().atZoneSameInstant(zone).format(MDHM), Instant.now(clock).toEpochMilli(), closed, items);
    }

    @Transactional
    public CheckRound decide(AuthUser u, int idx, String decision) {
        if (!"OK".equals(decision) && !"OBJECT".equals(decision)) throw ApiException.validation("请选择确认或有异议");
        RoundRow r = roundRow(u.org());
        if (r == null) throw ApiException.notFound("当前没有核对任务");
        if (closed(r)) throw ApiException.conflict("核对期已截止,未处理项视为确认");
        List<String> cur = jdbc.queryForList("select coalesce(decision, '') from pr_check_item where round_id = ? and idx = ? for update",
                String.class, r.id(), idx);
        if (cur.isEmpty()) throw ApiException.notFound("核对项不存在");
        if (!cur.get(0).isEmpty()) throw ApiException.conflict("该项已处理");
        jdbc.update("update pr_check_item set decision = ?, decided_at = now(), decided_by = ? where round_id = ? and idx = ?",
                decision, u.name(), r.id(), idx);
        return round(u.org());
    }

    // ---------------------------------------------------------------- 意见
    public record Ref(long id, String label, Long reportId) {}

    public record Opinion(String no, String ref, String category, String content, String status, String reply, String repliedAt,
                          Integer rating, String createdAt, String dueLabel) {}

    public List<Ref> refs(String org) {
        return jdbc.query("select id, label, report_id from pr_opinion_ref where org = ? order by sort",
                (rs, i) -> new Ref(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3)), org);
    }

    /** 本院意见工单（读第二批 opinion_ticket，按机构过滤）。 */
    public List<Opinion> mine(String org) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.query("""
                select no, ref, category, content, status, reply, replied_at, rating, created_at, due_date
                from opinion_ticket where org = ? order by created_at desc, no desc""", (rs, i) -> {
            String st = rs.getString(5);
            OffsetDateTime rep = rs.getObject(7, OffsetDateTime.class);
            LocalDate due = rs.getObject(10, LocalDate.class);
            String dueLabel = null;
            if (!"DONE".equals(st) && due != null) {
                long d = ChronoUnit.DAYS.between(today, due);
                dueLabel = d < 0 ? "已超期 " + (-d) + " 天" : d == 0 ? "今日到期" : "剩 " + d + " 天";
            }
            return new Opinion(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), st, rs.getString(6),
                    rep == null ? null : rep.atZoneSameInstant(zone).format(MDHM), (Integer) rs.getObject(8),
                    rs.getObject(9, LocalDate.class).format(MD), dueLabel);
        }, org);
    }

    public record SubmitReq(Long refId, String category, String text, Integer checkItem) {}

    public record Submitted(String no, String category, String owner, LocalDate dueDate, String message) {}

    @Transactional
    public Submitted submit(AuthUser u, SubmitReq req) {
        if (req == null || req.refId() == null) throw ApiException.validation("必须关联具体指标或报告段落");
        Ref ref = refs(u.org()).stream().filter(x -> x.id() == req.refId()).findFirst()
                .orElseThrow(() -> ApiException.validation("必须关联具体指标或报告段落"));
        if (Texts.blank(req.text())) throw ApiException.validation("请填写说明");
        String text = req.text().trim();
        if (text.length() > MAX_TEXT) throw ApiException.validation("说明不超过 " + MAX_TEXT + " 字");
        String chosen = Texts.blank(req.category()) ? CATEGORIES.get(0) : req.category();
        if (!CATEGORIES.contains(chosen)) throw ApiException.validation("意见类别不正确");

        RoundRow round = roundRow(u.org());
        boolean open = round != null && !closed(round);
        if (req.checkItem() != null) {
            if (!open) throw ApiException.conflict("核对期已截止,未处理项视为确认");
            List<Map<String, Object>> it = jdbc.queryForList(
                    "select decision, ticket_no from pr_check_item where round_id = ? and idx = ? for update", round.id(), req.checkItem());
            if (it.isEmpty()) throw ApiException.notFound("核对项不存在");
            if ("OK".equals(it.get(0).get("decision"))) throw ApiException.conflict("该项已确认,不能再提异议");
            if (it.get(0).get("ticket_no") != null) throw ApiException.conflict("该项已提交异议工单 " + it.get(0).get("ticket_no"));
        }
        boolean checkPeriod = open && (req.checkItem() != null || (ref.reportId() != null && ref.reportId() == round.reportId()));
        String category = checkPeriod ? CHECK_CATEGORY : chosen;

        LocalDate today = LocalDate.now(clock);
        LocalDate due = checkPeriod ? round.deadline().atZoneSameInstant(zone).toLocalDate() : addWorkdays(today, WORKDAYS);
        Long seq = jdbc.queryForObject("select nextval('pr_opinion_seq')", Long.class);
        String no = "YJ-" + today.format(DateTimeFormatter.ofPattern("MMdd")) + "-" + String.format("%03d", seq);
        jdbc.update("""
                insert into opinion_ticket (no, org, ref, category, owner, due_date, status, content, created_at)
                values (?, ?, ?, ?, ?, ?, 'WAIT', ?, ?)""", no, u.org(), Texts.truncate(ref.label(), 64), category, HANDLER, due, text, today);
        if (req.checkItem() != null) {
            jdbc.update("""
                    update pr_check_item set decision = 'OBJECT', decided_at = coalesce(decided_at, now()), decided_by = coalesce(decided_by, ?),
                           ticket_no = ? where round_id = ? and idx = ?""", u.name(), no, round.id(), req.checkItem());
        }
        String msg = checkPeriod
                ? "已提交核对期异议,工单 " + no + ",须在核对截止(" + due.format(MD) + ")前答复"
                : "已提交,工单 " + no + ",承办时限 " + WORKDAYS + " 个工作日";
        return new Submitted(no, category, HANDLER, due, msg);
    }

    public record RateReq(Integer rating) {}

    @Transactional
    public Opinion rate(AuthUser u, String no, RateReq req) {
        if (req == null || req.rating() == null || req.rating() < 1 || req.rating() > 5) throw ApiException.validation("评价为 1–5 星");
        List<String> st = jdbc.queryForList("select status from opinion_ticket where no = ? and org = ?", String.class, no, u.org());
        if (st.isEmpty()) throw ApiException.notFound("工单不存在");
        if (!"DONE".equals(st.get(0))) throw ApiException.conflict("工单尚未答复,答复后才能评价");
        jdbc.update("update opinion_ticket set rating = ? where no = ? and org = ?", req.rating(), no, u.org());
        return mine(u.org()).stream().filter(o -> o.no().equals(no)).findFirst().orElseThrow();
    }

    static LocalDate addWorkdays(LocalDate from, int days) {
        LocalDate d = from;
        int n = 0;
        while (n < days) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) n++;
        }
        return d;
    }
}
