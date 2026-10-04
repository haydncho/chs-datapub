package gov.ybj.chsdpub.flow;

import gov.ybj.chsdpub.common.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程设计器（A9）：发布流程模板带版本号；「召集人审批」为必经节点，不可删除、不可跳过，驳回固定退回「分析成稿」。
 * 保存为新版本后须召集人确认才生效，进行中的流程沿用旧版本。
 */
@RestController
@RequestMapping("/api/v1/flow-templates")
public class FlowController {

    private static final Set<String> MODES = Set.of("单人", "会签", "或签");

    private final JdbcTemplate jdbc;

    public FlowController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TemplateRow(long id, String kind, String version, String pendingVersion, String updatedBy, LocalDate updatedOn) {}

    public record Node(int idx, String name, String handler, String mode, int days, String escalateTo, boolean gate) {}

    public record Template(TemplateRow template, List<Node> nodes) {}

    @GetMapping
    public List<TemplateRow> list() {
        return jdbc.query("select id, kind, version, pending_version, updated_by, updated_on from flow_template order by sort",
                (rs, i) -> new TemplateRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getObject(6, LocalDate.class)));
    }

    private TemplateRow row(long id) {
        return list().stream().filter(t -> t.id() == id).findFirst().orElseThrow(() -> ApiException.notFound("流程模板不存在"));
    }

    private List<Node> nodes(long id) {
        return jdbc.query("select idx, name, handler, mode, days, escalate_to, gate from flow_node where template_id = ? order by idx",
                (rs, i) -> new Node(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6),
                        rs.getBoolean(7)), id);
    }

    private Node node(long id, int idx) {
        return nodes(id).stream().filter(n -> n.idx() == idx).findFirst().orElseThrow(() -> ApiException.notFound("节点不存在"));
    }

    @GetMapping("/{id}")
    public Template get(@PathVariable long id) {
        return new Template(row(id), nodes(id));
    }

    public record NodeReq(String mode, @Min(value = 1, message = "时限至少 1 个工作日") @Max(value = 30, message = "时限不超过 30 个工作日") Integer days) {}

    @PutMapping("/{id}/nodes/{idx}")
    @Transactional
    public Node update(@PathVariable long id, @PathVariable int idx, @RequestBody @jakarta.validation.Valid NodeReq req) {
        Node n = node(id, idx);
        if (req.mode() != null) {
            if ("—".equals(n.mode())) throw ApiException.conflict("机构处理节点不设处理方式");
            if (!MODES.contains(req.mode())) throw ApiException.validation("处理方式须为 单人 / 会签 / 或签");
            if (n.gate() && !"单人".equals(req.mode())) throw ApiException.conflict("召集人审批为单人审批,不可改为会签或或签");
        }
        jdbc.update("update flow_node set mode = coalesce(?, mode), days = coalesce(?, days) where template_id = ? and idx = ?",
                req.mode(), req.days(), id, idx);
        return node(id, idx);
    }

    @DeleteMapping("/{id}/nodes/{idx}")
    @Transactional
    public Map<String, Object> delete(@PathVariable long id, @PathVariable int idx) {
        Node n = node(id, idx);
        if (n.gate()) throw ApiException.conflict("必经节点不可删除");
        jdbc.update("delete from flow_node where template_id = ? and idx = ?", id, idx);
        // 重新编号（两步避免主键冲突）
        jdbc.update("update flow_node set idx = -idx where template_id = ? and idx > ?", id, idx);
        jdbc.update("update flow_node set idx = -idx - 1 where template_id = ? and idx < 0", id);
        return Map.of("message", "已删除节点「" + n.name() + "」,保存为新版本后生效");
    }

    @PostMapping("/{id}/versions")
    @Transactional
    public Map<String, Object> saveVersion(@PathVariable long id) {
        TemplateRow t = row(id);
        String base = t.pendingVersion() != null ? t.pendingVersion() : t.version();
        String[] p = base.substring(1).split("\\.");
        String next = "v" + p[0] + "." + (Integer.parseInt(p[1]) + 1);
        jdbc.update("update flow_template set pending_version = ? where id = ?", next, id);
        return Map.of("version", next, "message", "已保存为「" + t.kind() + " " + next + "」草稿,发布需召集人确认;进行中的流程沿用旧版本");
    }
}
