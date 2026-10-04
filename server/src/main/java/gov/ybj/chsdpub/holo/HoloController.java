package gov.ybj.chsdpub.holo;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** A2 医保数据公开全息图（召集人 / 行政管理组；鉴权见 SecurityConfig：/api/v1/holo/** → A2）。 */
@RestController
@RequestMapping("/api/v1/holo")
public class HoloController {

    private final HoloService svc;

    public HoloController(HoloService svc) {
        this.svc = svc;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "月") String period) {
        return svc.overview(period);
    }

    @GetMapping("/groups/{code}")
    public Map<String, Object> group(@PathVariable String code, @RequestParam(defaultValue = "月") String period) {
        return svc.group(code, period);
    }

    @GetMapping("/offsite")
    public Map<String, Object> offsite() {
        return svc.offsite(CurrentUser.get());
    }
}
