package gov.ybj.chsdpub.topic;

import gov.ybj.chsdpub.auth.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService svc;

    public TopicController(TopicService svc) {
        this.svc = svc;
    }

    @GetMapping("/{code}")
    public TopicService.Topic get(@PathVariable String code) {
        return svc.get(code);
    }

    @PostMapping("/{code}/sections/{idx}/approve")
    public TopicService.Section approve(@PathVariable String code, @PathVariable int idx) {
        return svc.approve(code, idx, CurrentUser.get());
    }

    @PostMapping("/{code}/sections/{idx}/regenerate")
    public TopicService.Section regenerate(@PathVariable String code, @PathVariable int idx) {
        return svc.regenerate(code, idx);
    }

    @PostMapping("/{code}/submit")
    public Map<String, Object> submit(@PathVariable String code) {
        return svc.submit(code, CurrentUser.get());
    }
}
