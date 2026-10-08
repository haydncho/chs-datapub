package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform settings: 外观配置 (A15) and 展示策略 rules (A13). Both are validated on the server with the
 * same rules the pages use (web/src/app/appearance.ts normalizeAppearance, web/src/mock/A13.ts rule
 * bounds); the A13 read model shows the saved values.
 */
@Configuration
@RestController
public class SettingDomain {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public SettingDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void put(String key, JsonNode value, String actor) {
        jdbc.sql("""
                insert into setting (key, value, updated_by) values (:k, cast(:v as jsonb), :by)
                on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by, updated_at = now()""")
                .param("k", key).param("v", value.toString()).param("by", actor).update();
    }

    Optional<JsonNode> get(String key) {
        return jdbc.sql("select value::text from setting where key = :k").param("k", key)
                .query(String.class).optional().map(s -> {
                    try {
                        return json.readTree(s);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    @GetMapping("/api/v1/settings/{key}")
    public JsonNode read(@PathVariable String key) {
        if (!key.matches("appearance|display_policy")) throw new IllegalArgumentException("unknown setting");
        return get(key).orElse(json.createObjectNode());
    }

    // ───────────────────────── A15 外观配置 ─────────────────────────

    /** integer appearance fields → [min, max]; mirrors normalizeAppearance and the A15 rotation stepper (5–120 s) */
    static final Map<String, int[]> A15_INTS = new LinkedHashMap<>();
    static final int NAME_MAX = 40;
    static final double MIN_CONTRAST = 4.5;
    private static final Pattern HEX6 = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    static {
        A15_INTS.put("c", new int[] {0, 4});
        A15_INTS.put("dens", new int[] {0, 2});
        A15_INTS.put("rad", new int[] {0, 2});
        A15_INTS.put("font", new int[] {0, 2});
        A15_INTS.put("card", new int[] {0, 2});
        A15_INTS.put("scr", new int[] {0, 2});
        A15_INTS.put("mot", new int[] {0, 2});
        A15_INTS.put("rot", new int[] {5, 120});
        A15_INTS.put("wm", new int[] {0, 2});
        A15_INTS.put("menu", new int[] {0, 1});
        A15_INTS.put("zebra", new int[] {0, 1});
    }

    /** @return the cleaned appearance to store; IllegalArgumentException (400) with a user-facing reason otherwise */
    ObjectNode validAppearance(JsonNode p) {
        ObjectNode out = json.createObjectNode();
        for (Iterator<String> it = p.fieldNames(); it.hasNext(); ) {
            String f = it.next();
            if (!A15_INTS.containsKey(f) && !"name".equals(f) && !"custom".equals(f)) throw new IllegalArgumentException("未知外观字段: " + f);
        }
        for (var e : A15_INTS.entrySet()) {
            JsonNode v = p.get(e.getKey());
            boolean optional = "menu".equals(e.getKey()) || "zebra".equals(e.getKey());
            if (v == null || v.isNull()) {
                if (optional) continue;
                throw new IllegalArgumentException("缺少外观字段: " + e.getKey());
            }
            if (!v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() < e.getValue()[0] || v.asInt() > e.getValue()[1]) {
                throw new IllegalArgumentException("外观字段 " + e.getKey() + " 须为 " + e.getValue()[0] + "–" + e.getValue()[1] + " 的整数");
            }
            out.put(e.getKey(), v.asInt());
        }
        JsonNode name = p.get("name");
        if (name == null || !name.isTextual() || name.asText().trim().isEmpty()) throw new IllegalArgumentException("平台名称不能为空");
        String n = name.asText().trim();
        if (n.length() > NAME_MAX) throw new IllegalArgumentException("平台名称最多 " + NAME_MAX + " 个字");
        if (n.chars().anyMatch(c -> c < 0x20 || c == 0x7f || c == '<' || c == '>')) throw new IllegalArgumentException("平台名称含非法字符");
        out.put("name", n);
        JsonNode custom = p.get("custom");
        String hex = custom == null || custom.isNull() ? "" : custom.isTextual() ? custom.asText().trim() : null;
        if (hex == null) throw new IllegalArgumentException("自定义主题色格式不正确");
        if (!hex.isEmpty()) {
            if (!HEX6.matcher(hex).matches()) throw new IllegalArgumentException("自定义主题色须为 #RRGGBB");
            hex = hex.toUpperCase();
            if (contrastWithWhite(hex) < MIN_CONTRAST) {
                throw new IllegalArgumentException("自定义主题色与白色文字对比度不足 " + MIN_CONTRAST + ":1");
            }
        }
        if (custom != null) out.put("custom", hex);
        return out;
    }

    /** WCAG contrast of white text on {@code #RRGGBB} (same formula as appearance.ts contrastWithWhite). */
    static double contrastWithWhite(String hex) {
        int n = Integer.parseInt(hex.substring(1), 16);
        double l = 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
        return 1.05 / (l + 0.05);
    }

    private static double channel(int c) {
        double x = c / 255.0;
        return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    @Bean
    ActionHandler a15Publish() {
        return handler("A15", "publishAppearance", (actor, p) -> {
            ObjectNode clean = validAppearance(p);
            put("appearance", clean, actor);
            return clean;
        });
    }

    @Bean
    ActionHandler a15Reset() {
        return handler("A15", "resetAppearance", (actor, p) -> {
            // an empty object (rather than no row) = "platform default", which every client applies on sync
            put("appearance", json.createObjectNode(), actor);
            return json.createObjectNode();
        });
    }

    // ───────────────────────── A13 展示策略 ─────────────────────────

    /** number rule → [min, max, step]; mirrors web/src/mock/A13.ts */
    static final Map<String, int[]> A13_NUMBERS = Map.of("minOrg", new int[] {2, 10, 1}, "minCase", new int[] {10, 100, 5});
    static final java.util.Set<String> A13_SWITCHES = java.util.Set.of("wm", "exp", "named");

    static void validRule(String key, JsonNode v) {
        if (A13_NUMBERS.containsKey(key)) {
            int[] b = A13_NUMBERS.get(key);
            if (v == null || !v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() < b[0] || v.asInt() > b[1]
                    || (v.asInt() - b[0]) % b[2] != 0) {
                throw new IllegalArgumentException("取值须为 " + b[0] + "–" + b[1] + (b[2] > 1 ? " 之间、步长 " + b[2] : "") + " 的整数");
            }
        } else if (A13_SWITCHES.contains(key)) {
            if (v == null || !v.isBoolean()) throw new IllegalArgumentException("取值须为 true / false");
        } else {
            throw new IllegalArgumentException("未知规则: " + key);
        }
    }

    @Bean
    ActionHandler a13SetRule() {
        return handler("A13", "setPolicyRule", (actor, p) -> {
            String k = Json.text(p, "key");
            validRule(k, p.get("value"));
            JsonNode stored = get("display_policy").orElse(null);
            ObjectNode cur = stored instanceof ObjectNode o ? o : json.createObjectNode();
            cur.set(k, p.get("value"));
            put("display_policy", cur, actor);
            return cur;
        });
    }

    /** A13 shows the saved rule values (seed values where nothing was saved, or a saved value is invalid). */
    @Bean
    PageOverlay a13Overlay() {
        return overlay("A13", payload -> {
            JsonNode saved = get("display_policy").orElse(null);
            if (!(saved instanceof ObjectNode s) || !(payload.get("rules") instanceof com.fasterxml.jackson.databind.node.ArrayNode rules)) return;
            for (JsonNode r : rules) {
                if (!(r instanceof ObjectNode rule)) continue;
                String key = rule.path("key").asText();
                JsonNode v = s.get(key);
                if (v == null) continue;
                try {
                    validRule(key, v);
                    rule.set("value", v);
                } catch (IllegalArgumentException ignored) { /* keep the seed value */ }
            }
        });
    }
}
