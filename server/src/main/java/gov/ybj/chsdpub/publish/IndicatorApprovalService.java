package gov.ybj.chsdpub.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 指标上线审批(A4 提交 → A8 召集人审批)。
 * <ul>
 *   <li>批准:指标「审批中」→「已上线」(v1.0);非仅内部指标登记到 A13 对标档位表,档位按申请的档位(审批单一并报批);</li>
 *   <li>驳回(意见必填):删除审批中的指标记录,A4 草稿解锁并显示驳回意见,可修改后重新提交(新审批单号)。</li>
 * </ul>
 * 对 ind_indicator / ind_change_log / ind_draft(指标组)与 benchmark_tier(第二批)有写入。
 */
@Service
public class IndicatorApprovalService {

    public static final String GROUP = "指标上线审批";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ConvenerGuard guard;
    private final ObjectMapper om;
    private final ZoneId zone;

    public IndicatorApprovalService(JdbcTemplate jdbc, ConvenerGuard guard, ObjectMapper om, AppProperties props) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.om = om;
        this.zone = ZoneId.of(props.zone());
    }

    public record IndicatorRequest(long id, String approvalNo, String code, String name, String grp, String domain, String formula,
                                   String scope, boolean internal, Integer tier, String tierName, String owner, String requestedBy,
                                   String requestedAt, String status, String template, String unit) {}

    private record Row(long draftId, long indicatorId, String approvalNo, String status, JsonNode config) {}

    private Row row(long draftId, boolean lock) {
        List<Row> r = jdbc.query("""
                select d.id, d.indicator_id, d.approval_no, i.status, d.config::text
                from ind_draft d join ind_indicator i on i.id = d.indicator_id where d.id = ? and d.submitted_at is not null"""
                + (lock ? " for update of d" : ""), (rs, n) -> {
            try {
                return new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), om.readTree(rs.getString(5)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, draftId);
        if (r.isEmpty()) throw ApiException.notFound("上线审批单不存在");
        return r.get(0);
    }

    private Row pending(long draftId) {
        Row r = row(draftId, true);
        if (!"审批中".equals(r.status())) throw ApiException.conflict("该审批单已处理");
        return r;
    }

    public IndicatorRequest get(long draftId) {
        Row r = row(draftId, false);
        Map<String, Object> m = jdbc.queryForMap("""
                select i.code, i.name, i.grp, i.domain, i.formula, i.scope, i.owner, u.name as who, d.submitted_at
                from ind_indicator i join ind_draft d on d.indicator_id = i.id join app_user u on u.id = d.created_by where i.id = ?""",
                r.indicatorId());
        boolean internal = r.config().path("internal").asBoolean(false);
        Integer tier = internal ? null : r.config().path("tier").asInt(0);
        return new IndicatorRequest(draftId, r.approvalNo(), (String) m.get("code"), (String) m.get("name"), (String) m.get("grp"),
                (String) m.get("domain"), (String) m.get("formula"), (String) m.get("scope"), internal, tier,
                tier == null ? null : TierApprovalService.tierName(tier), (String) m.get("owner"), (String) m.get("who"),
                ((java.sql.Timestamp) m.get("submitted_at")).toInstant().atZone(zone).format(TIME), r.status(),
                r.config().path("template").asText(""), r.config().path("unit").asText(""));
    }

    /** 待办:已提交且审批中的上线审批单。 */
    public List<PublishService.TodoItem> todos() {
        return jdbc.query("""
                select d.id, i.name, d.approval_no, u.name from ind_draft d join ind_indicator i on i.id = d.indicator_id
                join app_user u on u.id = d.created_by where d.submitted_at is not null and i.status = '审批中' order by d.submitted_at, d.id""",
                (rs, n) -> new PublishService.TodoItem("indicator", rs.getLong(1), rs.getString(2) + " 上线", "审批单 " + rs.getString(3),
                        "warning", rs.getString(4) + " 申请", "muted", "A4"));
    }

    @Transactional
    public Map<String, Object> approve(long draftId, PublishService.DecisionReq req) {
        AuthUser u = guard.require("A8 批准指标上线 #" + draftId);
        Row r = pending(draftId);
        IndicatorRequest q = get(draftId);
        String opinion = req == null || Texts.blank(req.opinion()) ? null : Texts.truncate(req.opinion().trim(), 256);
        jdbc.update("update ind_indicator set status = '已上线', version = 'v1.0', tier = ? where id = ?", q.tier(), r.indicatorId());
        if (!q.internal()) {
            // 登记到 A13 对标档位表(第二批):审批单一并报批的档位在批准时生效
            jdbc.update("""
                    insert into benchmark_tier (indicator, tier, sort) values (?, ?, (select coalesce(max(sort), 0) + 1 from benchmark_tier))
                    on conflict (indicator) do nothing""", q.name(), q.tier());
        }
        jdbc.update("update ind_change_log set sort = sort + 1 where indicator_id = ?", r.indicatorId());
        jdbc.update("insert into ind_change_log (indicator_id, label, changed_on, author, note, sort) values (?, 'v1.0', current_date, ?, ?, 1)",
                r.indicatorId(), u.name(), "上线审批通过(审批单 " + r.approvalNo() + ")" + (opinion == null ? "" : ":" + opinion));
        jdbc.update("insert into pub_indicator_decision (draft_id, approval_no, decision, opinion, decided_by) values (?, ?, 'APPROVED', ?, ?)",
                draftId, r.approvalNo(), opinion, u.userId());
        guard.record(u, "A4 指标「" + q.name() + "」上线审批 " + r.approvalNo(), "批准");
        return Map.of("message", "已批准 · 「" + q.name() + "」已上线" + (q.internal() ? "(仅内部,不进入发布包)" : ",对标档位为" + q.tierName()));
    }

    @Transactional
    public Map<String, Object> reject(long draftId, PublishService.DecisionReq req) {
        AuthUser u = guard.require("A8 驳回指标上线 #" + draftId);
        if (req == null || Texts.blank(req.opinion())) throw ApiException.validation("请填写驳回意见");
        Row r = pending(draftId);
        IndicatorRequest q = get(draftId);
        jdbc.update("insert into pub_indicator_decision (draft_id, approval_no, decision, opinion, decided_by) values (?, ?, 'REJECTED', ?, ?)",
                draftId, r.approvalNo(), Texts.truncate(req.opinion().trim(), 256), u.userId());
        // 审批中的指标记录撤销;草稿解锁,提交人在 A4 修改后可重新提交(新审批单号)
        jdbc.update("update ind_draft set submitted_at = null, approval_no = null, indicator_id = null where id = ?", draftId);
        jdbc.update("delete from ind_change_log where indicator_id = ?", r.indicatorId());
        jdbc.update("delete from ind_package_item where indicator_id = ?", r.indicatorId());
        jdbc.update("delete from ind_indicator where id = ?", r.indicatorId());
        guard.record(u, "A4 指标「" + q.name() + "」上线审批 " + r.approvalNo(), "驳回");
        return Map.of("message", "已驳回 · 「" + q.name() + "」退回 A4,提交人修改后可重新提交");
    }
}
