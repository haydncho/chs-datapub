package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图表与报告模板 (A5). State {@code tpl:<index>} → {version, dirty, charts: [[chart…] per section]}:
 * 加入 / 移除图表 edit the working copy (dirty), 保存为新版本 bumps the version (+1) and clears dirty.
 */
@Configuration
public class TemplateDomain {

    static final String PAGE = "A5";

    private final PageState state;

    public TemplateDomain(PageState state) {
        this.state = state;
    }

    private ArrayNode templates() {
        return state.seed(PAGE).path("templates") instanceof ArrayNode a ? a : state.json().createArrayNode();
    }

    /** working copy of template {@code ti}: stored state, else the seed */
    private ObjectNode working(int ti) {
        return state.get(PAGE, "tpl:" + ti).orElseGet(() -> {
            JsonNode t = templates().get(ti);
            ObjectNode o = state.json().createObjectNode();
            o.put("version", t.path("version").asInt(1)).put("dirty", false);
            ArrayNode charts = o.putArray("charts");
            for (JsonNode s : t.path("sections")) charts.add(s.path("charts").deepCopy());
            return o;
        });
    }

    private int template(JsonNode p) {
        return Checks.index(p, "template", "模板", 0, Math.max(0, templates().size() - 1));
    }

    private List<String> library() {
        List<String> out = new ArrayList<>();
        state.seed(PAGE).path("library").forEach(c -> out.add(c.path("name").asText()));
        return out;
    }

    private ObjectNode edit(JsonNode p, boolean add) {
        int ti = template(p);
        int si = Checks.index(p, "section", "章节", 0, Math.max(0, templates().get(ti).path("sections").size() - 1));
        String chart = Checks.text(p, "chart", "图表", 16, null);
        if (!library().contains(chart) && add) throw new IllegalArgumentException("组件库中没有「" + chart + "」");
        ObjectNode w = working(ti);
        ArrayNode charts = (ArrayNode) w.get("charts").get(si);
        int at = -1;
        for (int i = 0; i < charts.size(); i++) if (chart.equals(charts.get(i).asText())) at = i;
        String sec = templates().get(ti).path("sections").get(si).path("name").asText();
        if (add) {
            if (at >= 0) throw new IllegalArgumentException("「" + chart + "」已在「" + sec + "」中");
            charts.add(chart);
        } else {
            if (at < 0) throw new IllegalArgumentException("「" + sec + "」中没有「" + chart + "」");
            charts.remove(at);
        }
        w.put("dirty", true);
        return w;
    }

    @Bean
    ActionHandler a5AddChart() {
        return handler(PAGE, "addChart", (actor, p) -> {
            ObjectNode w = edit(p, true);
            state.put(PAGE, "tpl:" + p.get("template").asInt(), w, actor);
            return Map.of("dirty", true);
        });
    }

    @Bean
    ActionHandler a5RemoveChart() {
        return handler(PAGE, "removeChart", (actor, p) -> {
            ObjectNode w = edit(p, false);
            state.put(PAGE, "tpl:" + p.get("template").asInt(), w, actor);
            return Map.of("dirty", true);
        });
    }

    @Bean
    ActionHandler a5SaveTemplateVersion() {
        return handler(PAGE, "saveTemplateVersion", (actor, p) -> {
            int ti = template(p);
            ObjectNode w = working(ti);
            if (!w.path("dirty").asBoolean()) throw new IllegalArgumentException("当前模板没有未保存的修改");
            int v = w.path("version").asInt(1) + 1;
            w.put("version", v).put("dirty", false).put("savedBy", actor);
            state.put(PAGE, "tpl:" + ti, w, actor);
            return Map.of("version", v);
        });
    }

    @Bean
    @ConditionalOnProperty(name = "yb.auth.dev-header", havingValue = "true", matchIfMissing = true)
    ActionHandler a5ResetDemo() {
        return handler(PAGE, "resetDemo", (actor, p) -> {
            state.clear(PAGE);
            return null;
        });
    }

    @Bean
    PageOverlay a5Overlay() {
        return overlay(PAGE, payload -> {
            if (!(payload.get("templates") instanceof ArrayNode tpls)) return;
            state.all(PAGE, "tpl:").forEach((k, w) -> {
                int ti;
                try {
                    ti = Integer.parseInt(k);
                } catch (NumberFormatException e) {
                    return;
                }
                if (!(tpls.get(ti) instanceof ObjectNode t)) return;
                t.put("version", w.path("version").asInt(t.path("version").asInt()));
                t.put("dirty", w.path("dirty").asBoolean());
                JsonNode secs = t.path("sections");
                for (int i = 0; i < secs.size() && i < w.path("charts").size(); i++) {
                    if (secs.get(i) instanceof ObjectNode s) s.set("charts", w.path("charts").get(i).deepCopy());
                }
            });
        });
    }
}
