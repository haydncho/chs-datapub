package gov.ybj.chsdpub.recommend;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 智能推荐中心（A6）：召集人 / 行政管理组（鉴权见 SecurityConfig：/api/v1/recommend/**）。 */
@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    private final RecommendService svc;

    public RecommendController(RecommendService svc) {
        this.svc = svc;
    }

    @GetMapping("/topics")
    public Map<String, Object> topics() {
        return svc.topics();
    }

    public record DecisionReq(String action) {}

    @PostMapping("/topics/{code}/decision")
    public Map<String, Object> decide(@PathVariable String code, @RequestBody DecisionReq req) {
        return svc.decide(code, req.action(), CurrentUser.get());
    }

    @PostMapping("/topics/{code}/undo")
    public Map<String, Object> undo(@PathVariable String code) {
        return svc.undo(code);
    }

    public record ModifyReq(List<String> reasons, String note) {}

    @PutMapping("/topics/{code}")
    public Map<String, Object> modify(@PathVariable String code, @RequestBody ModifyReq req) {
        return svc.modify(code, req.reasons(), req.note());
    }

    @GetMapping("/attribution")
    public Map<String, Object> attribution() {
        return svc.attribution();
    }

    @GetMapping("/benchmark")
    public Map<String, Object> benchmark(@RequestParam(defaultValue = "75") int threshold) {
        return svc.benchmark(threshold);
    }

    @GetMapping("/anomalies")
    public List<RecommendService.Anomaly> anomalies() {
        return svc.anomalies();
    }

    @PostMapping("/anomalies/{id}/letter")
    public Map<String, Object> letter(@PathVariable long id) {
        return svc.letter(id, CurrentUser.get());
    }

    @GetMapping("/presentations")
    public List<RecommendService.Presentation> presentations() {
        return svc.presentations();
    }

    @GetMapping("/methods")
    public List<RecommendService.MethodCard> methods() {
        return svc.methods();
    }
}
