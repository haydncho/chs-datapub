package cn.ybdata.core.security;

import cn.ybdata.core.page.PageScopeFilter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 本院具名 data for a 定点医药机构 identity on the institution pages (B1 B2 B4 B5 B7 D1).
 *
 * <p>The stored page payloads are one institution's own figures — {@link #SEED_ORG} (示例市第一人民医院).
 * A hospital identity of any <em>other</em> organisation must never receive them: every 本院 field is
 * replaced by an empty value of the same type (so the client's seed merge cannot fill it back in), the
 * payload is flagged {@code noOwnData: true} and carries {@code noOwnDataNote} for the empty state.
 * Generic content (发布机构, 口径说明, 签收须知 …) is kept. Bureau identities are not affected.
 *
 * <p>B3 and the 全景图 have their own filters ({@link HospitalBenchmarkScope}, {@link HospitalCockpitScope}).
 */
@Component
@Order(10)
public class HospitalOwnDataScope implements PageScopeFilter {

    /** organisation whose own figures the stored B/D page payloads are */
    public static final String SEED_ORG = "H001";
    public static final String NOTE = "暂无本院数据";

    private static final Set<String> PAGES = Set.of("B1", "B2", "B4", "B5", "B7", "D1");

    /** true when {@code actor} is a hospital identity whose own data is the stored payload */
    public static boolean ownsSeed(Actor actor) {
        return SEED_ORG.equals(actor.orgId());
    }

    @Override
    public boolean appliesTo(String code, Actor actor) {
        return PAGES.contains(code) && actor.isHospital() && !ownsSeed(actor);
    }

    @Override
    public void apply(ObjectNode p, Actor actor) {
        throw new UnsupportedOperationException("page code required");
    }

    @Override
    public void apply(String code, ObjectNode p, Actor actor) {
        String org = actor.orgName() == null ? "本机构" : actor.orgName();
        p.put("noOwnData", true);
        p.put("noOwnDataNote", org + " · " + NOTE);
        switch (code) {
            case "B1" -> {
                p.set("hospital", p.objectNode().put("name", org).put("subtitle", NOTE));
                p.set("todos", p.objectNode().put("reportsToSign", 0).put("verifyItems", 0)
                        .put("verifyDaysLeft", 0).put("watchItems", 0));
                p.set("kpis", p.arrayNode());
                p.set("settlement", p.objectNode().put("billed", 0).put("drgPaid", 0));
                p.put("defaultDrg", "");
                p.set("drgs", p.arrayNode());
            }
            case "B2" -> p.set("groups", p.arrayNode());
            case "B4" -> {
                // the report list itself (name, type, date, status) is released per institution by ReportScope;
                // the per-report cover, toc and overview are H001 data
                p.set("contents", p.objectNode());
                p.put("audience", "定向发布 · " + org);
                p.set("coverKpis", p.arrayNode());
                p.set("toc", p.arrayNode());
                p.set("overview", p.objectNode().put("page", "").put("title", "").put("text", "")
                        .set("stats", p.arrayNode()));
                p.put("signer", actor.name() == null ? "" : actor.name());
                p.put("checkNote", "");
                p.put("correctionNote", "");
                ObjectNode diff = p.objectNode().put("from", "").put("to", "").put("summary", "").put("section", "");
                diff.set("rows", p.arrayNode());
                diff.set("before", p.arrayNode());
                diff.set("after", p.arrayNode());
                p.set("diff", diff);
                p.set("readLog", p.objectNode().put("count", 0).put("last", ""));
            }
            case "B5" -> {
                p.put("draft", "");
                p.put("subtitle", NOTE);
                p.put("remainingDays", 0);
                p.set("items", p.arrayNode());
            }
            case "B7" -> {
                p.put("subtitle", NOTE);
                p.set("kpis", p.arrayNode());
                p.set("sources", p.arrayNode());
                p.set("drgs", p.arrayNode());
            }
            case "D1" -> blankD1(p, actor, org);
            default -> { }
        }
    }

    private static void blankD1(ObjectNode p, Actor actor, String org) {
        p.put("hospital", org);
        ObjectNode home = p.objectNode();
        home.set("deviation", p.objectNode().put("label", "医保记账 vs DRG 支付 偏离")
                .put("value", "—").put("unit", "").put("note", NOTE));
        home.set("kpis", p.arrayNode());
        p.set("home", home);
        p.set("todos", p.arrayNode());
        ObjectNode report = p.objectNode().put("meta", "").put("title", "").put("pages", "").put("pendingLabel", "");
        report.set("sections", p.arrayNode());
        p.set("report", report);
        ObjectNode alert = p.objectNode().put("level", "").put("meta", "").put("title", "").put("value", "")
                .put("unitNote", "").put("attribution", "").put("pendingNote", "").put("doneNote", "");
        alert.set("bars", p.arrayNode());
        p.set("alert", alert);
        p.set("messages", p.arrayNode());
        if (p.get("me") instanceof ObjectNode me) {
            me.put("name", actor.name() == null ? "" : actor.name());
            me.put("title", "");
            me.put("org", org + " · 本院具名");
            if (me.get("rows") instanceof ArrayNode rows) {
                for (var r : rows) if (r instanceof ObjectNode row && "签收记录".equals(row.path("k").asText())) row.put("v", "");
            }
        }
    }
}
