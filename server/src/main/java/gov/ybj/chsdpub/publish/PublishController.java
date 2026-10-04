package gov.ybj.chsdpub.publish;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 发布工作流（A8）接口：/api/v1/publish/**（召集人、行政管理组可进入；批准 / 驳回只限召集人，服务端校验）。
 */
@RestController
@RequestMapping("/api/v1/publish")
public class PublishController {

    private final PublishService flows;
    private final TierApprovalService tiers;

    public PublishController(PublishService flows, TierApprovalService tiers) {
        this.flows = flows;
        this.tiers = tiers;
    }

    @GetMapping("/todos")
    public List<PublishService.TodoGroup> todos() {
        return flows.todos();
    }

    @GetMapping("/flows/{id}")
    public PublishService.FlowDetail flow(@PathVariable long id) {
        return flows.detail(id);
    }

    @PutMapping("/flows/{id}/scope")
    public Map<String, Object> scope(@PathVariable long id, @RequestBody PublishService.Scope scope) {
        return flows.updateScope(id, scope);
    }

    @PostMapping("/flows/{id}/submit")
    public Map<String, Object> submit(@PathVariable long id) {
        return flows.submit(id);
    }

    @PostMapping("/flows/{id}/approve")
    public Map<String, Object> approve(@PathVariable long id, @RequestBody(required = false) PublishService.DecisionReq req) {
        return flows.approve(id, req);
    }

    @PostMapping("/flows/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id, @RequestBody(required = false) PublishService.DecisionReq req) {
        return flows.reject(id, req);
    }

    @PostMapping("/flows/{id}/corrections")
    public Map<String, Object> correct(@PathVariable long id, @RequestBody(required = false) PublishService.CorrectionReq req) {
        return flows.correct(id, req);
    }

    @GetMapping("/audience-versions")
    public List<PublishService.AudienceVersion> audienceVersions() {
        return flows.audienceVersions();
    }

    @GetMapping("/tier-requests/{id}")
    public TierApprovalService.TierRequest tierRequest(@PathVariable long id) {
        return tiers.get(id);
    }

    @PostMapping("/tier-requests/{id}/approve")
    public Map<String, Object> approveTier(@PathVariable long id, @RequestBody(required = false) PublishService.DecisionReq req) {
        return tiers.approve(id, req);
    }

    @PostMapping("/tier-requests/{id}/reject")
    public Map<String, Object> rejectTier(@PathVariable long id, @RequestBody(required = false) PublishService.DecisionReq req) {
        return tiers.reject(id, req);
    }
}
