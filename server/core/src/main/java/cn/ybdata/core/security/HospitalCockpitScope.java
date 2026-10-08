package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 全景图 (cockpit) for a 定点医药机构 identity (本院具名 · 同级匿名分位). Removed from the payload:
 * <ul>
 *   <li>{@code identities[id=conv]} — the 医保局 view, whose alerts name other institutions
 *       (某肛肠专科医院 · GG19 …) and whose KPIs are city-wide management figures;</li>
 *   <li>{@code institutions} → [] — every institution by name with its 例均基金差额 (机构热力 view, conv only);</li>
 *   <li>{@code flows} → [] — 异地就医 destination ranking (prototype: “就医地机构排名仅医保局可见”);</li>
 *   <li>{@code matrix} → [], {@code matrixSummary} → 0 — 公开矩阵 internal publication monitoring (conv only).</li>
 * </ul>
 * The remaining {@code hosp} identity is labelled with the session user and organisation.
 *
 * <p>同级对标 ({@code peers}): each row is reduced to 本院's value plus the other institutions' values as an
 * unordered distribution — {@code own}, {@code others} (ascending), {@code ownIndex}; {@code values} is
 * rebuilt as {@code others} with {@code own} inserted at {@code ownIndex} (the slot the big screen marks
 * as 本院). No slot is tied to an institution, so nothing can be matched against B3's named peer list.
 *
 * <p>The stored 本院 figures are {@link HospitalOwnDataScope#SEED_ORG}'s. For a hospital of any other
 * organisation every 本院 field (KPIs, 钱/效/错, 待办, 闭环, 记账/支付 series, 科室, 同级对标) is emptied,
 * {@code noOwnData: true} is set and the city-wide reference tables are kept.
 */
@Component
@Order(20)
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
        if (HospitalOwnDataScope.ownsSeed(actor)) {
            for (JsonNode row : payload.path("peers")) {
                if (row instanceof ObjectNode r) anonymisePeer(r, OWN_SLOT);
            }
        } else {
            blankOwnData(payload, kept, actor);
        }
    }

    /** slot of 本院 in the stored {@code peers[].values} (and where the big screen draws 本院) */
    static final int OWN_SLOT = 2;

    static void anonymisePeer(ObjectNode r, int own) {
        JsonNode values = r.path("values");
        HospitalBenchmarkScope.anonymise(r, values, own);
        ArrayNode rebuilt = r.arrayNode();
        JsonNode others = r.path("others");
        for (int i = 0, k = 0; i < others.size() + 1; i++) {
            if (i == own) rebuilt.add(r.path("own"));
            else rebuilt.add(others.get(k++));
        }
        r.set("values", rebuilt);
        r.put("ownIndex", own);
    }

    private static void blankOwnData(ObjectNode payload, ArrayNode kept, Actor actor) {
        String org = actor.orgName() == null ? "本机构" : actor.orgName();
        payload.put("noOwnData", true);
        payload.put("noOwnDataNote", org + " · " + HospitalOwnDataScope.NOTE);
        for (JsonNode id : kept) {
            ObjectNode h = (ObjectNode) id;
            for (String f : new String[] {"kpis", "eff", "errs", "alerts", "loop"}) h.set(f, h.arrayNode());
            if (h.get("money") instanceof ObjectNode m) {
                m.put("budget", "—").put("spend", "—").put("balance", "—");
                m.set("bars", m.arrayNode());
            }
            h.set("recipients", h.arrayNode().add((actor.name() == null ? "" : actor.name() + " · ") + org));
            h.put("todo", "");
            // 季/年 figures and the 本院 DRG rows are H001 data too
            h.remove("periods");
            h.set("ownDrgs", h.arrayNode());
        }
        payload.set("hospRecorded", payload.arrayNode());
        payload.set("hospPaid", payload.arrayNode());
        payload.set("depts", payload.arrayNode());
        payload.set("peers", payload.arrayNode());
        payload.set("peerSummary", payload.objectNode().put("better", "—").put("watch", "—")
                .put("cmiPct", "—").put("diffPct", "—"));
    }
}
