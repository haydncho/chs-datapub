package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * B3 对标 PK for a 定点医药机构 identity.
 *
 * <p>The stored payload has {@code metrics[].values} index-aligned with the named {@code peers}
 * list, and the seed lists both in a metric-correlated order. For a hospital viewer:
 * <ul>
 *   <li><b>本院</b> is the peer whose name equals the viewer's organisation — never assumed from the
 *       stored {@code ownIndex}. A viewer outside the peer group gets no 本院 position at all
 *       ({@code ownIndex = -1}, {@code noOwnData = true}, every metric without values).</li>
 *   <li><b>peers</b> are re-listed in a fixed, data-independent order (by name), so the order of the
 *       list itself no longer reveals any ranking.</li>
 *   <li><b>匿名分位 / 匿名编号</b> metrics ({@code pct} / {@code anon}) lose {@code values} entirely: only
 *       {@code own} (本院's value) and {@code others} (the other institutions' values as an unordered
 *       distribution, ascending) are sent. Nothing in the payload is position-aligned with a peer name,
 *       so no value can be attributed to an institution — whatever order the source data was in.</li>
 *   <li><b>具名</b> metrics (经召集人审批开放具名) keep per-institution {@code values}, re-aligned to the
 *       new peer order. Without any named metric the other peer names become 同级机构 A/B/… and the
 *       具名排行 panel is emptied.</li>
 * </ul>
 */
@Component
@Order(20)
public class HospitalBenchmarkScope implements PageScopeFilter {

    private static final Comparator<String> BY_NAME = Collator.getInstance(Locale.CHINA)::compare;

    @Override
    public boolean appliesTo(String code, Actor actor) {
        return "B3".equals(code) && actor.isHospital();
    }

    @Override
    public void apply(ObjectNode payload, Actor actor) {
        List<String> names = new ArrayList<>();
        payload.path("peers").forEach(p -> names.add(p.asText()));
        int ownSrc = actor.orgName() == null ? -1 : names.indexOf(actor.orgName());

        // fixed order: by name — independent of every metric's values
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) order.add(i);
        order.sort((a, b) -> BY_NAME.compare(names.get(a), names.get(b)));
        int own = ownSrc < 0 ? -1 : order.indexOf(ownSrc);
        int storedOwn = payload.path("ownIndex").asInt(-1);

        boolean anyNamed = false;
        boolean rankingNamed = false;
        String rankingMetric = payload.path("ranking").path("metric").asText("");
        for (JsonNode node : payload.path("metrics")) {
            if (!(node instanceof ObjectNode m)) continue;
            JsonNode src = m.path("values");
            if (ownSrc >= 0 && ownSrc != storedOwn) {
                m.put("ownValue", formatLike(m.path("ownValue").asText(""), src.path(ownSrc).asDouble()));
            }
            if ("named".equals(m.path("tier").asText())) {
                anyNamed = true;
                if (rankingMetric.equals(m.path("name").asText())) rankingNamed = true;
                ArrayNode re = m.arrayNode();
                for (int i : order) re.add(src.path(i));
                m.set("values", re);
            } else {
                anonymise(m, src, ownSrc);
            }
            if (ownSrc < 0) {
                m.remove("values");
                m.remove("own");
                m.set("others", m.arrayNode());
                m.put("ownValue", "—");
            }
        }

        ArrayNode peers = payload.arrayNode();
        int letter = 0;
        for (int k = 0; k < order.size(); k++) {
            String n = names.get(order.get(k));
            if (anyNamed || k == own) peers.add(n);
            else peers.add("同级机构 " + (char) ('A' + letter++));
        }
        payload.set("peers", peers);
        payload.put("ownIndex", own);
        if (!rankingNamed && payload.get("ranking") instanceof ObjectNode r) r.put("metric", "");
        if (ownSrc < 0) {
            payload.put("noOwnData", true);
            if (payload.get("ranking") instanceof ObjectNode r) r.put("metric", "");
            String org = actor.orgName() == null ? "本机构" : actor.orgName();
            payload.put("subtitle", org + " 不在该同级组 · 暂无本院对标数据");
        }
    }

    /** format {@code v} with the decimals and unit suffix of the stored label ("6.8%" → 1 decimal + "%") */
    static String formatLike(String label, double v) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^[^0-9-]*-?[0-9,]*(?:\\.([0-9]+))?(.*)$").matcher(label);
        int decimals = 2;
        String suffix = "";
        if (mt.matches()) {
            decimals = mt.group(1) == null ? 0 : mt.group(1).length();
            suffix = mt.group(2);
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", v) + suffix;
    }

    /** replace position-aligned values by 本院's value plus the unordered distribution of the others */
    static void anonymise(ObjectNode m, JsonNode values, int own) {
        List<Double> others = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) if (i != own) others.add(values.get(i).asDouble());
        others.sort(Double::compare);
        ArrayNode o = m.arrayNode();
        others.forEach(o::add);
        m.remove("values");
        if (own >= 0 && own < values.size()) m.set("own", values.get(own));
        m.set("others", o);
    }
}
