package cn.ybdata.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validation of a 流程设计器 (A9) flow before it is published as a new version. Pure — unit-tested. */
final class FlowRules {
    private FlowRules() {}

    static final int COLS = 6;
    static final int MAX_NODES = 24;
    static final int MAX_DAYS = 60;
    static final Set<String> KINDS = Set.of("自动", "人工", "审批", "签收", "可选", "并行");
    static final String APPROVER = "召集人";
    static final String SIGNER = "定点医疗机构";

    /** critical path: every column takes its longest non-optional node */
    static int criticalPath(JsonNode nodes) {
        int total = 0;
        for (int c = 0; c < COLS; c++) {
            int m = 0;
            for (JsonNode n : nodes) {
                if (n.path("col").asInt(-1) == c && !"可选".equals(n.path("kind").asText())) m = Math.max(m, n.path("days").asInt(0));
            }
            total += m;
        }
        return total;
    }

    /**
     * @return the problems of the flow (empty = publishable): structure, 承办角色 of the approval and sign-off
     *         nodes, and the critical path against the 法定期限.
     */
    static List<String> problems(JsonNode nodes, Set<String> lanes, Set<String> timeoutActions, Set<String> channels, int legalLimit) {
        List<String> out = new ArrayList<>();
        if (!nodes.isArray() || nodes.isEmpty()) {
            out.add("流程至少需要 1 个节点");
            return out;
        }
        if (nodes.size() > MAX_NODES) out.add("节点不能超过 " + MAX_NODES + " 个");
        boolean[] used = new boolean[COLS];
        for (JsonNode n : nodes) {
            String name = n.path("name").asText("").trim();
            String kind = n.path("kind").asText();
            int col = n.path("col").asInt(-1);
            JsonNode days = n.path("days");
            if (name.isEmpty() || name.length() > 16) out.add("节点名称须为 1–16 个字");
            if (!KINDS.contains(kind)) out.add("未知节点类型:" + kind);
            if (!lanes.contains(n.path("lane").asText())) out.add("未知承办角色:" + n.path("lane").asText());
            if (col < 0 || col >= COLS) out.add("节点「" + name + "」的阶段无效");
            else used[col] = true;
            if (!days.canConvertToInt() || days.asInt() < 0 || days.asInt() > MAX_DAYS || days.asDouble() != days.asInt()) {
                out.add("节点「" + name + "」的办理时限须为 0–" + MAX_DAYS + " 的整数");
            }
            if (n.has("timeoutAction") && !timeoutActions.contains(n.path("timeoutAction").asText())) out.add("未知超时动作:" + n.path("timeoutAction").asText());
            for (JsonNode c : n.path("channels")) if (!channels.contains(c.asText())) out.add("未知通知渠道:" + c.asText());
        }
        // 起止节点完整: the flow starts in the first stage and has no empty stage before its last one
        int last = -1;
        for (int c = 0; c < COLS; c++) if (used[c]) last = c;
        boolean contiguous = used[0];
        for (int c = 0; c <= last; c++) contiguous &= used[c];
        if (!contiguous) out.add("起止节点不完整:流程须从「发起」阶段开始且各阶段连续");
        if (!hasNode(nodes, "审批", APPROVER)) out.add("须包含由召集人承办的审批节点");
        if (!hasNode(nodes, "签收", SIGNER)) out.add("须包含由定点医疗机构承办的签收节点");
        for (JsonNode n : nodes) {
            if ("审批".equals(n.path("kind").asText()) && !APPROVER.equals(n.path("lane").asText())) {
                out.add("审批节点「" + n.path("name").asText() + "」须由召集人承办");
            }
        }
        int total = criticalPath(nodes);
        if (total > legalLimit) out.add("关键路径 " + total + " 天超过法定期限 " + legalLimit + " 个工作日");
        return out;
    }

    static boolean hasNode(JsonNode nodes, String kind, String lane) {
        for (JsonNode n : nodes) if (kind.equals(n.path("kind").asText()) && lane.equals(n.path("lane").asText())) return true;
        return false;
    }
}
