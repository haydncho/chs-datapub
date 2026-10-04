package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * B3 对标 PK for a 定点医药机构 identity. {@code metrics[].values} are index-aligned with the named
 * {@code peers} list, so a 匿名分位 ({@code pct}) or 匿名编号 ({@code anon}) metric would otherwise
 * reveal each named peer's value. For those tiers the other institutions' values are re-ordered
 * (ascending, 本院 slot untouched): the distribution — and therefore 本院's position — is unchanged,
 * but no value can be attributed to a peer. Only {@code named} metrics (经召集人审批开放具名) keep
 * the per-institution order. Without any named metric the peer names themselves are replaced by
 * 同级机构 A/B/… and the 具名排行 panel is emptied.
 */
@Component
public class HospitalBenchmarkScope implements PageScopeFilter {

    @Override
    public boolean appliesTo(String code, Actor actor) {
        return "B3".equals(code) && actor.isHospital();
    }

    @Override
    public void apply(ObjectNode payload, Actor actor) {
        int own = payload.path("ownIndex").asInt(-1);
        boolean anyNamed = false;
        String rankingMetric = payload.path("ranking").path("metric").asText("");
        boolean rankingNamed = false;
        for (JsonNode m : payload.path("metrics")) {
            String tier = m.path("tier").asText();
            if ("named".equals(tier)) {
                anyNamed = true;
                if (rankingMetric.equals(m.path("name").asText())) rankingNamed = true;
                continue;
            }
            if (m.get("values") instanceof ArrayNode values) shuffleOthers(values, own);
        }
        if (!rankingNamed && payload.get("ranking") instanceof ObjectNode r) r.put("metric", "");
        if (!anyNamed && payload.get("peers") instanceof ArrayNode peers) {
            int letter = 0;
            for (int i = 0; i < peers.size(); i++) {
                if (i == own) continue;
                peers.set(i, peers.textNode("同级机构 " + (char) ('A' + letter++)));
            }
        }
    }

    /** sort every value except the one at {@code own} ascending into the non-own slots */
    static void shuffleOthers(ArrayNode values, int own) {
        List<JsonNode> others = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) if (i != own) others.add(values.get(i));
        others.sort((a, b) -> Double.compare(a.asDouble(), b.asDouble()));
        int k = 0;
        for (int i = 0; i < values.size(); i++) if (i != own) values.set(i, others.get(k++));
    }
}
