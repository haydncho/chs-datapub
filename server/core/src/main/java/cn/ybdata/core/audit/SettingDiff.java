package cn.ybdata.core.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Before/after diff for configuration events (A13 展示策略, A15 外观配置). The "before" value is not
 * stored in the event; it is the value set by the previous event of the same setting (or the platform
 * default when there is none), so the trail stays a plain append-only log.
 */
public final class SettingDiff {

    public record Change(String field, String label, String from, String to) {}

    public record Diff(String from, String to, List<Change> changes) {}

    /** A13 rule key → [label, unit, default]. Mirrors web/src/mock/A13.ts. */
    private static final Map<String, String[]> A13_RULES = new LinkedHashMap<>();
    /** A15 appearance field → [label, option labels…]; numeric fields without options use the raw value. */
    private static final Map<String, String[]> A15_FIELDS = new LinkedHashMap<>();
    /** web/src/app/appearance.ts DEFAULT_APPEARANCE */
    private static final Map<String, String> A15_DEFAULT = new LinkedHashMap<>();

    static {
        A13_RULES.put("minOrg", new String[] {"小样本抑制 · 同级机构数", "家", "5"});
        A13_RULES.put("minCase", new String[] {"小样本抑制 · 病组病例数", "例", "30"});
        A13_RULES.put("wm", new String[] {"动态水印", "", "true"});
        A13_RULES.put("exp", new String[] {"允许机构导出 PDF", "", "true"});
        A13_RULES.put("named", new String[] {"具名排行需逐指标审批", "", "true"});

        A15_FIELDS.put("c", new String[] {"主题色", "政务蓝", "医保青", "深海蓝", "中国红", "墨绿"});
        A15_FIELDS.put("dens", new String[] {"信息密度", "紧凑", "标准", "宽松"});
        A15_FIELDS.put("rad", new String[] {"圆角", "直角 4", "标准 12", "圆润 18"});
        A15_FIELDS.put("font", new String[] {"正文字号", "12px", "13px", "14px"});
        A15_FIELDS.put("card", new String[] {"卡片样式", "描边", "渐变", "投影"});
        A15_FIELDS.put("scr", new String[] {"大屏配色", "深空蓝", "墨黑", "政务蓝"});
        A15_FIELDS.put("mot", new String[] {"动效", "关闭", "标准", "丰富"});
        A15_FIELDS.put("rot", new String[] {"大屏轮播(秒)"});
        A15_FIELDS.put("wm", new String[] {"水印浓度", "浅", "标准", "深"});
        A15_FIELDS.put("name", new String[] {"平台名称"});
        A15_DEFAULT.putAll(Map.of("c", "0", "dens", "1", "rad", "1", "font", "1", "card", "1", "scr", "0",
                "mot", "1", "rot", "20", "wm", "1"));
        A15_DEFAULT.put("name", "医保数据公开 · 定向发布平台");
    }

    private SettingDiff() {}

    /** Events whose diff is computed; {@code settingKey} identifies "the same setting". */
    public static boolean applies(String page, String action) {
        return ("A13".equals(page) && "setPolicyRule".equals(action))
                || ("A15".equals(page) && ("publishAppearance".equals(action) || "resetAppearance".equals(action)));
    }

    /**
     * @param prev payload of the previous event of the same setting (null = none → platform default);
     *             for A15 a previous {@code resetAppearance} counts as the default, pass null for it.
     */
    public static Diff compute(String page, String action, JsonNode payload, JsonNode prev) {
        if ("A13".equals(page)) return a13(payload, prev);
        return a15("resetAppearance".equals(action) ? null : payload, prev);
    }

    private static Diff a13(JsonNode payload, JsonNode prev) {
        String key = payload.path("key").asText();
        String[] rule = A13_RULES.getOrDefault(key, new String[] {key, "", null});
        String before = prev != null && prev.has("value") ? raw(prev.get("value")) : rule[2];
        String after = raw(payload.get("value"));
        String from = rule[0] + " " + a13Value(before, rule[1]);
        String to = rule[0] + " " + a13Value(after, rule[1]);
        return new Diff(from, to, List.of(new Change(key, rule[0], a13Value(before, rule[1]), a13Value(after, rule[1]))));
    }

    private static String a13Value(String v, String unit) {
        if (v == null) return "未设置";
        if ("true".equals(v)) return "开启";
        if ("false".equals(v)) return "关闭";
        return v + (unit.isEmpty() ? "" : " " + unit);
    }

    private static Diff a15(JsonNode now, JsonNode prev) {
        List<Change> changes = new ArrayList<>();
        StringJoiner from = new StringJoiner(" · ");
        StringJoiner to = new StringJoiner(" · ");
        A15_FIELDS.forEach((field, spec) -> {
            String b = prev != null && prev.has(field) ? raw(prev.get(field)) : A15_DEFAULT.get(field);
            String a = now != null && now.has(field) ? raw(now.get(field)) : A15_DEFAULT.get(field);
            if (b == null ? a == null : b.equals(a)) return;
            String bl = a15Value(spec, b);
            String al = a15Value(spec, a);
            changes.add(new Change(field, spec[0], bl, al));
            from.add(spec[0] + " " + bl);
            to.add(spec[0] + " " + al);
        });
        if (changes.isEmpty()) return new Diff("无变化", "无变化", changes);
        return new Diff(from.toString(), to.toString(), changes);
    }

    private static String a15Value(String[] spec, String v) {
        if (v == null) return "未设置";
        if (spec.length > 1) {
            try {
                int i = Integer.parseInt(v);
                if (i >= 0 && i < spec.length - 1) return spec[i + 1];
            } catch (NumberFormatException ignored) {
                // fall through to the raw value
            }
        }
        return v;
    }

    private static String raw(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        return n.isValueNode() ? n.asText() : n.toString();
    }
}
