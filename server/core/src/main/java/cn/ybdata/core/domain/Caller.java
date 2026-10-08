package cn.ybdata.core.domain;

import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who performs a domain action, with the organisation that action belongs to.
 *
 * <p>For a logged-in identity (session or a known {@code X-YB-User}) this is the active identity
 * (role + org) — never a client-supplied organisation. For the legacy dev fallback (no credentials,
 * {@code yb.auth.dev-header=true}) the per-page demo user is looked up, so 李敏 on the B pages
 * still acts for 示例市第一人民医院.
 */
record Caller(String name, String role, String orgId, String orgName) {

    /** a 定点医药机构 identity acting for its own institution */
    boolean hospital() {
        return "hospital".equals(role) && orgId != null && orgId.startsWith("H");
    }

    boolean county() {
        return "county".equals(role);
    }

    static Actor currentActor() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s) {
            return CurrentActor.get(s.getRequest());
        }
        return null;
    }

    /** @param actorName the audit actor ActionController resolved (session name or per-page demo identity) */
    static Caller resolve(JdbcClient jdbc, String actorName) {
        Actor a = currentActor();
        if (a != null && a.enforced()) return new Caller(a.name(), a.role(), a.orgId(), a.orgName());
        String n = a != null && a.name() != null ? a.name() : actorName;
        if (n == null || n.isBlank()) return new Caller(actorName, null, null, null);
        return jdbc.sql("""
                        select u.name, i.role_code, i.org_id, o.name
                        from app_user u join user_identity i on i.user_id = u.id left join org o on o.id = i.org_id
                        where (u.name = :n or u.login = :n) and u.enabled
                        order by i.sort_order, i.id limit 1""")
                .param("n", n)
                .query((rs, i) -> new Caller(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .optional().orElse(new Caller(n, null, null, null));
    }

    /** viewer of a page payload; dev fallback uses the page's demo identity */
    static Caller viewer(JdbcClient jdbc, String page) {
        Actor a = currentActor();
        if (a == null) return null;
        String demo = page.startsWith("B") || page.equals("D1") ? "李敏" : page.equals("C3") ? "周敏" : null;
        return resolve(jdbc, a.name() != null ? a.name() : demo);
    }
}
