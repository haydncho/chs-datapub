package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户与权限 (A12):页面数据取自 app_user / app_role / org / user_identity 与 {@code AccessPolicy};
 * 操作 {@code setUserEnabled}(停用/启用)、{@code requestAddUser}(新增申请,待复核)、
 * {@code reviewAddUser}(另一人复核)。操作本身由 ActionController 写入审计。
 */
@Configuration
public class UserDomain {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern LOGIN = Pattern.compile("^[a-z][a-z0-9_]{2,31}$");
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private final JdbcClient jdbc;

    public UserDomain(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ───────────────────────── 页面数据 ─────────────────────────

    private record Role(String code, String name, String dataScope) {}

    private record Row(long id, String login, String name, String roleCode, String orgId, String orgName,
                       Instant lastLogin, boolean enabled) {}

    private record Ident(long userId, String roleCode, String roleName, String orgName, String desc) {}

    @Bean
    PageOverlay a12Overlay() {
        return overlay("A12", this::fill);
    }

    void fill(ObjectNode p) {
        Instant now = Instant.now();
        Map<String, Role> roles = new LinkedHashMap<>();
        List<Role> all = jdbc.sql("select code, name, data_scope from app_role")
                .query((rs, i) -> new Role(rs.getString(1), rs.getString(2), rs.getString(3))).list();
        for (String c : UserMatrix.ROLE_ORDER) all.stream().filter(r -> r.code().equals(c)).findFirst().ifPresent(r -> roles.put(c, r));
        all.stream().filter(r -> !roles.containsKey(r.code())).forEach(r -> roles.put(r.code(), r));

        List<Row> rows = jdbc.sql("""
                select u.id, u.login, u.name, u.role_code, u.org_id, o.name, u.last_login, u.enabled
                from app_user u left join org o on o.id = u.org_id order by u.id""")
                .query((rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), rs.getBoolean(8)))
                .list();
        Map<Long, List<Ident>> idents = new LinkedHashMap<>();
        jdbc.sql("""
                select i.user_id, i.role_code, r.name, o.name, i.description
                from user_identity i join app_role r on r.code = i.role_code left join org o on o.id = i.org_id
                order by i.user_id, i.sort_order, i.id""")
                .query((rs, n) -> new Ident(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)))
                .list().forEach(x -> idents.computeIfAbsent(x.userId(), k -> new ArrayList<>()).add(x));

        // 分组与访问矩阵
        ArrayNode scopes = F.arrayNode();
        ArrayNode groups = F.arrayNode();
        for (UserMatrix.Group g : UserMatrix.GROUPS) {
            scopes.add(g.name());
            ObjectNode gn = groups.addObject().put("id", g.id()).put("name", g.name());
            ArrayNode pg = gn.putArray("pages");
            for (String c : g.pages()) pg.addObject().put("code", c).put("name", UserMatrix.PAGE_NAMES.getOrDefault(c, c));
        }

        Map<String, Integer> headcount = new LinkedHashMap<>();
        rows.forEach(r -> headcount.merge(r.roleCode(), 1, Integer::sum));

        ArrayNode roleArr = F.arrayNode();
        ArrayNode matrix = F.arrayNode();
        for (Role r : roles.values()) {
            ObjectNode rn = roleArr.addObject();
            rn.put("code", r.code()).put("name", r.name()).put("users", headcount.getOrDefault(r.code(), 0))
                    .put("side", UserMatrix.sideOf(r.code())).put("dataScope", r.dataScope())
                    .put("readOnly", cn.ybdata.core.security.AccessPolicy.isReadOnly(r.code()))
                    .put("canApprove", UserMatrix.canApprove(r.code()));
            ArrayNode pages = rn.putArray("pages");
            cn.ybdata.core.security.AccessPolicy.pagesFor(r.code()).forEach(pages::add);
            ObjectNode mr = matrix.addObject().put("role", r.code()).put("name", r.name());
            ArrayNode cells = mr.putArray("cells");
            for (UserMatrix.Cell c : UserMatrix.row(r.code())) {
                ObjectNode cn = cells.addObject().put("group", c.group()).put("level", c.level()).put("total", c.total());
                ArrayNode pp = cn.putArray("pages");
                c.pages().forEach(code -> pp.addObject().put("code", code).put("name", UserMatrix.PAGE_NAMES.getOrDefault(code, code)));
            }
        }

        // 用户
        ArrayNode users = F.arrayNode();
        int bureau = 0, org = 0, expiring = 0, disabled = 0;
        for (Row u : rows) {
            Role role = roles.get(u.roleCode());
            String status = UserMatrix.statusOf(u.enabled(), u.lastLogin(), now);
            String side = UserMatrix.sideOf(u.roleCode());
            if ("bureau".equals(side)) bureau++; else org++;
            if ("expiring".equals(status)) expiring++;
            if ("off".equals(status)) disabled++;
            List<Ident> mine = idents.getOrDefault(u.id(), List.of());
            ObjectNode un = users.addObject();
            un.put("login", u.login()).put("name", u.name()).put("role", role == null ? u.roleCode() : role.name())
                    .put("roleCode", u.roleCode()).put("org", u.orgName() == null ? "—" : u.orgName())
                    .put("scope", role == null ? "" : role.dataScope()).put("side", side)
                    .put("lastLogin", UserMatrix.lastLoginText(u.lastLogin(), now, ZONE)).put("status", status)
                    .put("enabled", u.enabled());
            if (u.lastLogin() != null) un.put("lastLoginAt", u.lastLogin().toString());
            mine.stream().filter(x -> x.roleCode().equals(u.roleCode()) && x.desc() != null && x.desc().contains("承办"))
                    .findFirst().ifPresent(x -> un.put("title", "承办人"));
            Set<String> sides = new LinkedHashSet<>(List.of(side));
            ArrayNode held = un.putArray("identities");
            for (Ident x : mine) {
                sides.add(UserMatrix.sideOf(x.roleCode()));
                held.addObject().put("roleCode", x.roleCode()).put("role", x.roleName())
                        .put("org", x.orgName() == null ? "—" : x.orgName()).put("side", UserMatrix.sideOf(x.roleCode()));
            }
            ArrayNode sn = un.putArray("sides");
            sides.forEach(sn::add);
        }
        int total = rows.size();

        // 待复核的新增申请
        record Req(String login, String name, String roleCode, String orgName, String by, Instant at) {}
        List<Req> reqs = jdbc.sql("""
                select q.login, q.name, q.role_code, o.name, q.requested_by, q.requested_at
                from user_request q left join org o on o.id = q.org_id where q.status = 'pending' order by q.id""")
                .query((rs, i) -> new Req(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant())).list();
        for (Req q : reqs) {
            Role role = roles.get(q.roleCode());
            String side = UserMatrix.sideOf(q.roleCode());
            ObjectNode un = users.addObject();
            un.put("login", q.login()).put("name", q.name()).put("role", role == null ? q.roleCode() : role.name())
                    .put("roleCode", q.roleCode()).put("org", q.orgName() == null ? "—" : q.orgName())
                    .put("scope", role == null ? "" : role.dataScope()).put("side", side).put("lastLogin", "—")
                    .put("status", "pending").put("enabled", false).put("requestedBy", q.by())
                    .put("requestedAt", UserMatrix.lastLoginText(q.at(), now, ZONE));
            un.putArray("identities");
            un.putArray("sides").add(side);
        }

        ObjectNode summary = F.objectNode();
        summary.put("total", total).put("bureau", bureau).put("org", org).put("expiring", expiring)
                .put("disabled", disabled).put("pending", reqs.size());
        p.set("scopes", scopes);
        p.set("groups", groups);
        p.set("roles", roleArr);
        p.set("matrix", matrix);
        p.set("users", users);
        p.set("summary", summary);
        p.put("userSummary", "共 " + total + " 人 · 医保局端 " + bureau + " · 机构端 " + org
                + " · 超过 " + UserMatrix.INACTIVE_DAYS + " 天未登录 " + expiring + " 人即将停用"
                + (reqs.isEmpty() ? "" : " · 另有待复核申请 " + reqs.size() + " 项(列表与分端计数含申请)"));
    }

    // ───────────────────────── 操作 ─────────────────────────

    /** 当前请求的身份(由 AuthFilter 解析;没有身份的请求到不了这里)。 */
    private static Actor currentActor() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest req = a.getRequest();
            return CurrentActor.get(req);
        }
        return null;
    }

    static void require(Actor who, String action) {
        if (who == null) throw new AccessDeniedException("请先登录");
        if (!UserMatrix.mayAct(who.role(), action)) {
            throw new AccessDeniedException("当前身份无权执行 A12/" + action + (
                    "reviewAddUser".equals(action) ? "(仅召集人、行政管理组可复核)" : "(仅召集人可操作)"));
        }
    }

    @Bean
    ActionHandler a12SetUserEnabled() {
        return handler("A12", "setUserEnabled", (actor, p) -> {
            Actor who = currentActor();
            require(who, "setUserEnabled");
            String login = Json.text(p, "login");
            JsonNode en = p.get("enabled");
            if (en == null || !en.isBoolean()) throw new IllegalArgumentException("missing field: enabled");
            boolean enabled = en.asBoolean();
            if (!enabled) {
                if (who != null && login.equals(who.login())) throw new IllegalArgumentException("不能停用自己的账号");
                String role = jdbc.sql("select role_code from app_user where login = :l").param("l", login)
                        .query(String.class).optional().orElse(null);
                if ("convener".equals(role)) {
                    int others = jdbc.sql("select count(*) from app_user where role_code = 'convener' and enabled and login <> :l")
                            .param("l", login).query(Integer.class).single();
                    if (others == 0) throw new IllegalArgumentException("不能停用最后一名召集人");
                }
            }
            int n = jdbc.sql("update app_user set enabled = :e where login = :l").param("e", enabled).param("l", login).update();
            if (n == 0) throw new IllegalArgumentException("未知用户: " + login);
            if (!enabled) { // 停用后立即作废其已签发的会话
                jdbc.sql("""
                        update auth_session set revoked_at = now()
                        where revoked_at is null and user_id = (select id from app_user where login = :l)""")
                        .param("l", login).update();
            }
            return Map.of("login", login, "enabled", enabled);
        });
    }

    @Bean
    ActionHandler a12RequestAddUser() {
        return handler("A12", "requestAddUser", (actor, p) -> {
            require(currentActor(), "requestAddUser");
            String name = Json.text(p, "name").trim();
            String login = Json.text(p, "login").trim();
            String role = Json.text(p, "role").trim();
            if (name.isEmpty() || name.length() > 32) throw new IllegalArgumentException("姓名为 1–32 个字符");
            if (!LOGIN.matcher(login).matches()) throw new IllegalArgumentException("登录名须为 3–32 位小写字母、数字或下划线,以字母开头");
            String roleCode = jdbc.sql("select code from app_role where code = :r or name = :r").param("r", role)
                    .query(String.class).optional().orElseThrow(() -> new IllegalArgumentException("未知角色: " + role));
            String orgIn = Json.textOr(p, "org", "").trim();
            String orgId = orgIn.isEmpty() ? defaultOrg(roleCode)
                    : jdbc.sql("select id from org where id = :o or name = :o").param("o", orgIn)
                            .query(String.class).optional().orElseThrow(() -> new IllegalArgumentException("未知机构: " + orgIn));
            String level = jdbc.sql("select level from org where id = :o").param("o", orgId).query(String.class).optional().orElse("");
            if (!UserMatrix.orgFits(roleCode, level)) {
                throw new IllegalArgumentException("所属机构与角色不匹配:" + UserMatrix.orgRule(roleCode));
            }
            if (jdbc.sql("select count(*) from app_user where login = :l").param("l", login).query(Integer.class).single() > 0) {
                throw new IllegalArgumentException("登录名已存在: " + login);
            }
            if (jdbc.sql("select count(*) from user_request where login = :l and status = 'pending'").param("l", login)
                    .query(Integer.class).single() > 0) {
                throw new IllegalArgumentException("该登录名已有待复核的申请: " + login);
            }
            jdbc.sql("insert into user_request (login, name, role_code, org_id, requested_by) values (:l, :n, :r, :o, :by)")
                    .param("l", login).param("n", name).param("r", roleCode).param("o", orgId).param("by", actor).update();
            return Map.of("login", login, "status", "pending");
        });
    }

    @Bean
    ActionHandler a12ReviewAddUser() {
        return handler("A12", "reviewAddUser", (actor, p) -> {
            require(currentActor(), "reviewAddUser");
            String login = Json.text(p, "login");
            JsonNode ap = p.get("approve");
            if (ap == null || !ap.isBoolean()) throw new IllegalArgumentException("missing field: approve");
            record Req(long id, String name, String roleCode, String orgId, String by) {}
            Req q = jdbc.sql("select id, name, role_code, org_id, requested_by from user_request where login = :l and status = 'pending'")
                    .param("l", login).query((rs, i) -> new Req(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5))).optional()
                    .orElseThrow(() -> new IllegalArgumentException("没有待复核的申请: " + login));
            if (q.by().equals(actor)) throw new AccessDeniedException("须由另一人复核,不能复核自己提交的申请");
            boolean approve = ap.asBoolean();
            jdbc.sql("update user_request set status = :s, reviewed_by = :by, reviewed_at = now() where id = :id")
                    .param("s", approve ? "approved" : "rejected").param("by", actor).param("id", q.id()).update();
            if (approve) createUser(login, q.name(), q.roleCode(), q.orgId());
            return Map.of("login", login, "status", approve ? "approved" : "rejected");
        });
    }

    private static String defaultOrg(String roleCode) {
        return switch (roleCode) {
            case "observer" -> "PUB";
            case "hospital" -> "H001";
            case "county" -> throw new IllegalArgumentException("县区医保部门须填写所属机构");
            default -> "YBJ";
        };
    }

    /** 申请通过:建账号并授予该角色的默认身份(登录落地页取自角色的首个可访问页面)。 */
    private void createUser(String login, String name, String roleCode, String orgId) {
        long uid = jdbc.sql("insert into app_user (login, name, role_code, org_id) values (:l, :n, :r, :o) returning id")
                .param("l", login).param("n", name).param("r", roleCode).param("o", orgId).query(Long.class).single();
        String roleName = jdbc.sql("select name from app_role where code = :r").param("r", roleCode).query(String.class).single();
        String orgName = jdbc.sql("select name from org where id = :o").param("o", orgId).query(String.class).optional().orElse("—");
        String side = UserMatrix.sideOf(roleCode);
        String target = cn.ybdata.core.security.AccessPolicy.pagesFor(roleCode).stream().filter(c -> !"A1".equals(c))
                .findFirst().orElse("A1");
        boolean pub = "observer".equals(roleCode);
        jdbc.sql("""
                insert into user_identity (user_id, role_code, org_id, label, description, zone, tone, initial, target, scope, sort_order)
                values (:u, :r, :o, :label, :desc, :zone, :tone, :ini, :target, :scope, 0)""")
                .param("u", uid).param("r", roleCode).param("o", orgId)
                .param("label", orgName + " · " + roleName).param("desc", roleName + " · 新增账号")
                .param("zone", pub ? "公开层" : "bureau".equals(side) ? "分析监测区" : "发布区")
                .param("tone", "bureau".equals(side) ? "brand" : "ok").param("ini", roleName.substring(0, 1))
                .param("target", target).param("scope", orgName).update();
    }
}
