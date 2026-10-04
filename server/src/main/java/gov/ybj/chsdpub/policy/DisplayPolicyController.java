package gov.ybj.chsdpub.policy;

import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 展示策略（A13）：「谁在看 × 数据归谁」四象限；对标三档（匿名分位 / 匿名编号 / 具名PK与排行）。
 * 切换档位须提交召集人审批，审批通过前按原档位发布。
 */
@RestController
@RequestMapping("/api/v1")
public class DisplayPolicyController {

    public static final List<String> TIERS = List.of("匿名分位", "匿名编号", "具名PK与排行");

    private final JdbcTemplate jdbc;

    public DisplayPolicyController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Quadrant(int id, String title, String summary, String tone, String audience, String granularity, String naming,
                           String threshold, boolean fixedNone) {}

    public record Pending(long id, int toTier, String approver) {}

    public record Tier(String indicator, int tier, Pending pending) {}

    @GetMapping("/display-policy/quadrants")
    public List<Quadrant> quadrants() {
        return jdbc.query("select id, title, summary, tone, audience, granularity, naming, threshold, fixed_none from display_quadrant order by id",
                (rs, i) -> new Quadrant(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getBoolean(9)));
    }

    @GetMapping("/benchmark-tiers")
    public Map<String, Object> tiers() {
        List<Tier> rows = jdbc.query("""
                select t.indicator, t.tier, r.id, r.to_tier, r.approver from benchmark_tier t
                left join tier_change_request r on r.indicator = t.indicator and r.status = 'PENDING' order by t.sort""",
                (rs, i) -> new Tier(rs.getString(1), rs.getInt(2),
                        rs.getObject(3) == null ? null : new Pending(rs.getLong(3), rs.getInt(4), rs.getString(5))));
        return Map.of("tierNames", TIERS, "rows", rows, "approver", approver());
    }

    private String approver() {
        List<String> r = jdbc.queryForList("""
                select u.name from user_identity i join app_user u on u.id = i.user_id where i.role = 'CONVENER' order by i.id limit 1""",
                String.class);
        return (r.isEmpty() ? "召集人" : r.get(0)) + "(召集人)";
    }

    public record ChangeReq(Integer toTier, String reason) {}

    @PostMapping("/benchmark-tiers/{indicator}/change-requests")
    @Transactional
    public Tier request(@PathVariable String indicator, @RequestBody ChangeReq req) {
        List<Integer> cur = jdbc.queryForList("select tier from benchmark_tier where indicator = ?", Integer.class, indicator);
        if (cur.isEmpty()) throw ApiException.notFound("指标不存在");
        if (req.toTier() == null || req.toTier() < 0 || req.toTier() > 2) throw ApiException.validation("目标档位无效");
        if (req.toTier().equals(cur.get(0))) throw ApiException.validation("目标档位与当前档位相同");
        if (Texts.blank(req.reason())) throw ApiException.validation("请填写变更理由");
        jdbc.update("""
                insert into tier_change_request (indicator, from_tier, to_tier, reason, approver, requested_by) values (?,?,?,?,?,?)""",
                indicator, cur.get(0), req.toTier(), Texts.truncate(req.reason().trim(), 256), approver(), CurrentUser.get().userId());
        @SuppressWarnings("unchecked")
        List<Tier> rows = (List<Tier>) tiers().get("rows");
        return rows.stream().filter(t -> t.indicator().equals(indicator)).findFirst().orElseThrow();
    }
}
