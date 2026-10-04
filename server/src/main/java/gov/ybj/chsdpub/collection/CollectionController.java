package gov.ybj.chsdpub.collection;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/collection")
public class CollectionController {

    private final CollectionService svc;

    public CollectionController(CollectionService svc) {
        this.svc = svc;
    }

    @GetMapping
    public CollectionService.Overview overview(@RequestParam(defaultValue = "2026-08") String period) {
        return svc.overview(period);
    }

    @GetMapping("/sources/{id}")
    public CollectionService.SourceDetail source(@PathVariable long id) {
        return svc.source(id);
    }

    @GetMapping("/lineage")
    public List<CollectionService.LineageStep> lineage(@RequestParam String indicator) {
        return svc.lineage(indicator);
    }

    @PostMapping("/sources/{id}/arrival")
    public Map<String, Object> arrival(@PathVariable long id) {
        return svc.markArrived(id);
    }

    @PostMapping("/quality-check")
    public Map<String, Object> qualityCheck(@RequestParam(defaultValue = "2026-08") String period) {
        return svc.qualityCheck(period);
    }
}
