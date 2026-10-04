package gov.ybj.chsdpub.opinion;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 意见与申诉管理（A10）：承办人在时限内答复；核对期异议须在发布前答复，影响本期发布包定稿。 */
@RestController
@RequestMapping("/api/v1/opinions")
public class OpinionController {

    public static final String CHECK = "核对期异议";
    private static final List<String> TABS = List.of("全部", CHECK, "待答复", "已答复");

    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public OpinionController(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(props.zone());
    }

    public record Ticket(String no, String org, String ref, String category, String owner, String dueLabel, String dueTone,
                         String status, String content, String reply, Integer rating, boolean typical) {}

    private List<Ticket> all() {
        LocalDate today = LocalDate.now(zone);
        return jdbc.query("""
                select no, org, ref, category, owner, due_date, status, content, reply, rating, typical
                from opinion_ticket order by created_at desc, no desc""", (rs, i) -> {
            String st = rs.getString(7);
            LocalDate due = rs.getObject(6, LocalDate.class);
            String label = "—", tone = "muted";
            if (!"DONE".equals(st) && due != null) {
                long d = ChronoUnit.DAYS.between(today, due);
                label = d < 0 ? "超期 " + (-d) + " 天" : d == 0 ? "今日到期" : "剩 " + d + " 天";
                tone = d < 0 ? "danger" : d <= 1 ? "warning" : "muted";
            }
            return new Ticket(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), label, tone, st,
                    rs.getString(8), rs.getString(9), (Integer) rs.getObject(10), rs.getBoolean(11));
        });
    }

    private static boolean match(String tab, Ticket t) {
        return switch (tab) {
            case CHECK -> CHECK.equals(t.category());
            case "待答复" -> !"DONE".equals(t.status());
            case "已答复" -> "DONE".equals(t.status());
            default -> true;
        };
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "全部") String tab) {
        List<Ticket> all = all();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String t : TABS) counts.put(t, all.stream().filter(x -> match(t, x)).count());
        return Map.of("counts", counts, "rows", all.stream().filter(x -> match(tab, x)).toList());
    }

    private Ticket one(String no) {
        return all().stream().filter(t -> t.no().equals(no)).findFirst().orElseThrow(() -> ApiException.notFound("工单不存在"));
    }

    public record ReplyReq(String text) {}

    @PostMapping("/{no}/reply")
    @Transactional
    public Ticket reply(@PathVariable String no, @RequestBody ReplyReq req) {
        Ticket t = one(no);
        if ("DONE".equals(t.status())) throw ApiException.conflict("该工单已答复");
        if (req == null || Texts.blank(req.text())) throw ApiException.validation("请填写答复意见");
        jdbc.update("update opinion_ticket set status = 'DONE', reply = ?, replied_at = now() where no = ?", req.text().trim(), no);
        return one(no);
    }

    public record TransferReq(String to) {}

    @PostMapping("/{no}/transfer")
    @Transactional
    public Ticket transfer(@PathVariable String no, @RequestBody(required = false) TransferReq req) {
        Ticket t = one(no);
        if ("DONE".equals(t.status())) throw ApiException.conflict("已答复的工单不能转办");
        String to = req == null || Texts.blank(req.to()) ? "基金监管科 · 刘伟" : req.to().trim();
        jdbc.update("update opinion_ticket set owner = ?, status = 'DOING' where no = ?", Texts.truncate(to, 32), no);
        return one(no);
    }

    public record TypicalReq(boolean typical) {}

    @PutMapping("/{no}/typical")
    @Transactional
    public Ticket typical(@PathVariable String no, @RequestBody TypicalReq req) {
        one(no);
        jdbc.update("update opinion_ticket set typical = ? where no = ?", req.typical(), no);
        return one(no);
    }
}
