package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * 全息图 (cockpit) for a 定点医药机构 identity (本院具名 · 同级匿名分位). Removed from the payload:
 * <ul>
 *   <li>{@code identities[id=conv]} — the 医保局 view, whose alerts name other institutions
 *       (某肛肠专科医院 · GG19 …) and whose KPIs are city-wide management figures;</li>
 *   <li>{@code institutions} → [] — every institution by name with its 例均基金差额 (机构热力 view, conv only);</li>
 *   <li>{@code flows} → [] — 异地就医 destination ranking (prototype: “就医地机构排名仅医保局可见”);</li>
 *   <li>{@code matrix} → [], {@code matrixSummary} → 0 — 公开矩阵 internal publication monitoring (conv only).</li>
 * </ul>
 * The remaining {@code hosp} identity is labelled with the session user and organisation.
 * Kept: city DRG reference table (no institution names), 本院 departments, anonymous peer distributions.
 */
@Component
public class HospitalCockpitScope implements PageScopeFilter {

    @Override
    public boolean appliesTo(String code, Actor actor) {
        return "cockpit".equals(code) && actor.isHospital();
    }

    @Override
    public void apply(ObjectNode payload, Actor actor) {
        ArrayNode kept = payload.arrayNode();
        JsonNode ids = payload.path("identities");
        for (JsonNode id : ids) {
            if (!"hosp".equals(id.path("id").asText())) continue;
            ObjectNode own = ((ObjectNode) id).deepCopy();
            if (actor.name() != null) own.put("name", actor.name());
            if (actor.orgName() != null) {
                own.put("org", actor.orgName());
                own.put("orgShort", actor.orgName());
            }
            kept.add(own);
        }
        payload.set("identities", kept);
        payload.set("institutions", payload.arrayNode());
        payload.set("flows", payload.arrayNode());
        payload.set("matrix", payload.arrayNode());
        if (payload.has("matrixSummary")) {
            payload.set("matrixSummary", payload.objectNode().put("unpublished", 0).put("unread", 0).put("unanswered", 0));
        }
    }
}
