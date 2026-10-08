package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.audit.AuditTypes;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标配置 (A4). State in {@code page_state}:
 * {@code tier:<indicator>} → pending 对标档位 change; {@code package} → indicators in the target 发布包;
 * {@code indicator:<name>} → indicators submitted from the 新建指标 wizard (审批中, with a server-issued 审批编号).
 */
@Configuration
public class IndicatorDomain {

    static final String PAGE = "A4";
    static final List<String> TIERS = List.of("pct", "anon", "named");
    static final List<String> GROUPS = List.of("钱", "效", "错");
    static final List<String> FREQS = List.of("月", "季", "年");
    static final List<String> DIMS = List.of("机构", "等级", "病组", "时间", "县区", "险种");
    static final List<String> CHARTS = List.of("分位条", "趋势线", "分组柱", "散点");
    static final List<String> GRAINS = List.of("统筹区", "县区", "机构", "病组");
    /** 审批编号 ZB-<year>-<seq>; the seed's wizard shows the first one (0917) */
    static final int FIRST_APPROVAL_SEQ = 917;

    private final PageState state;

    public IndicatorDomain(PageState state) {
        this.state = state;
    }

    private Map<String, ObjectNode> seededIndicators() {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        for (JsonNode n : state.seed(PAGE).path("indicators")) {
            if (n instanceof ObjectNode o && o.hasNonNull("name")) out.put(o.get("name").asText(), o);
        }
        return out;
    }

    static String approvalNo(int submitted) {
        return "ZB-" + LocalDate.now(AuditTypes.ZONE).getYear() + "-" + String.format("%04d", FIRST_APPROVAL_SEQ + submitted);
    }

    static String today() {
        return LocalDate.now(AuditTypes.ZONE).toString();
    }

    @Bean
    ActionHandler a4RequestTierChange() {
        return handler(PAGE, "requestTierChange", (actor, p) -> {
            String name = Checks.text(p, "indicator", "指标", 32, null);
            ObjectNode ind = seededIndicators().get(name);
            if (ind == null) throw new IllegalArgumentException("未知指标:" + name);
            if ("internal".equals(ind.path("source").asText())) throw new IllegalArgumentException("仅内部指标不可配置对标档位");
            String to = Checks.oneOf(p, "to", "目标档位", TIERS);
            if (to.equals(ind.path("tier").asText())) throw new IllegalArgumentException("目标档位与当前档位相同");
            if (state.get(PAGE, "tier:" + name).isPresent()) throw new IllegalArgumentException(name + " 已有待审批的档位变更,请等待召集人审批");
            ObjectNode st = state.json().createObjectNode()
                    .put("from", ind.path("tier").asText()).put("to", to).put("by", actor).put("date", today());
            state.put(PAGE, "tier:" + name, st, actor);
            return Map.of("pending", to);
        });
    }

    @Bean
    ActionHandler a4AddToPackage() {
        return handler(PAGE, "addToPackage", (actor, p) -> {
            List<String> names = Checks.texts(p, "indicators", "指标", 50, 32, null);
            if (names.isEmpty()) throw new IllegalArgumentException("请先勾选要加入发布包的指标");
            Map<String, ObjectNode> seeded = seededIndicators();
            Map<String, ObjectNode> submitted = state.all(PAGE, "indicator:");
            for (String n : names) {
                JsonNode ind = seeded.containsKey(n) ? seeded.get(n) : submitted.get(n);
                if (ind == null) throw new IllegalArgumentException("未知指标:" + n);
                if ("internal".equals(ind.path("source").asText()) || ind.path("internalOnly").asBoolean()) {
                    throw new IllegalArgumentException("仅内部指标不可加入发布包:" + n);
                }
            }
            String pkg = state.seed(PAGE).path("targetPackage").asText("发布包");
            ObjectNode st = state.getOrNew(PAGE, "package");
            Set<String> items = new LinkedHashSet<>();
            st.path("items").forEach(n -> items.add(n.asText()));
            List<String> added = names.stream().filter(items::add).toList();
            if (added.isEmpty()) throw new IllegalArgumentException("所选指标均已在 " + pkg + " 中");
            st.put("package", pkg);
            ArrayNode arr = st.putArray("items");
            items.forEach(arr::add);
            state.put(PAGE, "package", st, actor);
            return Map.of("added", added, "package", pkg, "total", items.size());
        });
    }

    /** validate a 新建指标 submission; returns the stored indicator row (A4Indicator shape + wizard fields) */
    ObjectNode validateNew(JsonNode p, String actor) {
        String name = Checks.text(p, "name", "指标名称", 30, Checks.NAME);
        if (name.codePointCount(0, name.length()) < 2) throw new IllegalArgumentException("指标名称至少 2 个字");
        String group = Checks.oneOf(p, "group", "监测维度", GROUPS);
        String domain = Checks.text(p, "domain", "监测子域", 16, Checks.NAME);
        String numerator = Checks.text(p, "numerator", "分子", 60, Checks.EXPR);
        String denominator = Checks.text(p, "denominator", "分母", 60, Checks.EXPR);
        if (numerator.equals(denominator)) throw new IllegalArgumentException("分子与分母不能相同");
        List<String> filters = Checks.texts(p, "filters", "过滤条件", 8, 30, Checks.EXPR);
        List<String> dims = Checks.texts(p, "dims", "维度", DIMS.size(), 4, null);
        if (dims.isEmpty() || !DIMS.containsAll(dims)) throw new IllegalArgumentException("请至少选择 1 个维度(" + String.join(" / ", DIMS) + ")");
        String freq = Checks.oneOf(p, "freq", "更新频次", FREQS);
        String chart = Checks.oneOf(p, "chart", "图表模板", CHARTS);
        String grain = Checks.oneOf(p, "granularity", "粒度上限", GRAINS);
        String note = Checks.optionalText(p, "note", "解读文字", 200, null);
        if (note.contains("<") || note.contains(">")) throw new IllegalArgumentException("解读文字含有不允许的特殊字符");
        boolean internal = Checks.bool(p, "internalOnly");
        String tier;
        if (internal) {
            JsonNode t = p.get("tier");
            if (t != null && !t.isNull() && !"none".equals(t.asText())) {
                throw new IllegalArgumentException("仅内部指标不可配置对标档位");
            }
            tier = "none";
        } else {
            tier = Checks.oneOf(p, "tier", "对标档位", TIERS);
        }
        Map<String, ObjectNode> submitted = state.all(PAGE, "indicator:");
        if (seededIndicators().containsKey(name) || submitted.containsKey(name)) {
            throw new IllegalArgumentException("已存在同名指标「" + name + "」");
        }
        String no = approvalNo(submitted.size());
        JsonNode askedNo = p.get("approvalNo");
        if (askedNo != null && askedNo.isTextual()) {
            String a = askedNo.asText();
            boolean used = submitted.values().stream().anyMatch(s -> a.equals(s.path("approvalNo").asText()));
            if (used) throw new IllegalArgumentException("审批编号 " + a + " 已提交过,请勿重复提交");
        }
        ObjectNode o = state.json().createObjectNode();
        o.put("name", name).put("group", group).put("domain", domain)
                .put("source", internal ? "internal" : "local").put("tier", tier).put("freq", freq)
                .put("version", "v0.1").put("status", "review").put("refs", 0)
                .put("numerator", numerator).put("denominator", denominator)
                .put("internalOnly", internal).put("chart", chart).put("granularity", grain).put("note", note)
                .put("approvalNo", no).put("submittedBy", actor).put("submittedAt", today());
        ArrayNode f = o.putArray("filters");
        filters.forEach(f::add);
        ArrayNode d = o.putArray("dims");
        dims.forEach(d::add);
        ArrayNode h = o.putArray("history");
        h.addObject().put("version", "v0.1").put("date", today()).put("author", actor)
                .put("note", "新建 · 提交上线审批 " + no);
        return o;
    }

    @Bean
    ActionHandler a4SubmitIndicator() {
        return handler(PAGE, "submitIndicator", (actor, p) -> {
            ObjectNode row = validateNew(p, actor);
            state.put(PAGE, "indicator:" + row.get("name").asText(), row, actor);
            return Map.of("approvalNo", row.get("approvalNo").asText(), "indicator", row);
        });
    }

    @Bean
    @ConditionalOnProperty(name = "yb.auth.dev-header", havingValue = "true", matchIfMissing = true)
    ActionHandler a4ResetDemo() {
        return handler(PAGE, "resetDemo", (actor, p) -> {
            state.clear(PAGE);
            return null;
        });
    }

    @Bean
    PageOverlay a4Overlay() {
        return overlay(PAGE, payload -> {
            Map<String, ObjectNode> tiers = state.all(PAGE, "tier:");
            Set<String> pkg = new LinkedHashSet<>();
            state.get(PAGE, "package").ifPresent(s -> s.path("items").forEach(n -> pkg.add(n.asText())));
            Map<String, ObjectNode> submitted = state.all(PAGE, "indicator:");
            if (payload.get("indicators") instanceof ArrayNode arr) {
                submitted.values().forEach(arr::add);
                for (JsonNode n : arr) {
                    if (!(n instanceof ObjectNode o)) continue;
                    String name = o.path("name").asText();
                    ObjectNode t = tiers.get(name);
                    if (t != null) {
                        o.put("pendingTier", t.path("to").asText());
                        if (o.get("history") instanceof ArrayNode h) {
                            h.insertObject(0).put("version", o.path("version").asText()).put("date", t.path("date").asText())
                                    .put("author", t.path("by").asText()).put("note", "申请对标档位变更(审批中)").put("pending", true);
                        }
                    }
                    if (pkg.contains(name)) o.put("inPackage", true);
                }
            }
            if (payload.get("wizard") instanceof ObjectNode w) w.put("approvalNo", approvalNo(submitted.size()));
        });
    }
}
