package gov.ybj.chsdpub.indicator;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 指标可视化配置（A4）：召集人 / 行政管理组（鉴权见 SecurityConfig：/api/v1/indicators/**）。 */
@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorController {

    private final IndicatorService svc;

    public IndicatorController(IndicatorService svc) {
        this.svc = svc;
    }

    @GetMapping
    public IndicatorService.Page list(@RequestParam(required = false) String grp, @RequestParam(required = false) String domain,
                                      @RequestParam(required = false) String tag, @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                                      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "13") int size) {
        return svc.list(grp, domain, tag, q, sort, dir, page, size);
    }

    @GetMapping("/{id:\\d+}")
    public IndicatorService.Card card(@PathVariable long id) {
        return svc.card(id);
    }

    @PostMapping("/{id:\\d+}/package")
    public Map<String, Object> addToPackage(@PathVariable long id) {
        return svc.addToPackage(id, CurrentUser.get());
    }

    @DeleteMapping("/{id:\\d+}/package")
    public Map<String, Object> removeFromPackage(@PathVariable long id) {
        return svc.removeFromPackage(id);
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return svc.meta();
    }

    public record ValidateReq(String formula, List<String> dims) {}

    @PostMapping("/formula/validate")
    public Map<String, Object> validate(@RequestBody ValidateReq req) {
        return svc.validate(req.formula(), req.dims());
    }

    @PostMapping("/drafts")
    public IndicatorService.Draft openDraft() {
        return svc.openDraft(CurrentUser.get());
    }

    @GetMapping("/drafts/{id}")
    public IndicatorService.Draft getDraft(@PathVariable long id) {
        return svc.getDraft(id, CurrentUser.get());
    }

    @PutMapping("/drafts/{id}")
    public IndicatorService.Draft saveDraft(@PathVariable long id, @RequestBody IndicatorService.Config config) {
        return svc.saveDraft(id, config, CurrentUser.get());
    }

    @PostMapping("/drafts/{id}/reset")
    public IndicatorService.Draft resetDraft(@PathVariable long id) {
        return svc.resetDraft(id, CurrentUser.get());
    }

    public record PreviewReq(String org) {}

    @PostMapping("/drafts/{id}/preview")
    public Map<String, Object> preview(@PathVariable long id, @RequestBody PreviewReq req) {
        return svc.preview(id, req.org(), CurrentUser.get());
    }

    @PostMapping("/drafts/{id}/submit")
    public Map<String, Object> submit(@PathVariable long id) {
        return svc.submit(id, CurrentUser.get());
    }
}
