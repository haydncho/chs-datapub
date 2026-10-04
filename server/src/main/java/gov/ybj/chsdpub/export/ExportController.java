package gov.ybj.chsdpub.export;

import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Roles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 导出审批：用途、有效期、下载次数；导出文件嵌入实名水印与编号，审批通过后仅在专网内下载。
 * 全站不提供公网分享、复制链接、二维码。
 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    /** 可导出范围（页面）→ 导出内容（只读）。 */
    private static final Map<String, String> CONTENT = Map.of(
            "A2", "全息图当前视图(2026年8月 · 病组级 · 聚合数据)",
            "A7", "BR25 专题聚合结果(不含病例级数据)",
            "A14", "审计日志筛选结果",
            "B1", "本院全息图(2026年8月 · 本院数据)",
            "B2", "本院病组下钻(本院数据)",
            "C1", "甲县县区视图(本县区机构 + 医共体指标)",
            "C2", "各统筹区发布与监测汇总表");
    private static final List<String> PURPOSES = List.of("内部会议材料", "工作组专题分析", "上报省医保局");
    private static final List<String> VALIDITY = List.of("24小时", "3天", "7天");
    private static final List<String> TIMES = List.of("1次", "3次");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ExportController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/options")
    public Map<String, Object> options(@RequestParam String scope) {
        return Map.of("content", content(scope, CurrentUser.get()), "purposes", PURPOSES, "validity", VALIDITY, "times", TIMES);
    }

    private static String content(String scope, AuthUser u) {
        String c = CONTENT.get(scope);
        if (c == null) throw ApiException.validation("该页面不提供导出");
        if (!Roles.PAGES.get(scope).contains(u.role()) || Roles.EXPERT.equals(u.role())) throw ApiException.forbidden("当前身份无权导出该内容");
        return c;
    }

    public record ExportReq(String scope, String purpose, String validity, String times) {}

    @PostMapping
    @Transactional
    public Map<String, Object> request(@RequestBody ExportReq req) {
        AuthUser u = CurrentUser.get();
        String c = content(req.scope(), u);
        if (!PURPOSES.contains(req.purpose())) throw ApiException.validation("请选择用途");
        if (!VALIDITY.contains(req.validity())) throw ApiException.validation("请选择有效期");
        if (!TIMES.contains(req.times())) throw ApiException.validation("请选择下载次数");
        String wm = audit.nextWatermark();
        jdbc.update("insert into export_request (watermark_no, content, purpose, validity, times, user_id, requester_role, requester_org) values (?,?,?,?,?,?,?,?)",
                wm, c, req.purpose(), req.validity(), req.times(), u.userId(), u.role(), u.org());
        audit.record(u.name(), u.org(), AuditService.EXPORT, c + " · " + req.purpose(), "待审批", wm, AuditService.currentIp());
        return Map.of("watermarkNo", wm, "content", c);
    }

    // ------------------------------------------------------------ 审批、列表、下载

    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String SELECT = """
            select e.id, e.watermark_no, e.content, e.purpose, e.validity, e.times, e.status, e.created_at, e.decided_by,
                   e.opinion, e.expires_at, e.remaining, u.name, e.requester_org
            from export_request e join app_user u on u.id = e.user_id """;

    public record Item(long id, String watermarkNo, String content, String purpose, String validity, String times, String status,
                       String createdAt, String decidedBy, String opinion, String expiresAt, int remaining, String applicant, String org,
                       boolean downloadable) {}

    private Item item(java.sql.ResultSet rs) throws java.sql.SQLException {
        ZoneId z = ZoneId.systemDefault();
        String st = rs.getString(7);
        java.sql.Timestamp exp = rs.getTimestamp(11);
        int rem = rs.getInt(12);
        if ("已批准".equals(st)) {
            if (exp != null && exp.toInstant().isBefore(java.time.Instant.now())) st = "已失效";
            else if (rem <= 0) st = "已用尽";
        }
        return new Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), st,
                rs.getTimestamp(8).toInstant().atZone(z).format(MDHM), rs.getString(9), rs.getString(10),
                exp == null ? null : exp.toInstant().atZone(z).format(MDHM), rem, rs.getString(13), rs.getString(14), "已批准".equals(st));
    }

    /** 谁审批谁的申请:召集人审批他人的,召集人自己的由行政管理组审批(申请人不能批自己的)。 */
    private static boolean canDecide(AuthUser u, String requesterRole) {
        if (Roles.CONVENER.equals(requesterRole)) return Roles.ADMIN_GROUP.equals(u.role());
        return Roles.CONVENER.equals(u.role());
    }

    @GetMapping("/mine")
    public List<Item> mine() {
        AuthUser u = CurrentUser.get();
        return jdbc.query(SELECT + " where e.user_id = ? order by e.created_at desc", (rs, i) -> item(rs), u.userId());
    }

    @GetMapping("/pending")
    public List<Item> pending() {
        AuthUser u = CurrentUser.get();
        if (!Roles.CONVENER.equals(u.role()) && !Roles.ADMIN_GROUP.equals(u.role())) return List.of();
        String cond = Roles.CONVENER.equals(u.role()) ? "e.requester_role <> 'CONVENER'" : "e.requester_role = 'CONVENER'";
        return jdbc.query(SELECT + " where e.status = '待审批' and " + cond + " order by e.created_at", (rs, i) -> item(rs));
    }

    public record DecisionReq(String opinion) {}

    private Item decide(long id, boolean approve, DecisionReq req) {
        AuthUser u = CurrentUser.get();
        List<List<String>> row = jdbc.query("select status, requester_role, validity, times, watermark_no, content from export_request where id = ?",
                (rs, i) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), id);
        if (row.isEmpty()) throw ApiException.notFound("导出申请不存在");
        List<String> r = row.get(0);
        if (!canDecide(u, r.get(1))) throw ApiException.forbidden("当前身份无权审批该导出申请");
        if (!"待审批".equals(r.get(0))) throw ApiException.conflict("该申请已处理");
        String opinion = req == null || req.opinion() == null ? "" : req.opinion().trim();
        if (!approve && opinion.isEmpty()) throw ApiException.validation("驳回须填写意见");
        if (approve) {
            Duration d = switch (r.get(2)) { case "24小时" -> Duration.ofHours(24); case "3天" -> Duration.ofDays(3); default -> Duration.ofDays(7); };
            int times = Integer.parseInt(r.get(3).replace("次", ""));
            jdbc.update("update export_request set status = '已批准', decided_by = ?, decided_at = now(), opinion = ?, expires_at = ?, remaining = ? where id = ?",
                    u.name(), opinion, java.sql.Timestamp.from(OffsetDateTime.now().plus(d).toInstant()), times, id);
        } else {
            jdbc.update("update export_request set status = '已驳回', decided_by = ?, decided_at = now(), opinion = ? where id = ?", u.name(), opinion, id);
        }
        audit.record(u.name(), u.org(), AuditService.APPROVAL, "导出审批 · " + r.get(5), approve ? "已批准" : "已驳回", r.get(4), AuditService.currentIp());
        return jdbc.query(SELECT + " where e.id = ?", (rs, i) -> item(rs), id).get(0);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public Item approve(@PathVariable long id, @RequestBody(required = false) DecisionReq req) {
        return decide(id, true, req);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public Item reject(@PathVariable long id, @RequestBody(required = false) DecisionReq req) {
        return decide(id, false, req);
    }

    /** 带实名水印的下载:仅申请人本人、审批通过、未过期、有剩余次数;每次下载扣减并写审计。 */
    @GetMapping("/{id}/download")
    @Transactional
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        AuthUser u = CurrentUser.get();
        List<Item> rows = jdbc.query(SELECT + " where e.id = ? and e.user_id = ?", (rs, i) -> item(rs), id, u.userId());
        if (rows.isEmpty()) throw ApiException.notFound("导出申请不存在");
        Item it = rows.get(0);
        if (!it.downloadable()) throw ApiException.conflict("该导出当前不可下载(状态:" + it.status() + ")");
        if (jdbc.update("update export_request set remaining = remaining - 1 where id = ? and remaining > 0", id) == 0)
            throw ApiException.conflict("下载次数已用尽");
        String now = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String body = "\uFEFF水印编号," + it.watermarkNo() + "\n导出人," + u.name() + "\n机构," + u.org() + "\n下载时间," + now
                + "\n导出内容," + it.content() + "\n用途," + it.purpose()
                + "\n说明,本文件嵌入实名水印,仅限专网内使用;每次下载均留痕,可凭水印编号溯源到人\n";
        audit.record(u.name(), u.org(), AuditService.EXPORT, "下载 · " + it.content(), "成功", it.watermarkNo(), AuditService.currentIp());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        h.setContentDisposition(ContentDisposition.attachment().filename(it.watermarkNo() + ".csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(h).body(body.getBytes(StandardCharsets.UTF_8));
    }
}
