package gov.ybj.chsdpub.portal.reports;

import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预警提醒函与回执(流程 5 的机构端):本院收到的提醒函列表,10 个工作日内通过机构门户提交回执(原因说明必填);
 * 回执提交后 A11 状态变为「已回执」并显示回执摘要。只能处理本院、已发出待回执的记录。
 */
@RestController
@RequestMapping("/api/v1/portal/alerts")
public class PortalAlertController {

    private static final Map<String, String> LABEL = Map.of("SENT", "待回执", "RCPT", "已回执", "FIX", "整改中", "CLOSED", "已销号");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PortalAlertController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record Letter(long id, String letterNo, String rule, String group, String period, String value, String status,
                         String statusLabel, String receipt, boolean canReceipt) {}

    private List<Letter> letters(String org, Long id) {
        return jdbc.query("""
                select id, letter_seq, rule_name, drg_group, period, value, status, receipt from alert_trigger
                where org = ? and status in ('SENT', 'RCPT', 'FIX', 'CLOSED')""" + (id == null ? "" : " and id = ?") + " order by sort",
                (rs, i) -> new Letter(rs.getLong(1), "示医保提〔2026〕" + rs.getInt(2) + "号", rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), LABEL.get(rs.getString(7)), rs.getString(8), "SENT".equals(rs.getString(7))),
                id == null ? new Object[]{org} : new Object[]{org, id});
    }

    @GetMapping
    public List<Letter> list() {
        return letters(CurrentUser.get().org(), null);
    }

    public record ReceiptReq(String text) {}

    @PostMapping("/{id}/receipt")
    @Transactional
    public Letter receipt(@PathVariable long id, @RequestBody(required = false) ReceiptReq req) {
        AuthUser u = CurrentUser.get();
        if (req == null || Texts.blank(req.text())) throw ApiException.validation("请填写回执说明(原因分析与整改措施)");
        // 先锁定本院记录,再校验状态,避免并发重复回执
        List<String> st = jdbc.queryForList("select status from alert_trigger where id = ? and org = ? for update", String.class, id, u.org());
        if (st.isEmpty()) throw ApiException.notFound("提醒函不存在");
        if (!"SENT".equals(st.get(0))) throw ApiException.conflict("该提醒函" + ("RCPT".equals(st.get(0)) ? "已提交回执" : "当前不需要回执"));
        jdbc.update("update alert_trigger set status = 'RCPT', receipt = ?, receipt_at = now() where id = ?", Texts.truncate(req.text().trim(), 500), id);
        Letter l = letters(u.org(), id).get(0);
        audit.record(u, AuditService.RECEIPT, "提交预警回执 · " + l.rule() + " " + l.letterNo(), "已提交");
        return l;
    }
}
