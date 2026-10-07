package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 全景图 (cockpit) for every logged-in identity that is NOT a 定点医药机构 (that one is
 * {@link HospitalCockpitScope}). The big screen shows what the identity is entitled to:
 * <ul>
 *   <li>{@code identities} → only the 市医保局 view ({@code conv}); the 医院 view is one specific
 *       hospital's own figures and is not part of the 医保局 big screen;</li>
 *   <li>{@code county} (县区医保部门): additionally {@code institutions} are limited to the
 *       identity's own district, {@code alerts} to those naming a kept institution, and the
 *       internal publication monitoring ({@code matrix}) is removed — 本县具名, other 县区 aggregated only.</li>
 * </ul>
 * Not applied to the legacy dev fallback (no credentials), which keeps the full demo payload.
 */
@Component
public class BureauCockpitScope implements PageScopeFilter {

    @Override
    public boolean appliesTo(String code, Actor actor) {
        return "cockpit".equals(code) && actor.enforced() && !actor.isHospital();
    }

    @Override
    public void apply(ObjectNode payload, Actor actor) {
        ArrayNode conv = payload.arrayNode();
        for (JsonNode id : payload.path("identities")) {
            if ("conv".equals(id.path("id").asText())) conv.add(id);
        }
        payload.set("identities", conv);
        if ("county".equals(actor.role())) limitToCounty(payload, conv, actor.orgName());
    }

    private static void limitToCounty(ObjectNode payload, ArrayNode conv, String orgName) {
        ArrayNode keptInstitutions = payload.arrayNode();
        Set<String> names = new HashSet<>();
        for (JsonNode inst : payload.path("institutions")) {
            String district = inst.path("district").asText("");
            if (orgName != null && !district.isEmpty() && orgName.contains(district)) {
                keptInstitutions.add(inst);
                names.add(inst.path("name").asText());
            }
        }
        payload.set("institutions", keptInstitutions);
        payload.set("matrix", payload.arrayNode());
        if (payload.has("matrixSummary")) {
            payload.set("matrixSummary", payload.objectNode().put("unpublished", 0).put("unread", 0).put("unanswered", 0));
        }
        for (JsonNode id : conv) {
            ArrayNode keptAlerts = payload.arrayNode();
            for (JsonNode alert : id.path("alerts")) {
                String text = alert.path("text").asText("");
                if (names.stream().anyMatch(text::contains)) keptAlerts.add(alert);
            }
            ((ObjectNode) id).set("alerts", keptAlerts);
        }
    }
}
