package gov.ybj.chsdpub.portal.reports;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** B4 报告中心：本院定向发布报告列表、A4 纸样预览（带实名水印）、签收。 */
@RestController
@RequestMapping("/api/v1/portal/reports")
public class PortalReportController {

    private final PortalReportService reports;

    public PortalReportController(PortalReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<PortalReportService.Report> list(@RequestParam(required = false) String kind) {
        return reports.list(CurrentUser.get().org(), kind);
    }

    @GetMapping("/{id}")
    public PortalReportService.ReportDetail detail(@PathVariable long id) {
        return reports.detail(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/sign")
    public PortalReportService.Report sign(@PathVariable long id) {
        return reports.sign(CurrentUser.get(), id);
    }
}
