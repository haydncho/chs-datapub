package gov.ybj.chsdpub.alert;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/** 预警提醒（A11，流程 5）：规则触发 → 提醒函 → 机构回执 → 整改跟踪 → 销号。 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private static final List<String> ORDER = List.of("GEN", "SENT", "RCPT", "FIX", "CLOSED");
    private static final String[][] TRACK = {
            {"生成提醒函", "系统按规则生成,行政管理组核定"}, {"发出提醒函", "经定向发布通道送达,机构签收"},
            {"机构回执", "10 个工作日内提交原因说明"}, {"整改跟踪", "连续 2 期监测"}, {"销号", "指标回落至阈值内"}};

    private final JdbcTemplate jdbc;

    public AlertController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Rule(long id, String name, String scope, String condition, String frequency, int hits, boolean enabled) {}

    public record Step(String name, String desc, boolean done) {}

    public record Letter(String no, String org, String group, String rule, String value, String period) {}

    public record Trigger(long id, String date, String org, String group, String rule, String value, String status,
                          String receipt, Letter letter, List<Step> track) {}

    @GetMapping("/rules")
    public List<Rule> rules() {
        return jdbc.query("select id, name, scope, condition, frequency, hits, enabled from alert_rule order by sort",
                (rs, i) -> new Rule(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                        rs.getBoolean(7)));
    }

    public record EnableReq(boolean enabled) {}

    @PutMapping("/rules/{id}")
    @Transactional
    public Rule toggle(@PathVariable long id, @RequestBody EnableReq req) {
        if (jdbc.update("update alert_rule set enabled = ? where id = ?", req.enabled(), id) == 0) throw ApiException.notFound("规则不存在");
        return rules().stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }

    @GetMapping("/triggers")
    public List<Trigger> triggers() {
        return jdbc.query("""
                select id, trig_date, org, drg_group, rule_name, value, status, receipt, letter_seq, period
                from alert_trigger order by sort""", (rs, i) -> {
            String st = rs.getString(7);
            int k = ORDER.indexOf(st);
            List<Step> track = new ArrayList<>();
            for (int j = 0; j < TRACK.length; j++) track.add(new Step(TRACK[j][0], TRACK[j][1], j <= k));
            Letter letter = new Letter("示医保提〔2026〕" + rs.getInt(9) + "号", rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getString(10));
            return new Trigger(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                    st, rs.getString(8), letter, track);
        });
    }

    private Trigger one(long id) {
        return triggers().stream().filter(t -> t.id() == id).findFirst().orElseThrow(() -> ApiException.notFound("触发记录不存在"));
    }

    @PostMapping("/triggers/{id}/send")
    @Transactional
    public Trigger send(@PathVariable long id) {
        if (!"GEN".equals(one(id).status())) throw ApiException.conflict("提醒函已发出");
        jdbc.update("update alert_trigger set status = 'SENT', sent_at = now() where id = ?", id);
        return one(id);
    }

    public record ReceiptReq(String text) {}

    /** 登记机构回执（机构门户提交或线下回执由经办登记）。 */
    @PostMapping("/triggers/{id}/receipt")
    @Transactional
    public Trigger receipt(@PathVariable long id, @RequestBody(required = false) ReceiptReq req) {
        if (!"SENT".equals(one(id).status())) throw ApiException.conflict("当前状态不能登记回执");
        String text = req == null || Texts.blank(req.text())
                ? "已组织科室分析,主要原因为高值耗材使用增加,拟于下月起执行耗材使用审批。" : req.text().trim();
        jdbc.update("update alert_trigger set status = 'RCPT', receipt = ?, receipt_at = now() where id = ?", text, id);
        return one(id);
    }
}
