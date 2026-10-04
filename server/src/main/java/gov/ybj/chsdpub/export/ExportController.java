package gov.ybj.chsdpub.export;

import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Roles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
        jdbc.update("insert into export_request (watermark_no, content, purpose, validity, times, user_id) values (?,?,?,?,?,?)",
                wm, c, req.purpose(), req.validity(), req.times(), u.userId());
        audit.record(u.name(), u.org(), AuditService.EXPORT, c + " · " + req.purpose(), "待审批", wm, AuditService.currentIp());
        return Map.of("watermarkNo", wm, "content", c);
    }
}
