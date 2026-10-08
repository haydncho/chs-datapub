package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 预警提醒 (A11) for a 县区医保部门 identity (本县具名): only alerts of institutions in the identity's own
 * district are kept; the 回执率 is recomputed over the kept alerts.
 *
 * <p>The district is {@code org.district} of the identity's organisation when that is a county-level
 * institution; for a city-level or public organisation (示例市医保局, 公开汇总层) the district is taken from
 * the organisation name ("甲县医保局" → 甲县) and, failing that, nothing is shown — never the whole city.
 */
@Component
public class CountyAlertScope implements PageScopeFilter {

    private final JdbcClient jdbc;

    public CountyAlertScope(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean appliesTo(String code, Actor actor) {
        return "A11".equals(code) && actor.enforced() && "county".equals(actor.role());
    }

    @Override
    public void apply(ObjectNode payload, Actor actor) {
        Set<String> orgs = orgNamesInDistrict(districtOf(actor.orgId(), actor.orgName()));
        ArrayNode kept = payload.arrayNode();
        int sent = 0;
        int ack = 0;
        for (JsonNode a : payload.path("alerts")) {
            if (!orgs.contains(a.path("org").asText())) continue;
            kept.add(a);
            String st = a.path("status").asText();
            if ("sent".equals(st)) sent++;
            if ("ack".equals(st)) ack++;
        }
        payload.set("alerts", kept);
        payload.put("ackRate", sent + ack == 0 ? 0 : Math.round(ack * 100f / (sent + ack)));
    }

    /** county districts: districts of institutions other than the city itself */
    private List<String> countyDistricts() {
        return jdbc.sql("""
                select distinct district from org
                where district is not null and level not in ('医保局', '公开')
                  and district <> coalesce((select district from org where level = '医保局' order by id limit 1), '')""")
                .query(String.class).list();
    }

    /** @return the identity's 县区, or null when it cannot be determined (then nothing is visible) */
    public String districtOf(String orgId, String orgName) {
        if (orgId != null) {
            var row = jdbc.sql("select district, level from org where id = :id").param("id", orgId)
                    .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)}).optional();
            if (row.isPresent() && row.get()[0] != null && countyDistricts().contains(row.get()[0])) return row.get()[0];
        }
        if (orgName != null) {
            for (String d : countyDistricts()) {
                if (orgName.contains(d)) return d;
            }
        }
        return null;
    }

    public Set<String> orgNamesInDistrict(String district) {
        if (district == null) return Set.of();
        return jdbc.sql("select name from org where district = :d").param("d", district)
                .query(String.class).list().stream().collect(Collectors.toSet());
    }
}
