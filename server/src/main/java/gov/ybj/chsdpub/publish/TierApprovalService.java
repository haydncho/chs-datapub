package gov.ybj.chsdpub.publish;

import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 对标档位切换审批（A13 发起 → A8 召集人审批）。
 * 批准：更新 benchmark_tier.tier 并把申请单置 APPROVED；驳回：置 REJECTED（意见必填）。审批结论另记 pub_tier_decision。
 * 这是对第二批表 benchmark_tier / tier_change_request 的写入。
 */
@Service
public class TierApprovalService {

    public static final String GROUP = "对标档位切换";
    public static final List<String> TIERS = List.of("匿名分位", "匿名编号", "具名PK与排行");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ConvenerGuard guard;
    private final ZoneId zone;

    public TierApprovalService(JdbcTemplate jdbc, ConvenerGuard guard, AppProperties props) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.zone = ZoneId.of(props.zone());
    }

    public static String tierName(int t) {
        return t >= 0 && t < TIERS.size() ? TIERS.get(t) : "—";
    }

    public record TierRequest(long id, String indicator, int fromTier, int toTier, String fromName, String toName, int currentTier,
                              String reason, String approver, String requestedBy, String requestedAt, String status, boolean named) {}

    public TierRequest get(long id) {
        List<TierRequest> r = jdbc.query("""
                select r.id, r.indicator, r.from_tier, r.to_tier, t.tier, r.reason, r.approver, u.name, r.created_at, r.status
                from tier_change_request r join benchmark_tier t on t.indicator = r.indicator join app_user u on u.id = r.requested_by
                where r.id = ?""", (rs, i) -> new TierRequest(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                tierName(rs.getInt(3)), tierName(rs.getInt(4)), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getObject(9, OffsetDateTime.class).atZoneSameInstant(zone).format(TIME), rs.getString(10), rs.getInt(4) == 2), id);
        if (r.isEmpty()) throw ApiException.notFound("档位切换申请不存在");
        return r.get(0);
    }

    private TierRequest pending(long id) {
        List<Long> lock = jdbc.queryForList("select id from tier_change_request where id = ? for update", Long.class, id);
        if (lock.isEmpty()) throw ApiException.notFound("档位切换申请不存在");
        TierRequest t = get(id);
        if (!"PENDING".equals(t.status())) throw ApiException.conflict("该申请已处理");
        return t;
    }

    @Transactional
    public Map<String, Object> approve(long id, PublishService.DecisionReq req) {
        AuthUser u = guard.require("A8 批准对标档位切换 #" + id);
        TierRequest t = pending(id);
        String opinion = req == null || Texts.blank(req.opinion()) ? null : Texts.truncate(req.opinion().trim(), 256);
        jdbc.update("update benchmark_tier set tier = ? where indicator = ?", t.toTier(), t.indicator());
        jdbc.update("update tier_change_request set status = 'APPROVED' where id = ?", id);
        jdbc.update("insert into pub_tier_decision (request_id, decision, opinion, decided_by) values (?, 'APPROVED', ?, ?)", id, opinion, u.userId());
        guard.record(u, "A13 对标档位「" + t.indicator() + "」" + t.fromName() + " → " + t.toName(), "批准");
        return Map.of("message", "已批准 · 「" + t.indicator() + "」对标档位切换为" + t.toName());
    }

    @Transactional
    public Map<String, Object> reject(long id, PublishService.DecisionReq req) {
        AuthUser u = guard.require("A8 驳回对标档位切换 #" + id);
        if (req == null || Texts.blank(req.opinion())) throw ApiException.validation("请填写驳回意见");
        TierRequest t = pending(id);
        jdbc.update("update tier_change_request set status = 'REJECTED' where id = ?", id);
        jdbc.update("insert into pub_tier_decision (request_id, decision, opinion, decided_by) values (?, 'REJECTED', ?, ?)", id,
                Texts.truncate(req.opinion().trim(), 256), u.userId());
        guard.record(u, "A13 对标档位「" + t.indicator() + "」" + t.fromName() + " → " + t.toName(), "驳回");
        return Map.of("message", "已驳回 · 「" + t.indicator() + "」保持" + tierName(t.currentTier()));
    }
}
