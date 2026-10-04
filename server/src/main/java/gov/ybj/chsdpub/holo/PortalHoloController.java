package gov.ybj.chsdpub.holo;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** B1 本院全息图（机构门户；/api/v1/portal/** 仅机构身份）。机构取自会话身份，接口不接受机构参数。 */
@RestController
@RequestMapping("/api/v1/portal/holo")
public class PortalHoloController {

    private final HoloService svc;

    public PortalHoloController(HoloService svc) {
        this.svc = svc;
    }

    @GetMapping
    public Map<String, Object> hospital() {
        return svc.hospital(CurrentUser.get());
    }
}
