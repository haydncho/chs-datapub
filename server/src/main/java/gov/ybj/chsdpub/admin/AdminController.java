package gov.ybj.chsdpub.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 用户权限管理（A12）：组织树（医共体为虚拟组织）、角色权限矩阵与五维配置、账号生命周期。
 * 三员分立：安全管理员可管理授权，不能查看业务数据；授权类操作须第二名管理员复核后生效。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    public static final List<String> COLUMNS = List.of("统筹区汇总", "机构级数据", "他院数据", "病例明细", "导出", "配置与审批");

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final AuditService audit;

    public AdminController(JdbcTemplate jdbc, ObjectMapper om, AuditService audit) {
        this.jdbc = jdbc;
        this.om = om;
        this.audit = audit;
    }

    public record Org(long id, String name, int level, boolean virtual) {}

    public record Dim(String key, String value) {}

    public record Role(int id, String name, List<String> matrix, List<Dim> dims) {}

    public record Account(long id, String name, String org, String role, String stage, String tone, String note) {}

    @GetMapping("/orgs")
    public List<Org> orgs() {
        return jdbc.query("select id, name, level, is_virtual from org_unit order by sort",
                (rs, i) -> new Org(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4)));
    }

    @GetMapping("/roles")
    public Map<String, Object> roles() {
        List<Role> roles = jdbc.query("select id, name, matrix, dims::text from perm_role order by id", (rs, i) -> {
            List<List<String>> d;
            try {
                d = om.readValue(rs.getString(4), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return new Role(rs.getInt(1), rs.getString(2), Arrays.asList((String[]) rs.getArray(3).getArray()),
                    d.stream().map(x -> new Dim(x.get(0), x.get(1))).toList());
        });
        return Map.of("columns", COLUMNS, "roles", roles);
    }

    @GetMapping("/accounts")
    public List<Account> accounts() {
        return jdbc.query("select id, name, org, role, stage, tone, note from account_lifecycle order by sort",
                (rs, i) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7)));
    }

    public record OperationReq(@NotBlank(message = "缺少操作对象") String target, @NotBlank(message = "缺少操作类型") String action) {}

    @PostMapping("/operations")
    @Transactional
    public Map<String, Object> operate(@RequestBody @Valid OperationReq req) {
        var u = CurrentUser.get();
        jdbc.update("insert into admin_operation (target, action, requested_by) values (?, ?, ?)", req.target(), req.action(), u.userId());
        audit.record(u, AuditService.GRANT, req.action() + " · " + req.target(), "待复核");
        return Map.of("message", "已发起操作,需第二名管理员复核");
    }
}
