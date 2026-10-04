package gov.ybj.chsdpub.template;

import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 图表与报告模板（A5）：图表模板库、报告区块、预置模板、生成报告草稿并进入发布工作流第 3 步。 */
@RestController
@RequestMapping("/api/v1")
public class TemplateController {

    private final JdbcTemplate jdbc;

    public TemplateController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ChartTemplate(long id, String name, String useCase, String example, String tierScope, int usedBy,
                                String version, String updated) {}

    public record Block(int id, String name, int height) {}

    public record Preset(String name, List<Integer> blockIds) {}

    @GetMapping("/chart-templates")
    public List<ChartTemplate> templates() {
        return jdbc.query("select id, name, use_case, example, tier_scope, used_by, version, updated from chart_template order by sort",
                (rs, i) -> new ChartTemplate(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getInt(6), rs.getString(7), rs.getString(8)));
    }

    @GetMapping("/report-blocks")
    public List<Block> blocks() {
        return jdbc.query("select id, name, height from report_block order by id", (rs, i) -> new Block(rs.getInt(1), rs.getString(2), rs.getInt(3)));
    }

    @GetMapping("/report-presets")
    public List<Preset> presets() {
        return jdbc.query("select name, block_ids from report_preset order by sort", (rs, i) -> new Preset(rs.getString(1), ints(rs.getArray(2))));
    }

    private static List<Integer> ints(Array a) throws java.sql.SQLException {
        return Arrays.asList((Integer[]) a.getArray());
    }

    public record DraftReq(@NotBlank(message = "请选择预置模板") String preset, @NotEmpty(message = "报告至少包含一个区块") List<Integer> blockIds,
                           String period) {}

    @PostMapping("/report-drafts")
    @Transactional
    public Map<String, Object> draft(@RequestBody @Valid DraftReq req) {
        Integer known = jdbc.queryForObject("select count(*) from report_block where id = any(?)", Integer.class,
                (Object) req.blockIds().toArray(Integer[]::new));
        if (known == null || known != req.blockIds().stream().distinct().count()) throw ApiException.validation("包含不存在的区块");
        Long id = jdbc.queryForObject("""
                insert into report_draft (preset, period, block_ids, created_by) values (?, ?, ?, ?) returning id""", Long.class,
                req.preset(), req.period() == null ? "2026-08" : req.period(), req.blockIds().toArray(Integer[]::new), CurrentUser.get().userId());
        return Map.of("id", id, "flowStep", 3, "message", "已生成「" + req.preset() + "」草稿,进入发布工作流第 3 步");
    }
}
