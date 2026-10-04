package gov.ybj.chsdpub.portal.analysis;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机构门户分析接口（仅机构身份，见 SecurityConfig 的 /api/v1/portal/**）。机构 = 当前会话身份的 org，只返回本院数据。
 */
@RestController
@RequestMapping("/api/v1/portal")
public class PortalAnalysisController {

    private final PortalAnalysisService svc;

    public PortalAnalysisController(PortalAnalysisService svc) {
        this.svc = svc;
    }

    /** B2：本院重点病组（芯片）、小样本病组（已并入其他）、相关专题入口。 */
    @GetMapping("/groups")
    public PortalAnalysisService.GroupIndex groups() {
        return svc.groups(CurrentUser.get().org());
    }

    /** B2：病组下钻（KPI、费用结构、关键行为发生率、标杆差距）；小样本病组 404。 */
    @GetMapping("/groups/{code}")
    public PortalAnalysisService.GroupDetail group(@PathVariable String code) {
        return svc.group(CurrentUser.get().org(), code);
    }

    /** B3：指标列表与当前对标档位（读第二批 benchmark_tier）。 */
    @GetMapping("/benchmark")
    public PortalAnalysisService.BenchIndex benchmarks() {
        return svc.benchmarks(CurrentUser.get().org());
    }

    /** B3：按档位裁剪的对标结果（匿名分位 / 匿名编号 / 具名PK与排行）。 */
    @GetMapping("/benchmark/{indicator}")
    public PortalAnalysisService.BenchDetail benchmark(@PathVariable String indicator) {
        return svc.benchmark(CurrentUser.get().org(), indicator);
    }

    /** B7：区域外数据（省平台回流汇总；无就医地机构明细）。 */
    @GetMapping("/offsite")
    public PortalAnalysisService.Offsite offsite() {
        return svc.offsite(CurrentUser.get().org());
    }
}
