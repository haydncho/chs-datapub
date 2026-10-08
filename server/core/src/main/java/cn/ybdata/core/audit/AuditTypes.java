package cn.ybdata.core.audit;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The single table that maps recorded action names to the A14 审计日志 event-type vocabulary
 * (查阅 / 审批 / 配置 / 导出 / 权限 / 发布 / 登录 / 删除), plus the off-hours rule.
 *
 * <p>Lookup order: exact action name → first matching name prefix → {@link #OTHER}. The same table
 * is rendered to a SQL {@code CASE} expression ({@link #sqlCase(String)}) so the type filter runs in
 * the database and always agrees with {@link #classify(String)}.
 */
public final class AuditTypes {

    public static final String VIEW = "查阅";
    public static final String APPROVE = "审批";
    public static final String CONFIG = "配置";
    public static final String EXPORT = "导出";
    public static final String PERMISSION = "权限";
    public static final String PUBLISH = "发布";
    public static final String LOGIN = "登录";
    public static final String DELETE = "删除";
    public static final String OTHER = "其他";

    /** Filter-chip order on A14 (全部 is the UI's own "no filter"). */
    public static final List<String> VOCABULARY = List.of(VIEW, APPROVE, CONFIG, EXPORT, PERMISSION, PUBLISH, LOGIN, DELETE, OTHER);

    /** Platform time zone for the off-hours rule and the A14 time column. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 非工作时间: 22:00 (inclusive) – 06:00 (exclusive), platform time. */
    public static final int OFF_HOURS_FROM = 22;
    public static final int OFF_HOURS_TO = 6;

    /** type + Chinese verb used in the 对象 column; {@code known} = listed by exact name (else label is the raw action). */
    public record Kind(String type, String label, boolean known) {}

    private static final Map<String, Kind> EXACT = new LinkedHashMap<>();
    /** prefix → type; checked in insertion order, first hit wins. */
    private static final Map<String, String> PREFIX = new LinkedHashMap<>();
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9]{0,63}$");

    private static void exact(String action, String type, String label) {
        EXACT.put(action, new Kind(type, label, true));
    }

    static {
        // 登录 (A1)
        exact("login", LOGIN, "登录认证");
        exact("logout", LOGIN, "退出登录");
        exact("sendSmsCode", LOGIN, "下发短信验证码");
        exact("selectIdentity", LOGIN, "选择身份进入");
        // 审批
        exact("approvePublish", APPROVE, "批准发布");
        exact("rejectPublish", APPROVE, "驳回发布");
        exact("approveSection", APPROVE, "审定段落");
        exact("submitReview", APPROVE, "提交审核");
        exact("requestTierChange", APPROVE, "提交档位变更审批");
        exact("submitIndicator", APPROVE, "提交指标上线审批");
        exact("signReport", APPROVE, "签收报告");
        exact("submitVerification", APPROVE, "提交核对结果");
        exact("assignFeedback", APPROVE, "分派意见");
        exact("replyFeedback", APPROVE, "答复意见");
        exact("submitReceipt", APPROVE, "提交回执");
        exact("requestReview", APPROVE, "申请复核");
        exact("adoptTopic", APPROVE, "采纳选题");
        exact("skipTopic", APPROVE, "本期不做");
        exact("addToTopic", APPROVE, "转专题选题");
        exact("resolveComment", APPROVE, "处理批注");
        exact("submitSuggestion", APPROVE, "提交监督建议");
        exact("ackAlarm", APPROVE, "确认处置告警");
        // 配置
        exact("setPolicyRule", CONFIG, "展示策略");
        exact("publishAppearance", CONFIG, "外观配置发布");
        exact("resetAppearance", CONFIG, "外观恢复默认");
        exact("publishFlowVersion", CONFIG, "流程版本发布");
        exact("saveTemplateVersion", CONFIG, "模板版本保存");
        exact("addChart", CONFIG, "模板加入图表");
        exact("saveSubscription", CONFIG, "订阅推送设置");
        exact("resetDemo", CONFIG, "演示重置");
        exact("completeQualityCheck", CONFIG, "完成质量校验");
        exact("retryPull", CONFIG, "重新拉取数据");
        exact("regenerateSection", CONFIG, "重新生成段落");
        // 导出
        exact("exportReport", EXPORT, "导出报告");
        exact("exportCommentsExcel", EXPORT, "导出意见单");
        exact("exportAudit", EXPORT, "导出审计日志");
        // 权限
        exact("requestAddUser", PERMISSION, "新增用户申请");
        exact("setUserEnabled", PERMISSION, "停用/启用账号");
        exact("reviewAddUser", PERMISSION, "复核新增用户");
        exact("accessDenied", PERMISSION, "越权访问被拒绝");
        // 发布
        exact("urgeSign", PUBLISH, "催办签收");
        exact("urgeSignAll", PUBLISH, "一键催办");
        exact("startCorrection", PUBLISH, "发起更正");
        exact("startWithdraw", PUBLISH, "发起撤回");
        exact("sendReminder", PUBLISH, "发送提醒函");
        exact("pushComments", PUBLISH, "推送批注");
        exact("addToPackage", PUBLISH, "加入发布包");
        exact("generateMonthlyReport", PUBLISH, "生成月度报告");
        exact("notifyContact", PUBLISH, "通知对接人");
        // 查阅
        exact("markRead", VIEW, "标记已读");
        exact("viewPage", VIEW, "查阅页面");
        exact("completeCourse", VIEW, "完成培训课程");
        exact("startCourse", VIEW, "开始学习课程");
        exact("submitQuiz", VIEW, "提交课程测验");

        PREFIX.put("login", LOGIN);
        PREFIX.put("logout", LOGIN);
        PREFIX.put("auth", LOGIN);
        PREFIX.put("export", EXPORT);
        PREFIX.put("download", EXPORT);
        PREFIX.put("print", EXPORT);
        PREFIX.put("delete", DELETE);
        PREFIX.put("remove", DELETE);
        PREFIX.put("purge", DELETE);
        PREFIX.put("grant", PERMISSION);
        PREFIX.put("revoke", PERMISSION);
        PREFIX.put("assignRole", PERMISSION);
        PREFIX.put("setRole", PERMISSION);
        PREFIX.put("setScope", PERMISSION);
        PREFIX.put("approve", APPROVE);
        PREFIX.put("reject", APPROVE);
        PREFIX.put("sign", APPROVE);
        PREFIX.put("review", APPROVE);
        PREFIX.put("publish", PUBLISH);
        PREFIX.put("urge", PUBLISH);
        PREFIX.put("set", CONFIG);
        PREFIX.put("save", CONFIG);
        PREFIX.put("update", CONFIG);
        PREFIX.put("reset", CONFIG);
        PREFIX.put("view", VIEW);
        PREFIX.put("read", VIEW);
        PREFIX.put("open", VIEW);
        PREFIX.put("query", VIEW);
        PREFIX.put("search", VIEW);
    }

    private AuditTypes() {}

    public static String classify(String action) {
        return kind(action).type();
    }

    public static Kind kind(String action) {
        if (action == null) return new Kind(OTHER, "", false);
        Kind k = EXACT.get(action);
        if (k != null) return k;
        for (var e : PREFIX.entrySet()) {
            if (action.startsWith(e.getKey())) return new Kind(e.getValue(), action, false);
        }
        return new Kind(OTHER, action, false);
    }

    public static boolean isType(String type) {
        return VOCABULARY.contains(type);
    }

    /** 22:00–06:00 platform time. */
    public static boolean isOffHours(OffsetDateTime at) {
        int h = at.atZoneSameInstant(ZONE).getHour();
        return h >= OFF_HOURS_FROM || h < OFF_HOURS_TO;
    }

    /** Off-hours access that warrants the A14 anomaly banner: reading / exporting / deleting data at night. */
    public static boolean isRisk(String type, OffsetDateTime at) {
        return isOffHours(at) && (VIEW.equals(type) || EXPORT.equals(type) || DELETE.equals(type));
    }

    /** SQL expression equivalent of {@link #isOffHours} over a timestamptz column. */
    static String sqlOffHours(String column) {
        return "(extract(hour from " + column + " at time zone '" + ZONE.getId() + "') >= " + OFF_HOURS_FROM
                + " or extract(hour from " + column + " at time zone '" + ZONE.getId() + "') < " + OFF_HOURS_TO + ")";
    }

    /** SQL {@code CASE} that yields the same type as {@link #classify} for the given action column. */
    static String sqlCase(String column) {
        StringBuilder sb = new StringBuilder("(case");
        // exact names are disjoint, so grouping them by type keeps the expression short without changing the result
        Map<String, java.util.StringJoiner> byType = new LinkedHashMap<>();
        EXACT.forEach((action, k) -> {
            requireName(action);
            byType.computeIfAbsent(k.type(), t -> new java.util.StringJoiner(",")).add("'" + action + "'");
        });
        byType.forEach((type, names) -> sb.append(" when ").append(column).append(" in (").append(names)
                .append(") then '").append(type).append('\''));
        PREFIX.forEach((prefix, type) -> {
            requireName(prefix);
            sb.append(" when ").append(column).append(" like '").append(prefix).append("%' then '").append(type).append('\'');
        });
        return sb.append(" else '").append(OTHER).append("' end)").toString();
    }

    private static void requireName(String s) {
        if (!NAME.matcher(s).matches()) throw new IllegalStateException("bad action name in table: " + s);
    }
}
