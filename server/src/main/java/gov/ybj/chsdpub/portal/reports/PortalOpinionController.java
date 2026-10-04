package gov.ybj.chsdpub.portal.reports;

import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** B5 我的意见与机构核对：核对期逐项确认、提交意见（写入意见工单）、查看答复并评价。 */
@RestController
@RequestMapping("/api/v1/portal")
public class PortalOpinionController {

    private final PortalOpinionService svc;

    public PortalOpinionController(PortalOpinionService svc) {
        this.svc = svc;
    }

    /** 当前核对轮次：{round: null | {..., deadline, serverNow, closed, items}}。 */
    @GetMapping("/check")
    public Map<String, Object> check() {
        Map<String, Object> m = new HashMap<>();
        m.put("round", svc.round(CurrentUser.get().org()));
        return m;
    }

    public record DecideReq(String decision) {}

    @PutMapping("/check/items/{idx}")
    public PortalOpinionService.CheckRound decide(@PathVariable int idx, @RequestBody DecideReq req) {
        return svc.decide(CurrentUser.get(), idx, req == null ? null : req.decision());
    }

    @GetMapping("/opinions")
    public Map<String, Object> opinions() {
        AuthUser u = CurrentUser.get();
        return Map.of("refs", svc.refs(u.org()), "categories", PortalOpinionService.CATEGORIES, "rows", svc.mine(u.org()));
    }

    @PostMapping("/opinions")
    public PortalOpinionService.Submitted submit(@RequestBody(required = false) PortalOpinionService.SubmitReq req) {
        return svc.submit(CurrentUser.get(), req);
    }

    @PutMapping("/opinions/{no}/rating")
    public PortalOpinionService.Opinion rate(@PathVariable String no, @RequestBody(required = false) PortalOpinionService.RateReq req) {
        return svc.rate(CurrentUser.get(), no, req);
    }
}
