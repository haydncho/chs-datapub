package cn.ybdata.core.domain;

import cn.ybdata.core.security.AccessPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 用户与权限 (A12) 的纯函数:访问矩阵推导、账号状态判定、角色权限。不依赖数据库。 */
public final class UserMatrix {

    /** 页面分组(矩阵的列),与菜单分类同名;机构端功能 = 医院/监督侧页面。 */
    public record Group(String id, String name, List<String> pages) {}

    /** 矩阵单元:level = access(可访问) / readonly(只读) / none(无);pages 为该角色在此分组内可访问的页面。 */
    public record Cell(String group, String level, List<String> pages, int total) {}

    public static final List<Group> GROUPS = List.of(
            new Group("cock", "全景图", List.of("cockpit")),
            new Group("s1", "归集", List.of("A3")),
            new Group("s2", "配置", List.of("A4", "A5", "A13")),
            new Group("s3", "洞察", List.of("A6", "A7")),
            new Group("s4", "发布", List.of("A8", "A9")),
            new Group("s5", "反馈", List.of("A10", "A11")),
            new Group("gov", "设置", List.of("A12", "A14", "A15")),
            new Group("org", "机构端功能", List.of("B1", "B2", "B3", "B4", "B5", "B6", "B7", "C3", "D1")));

    public static final Map<String, String> PAGE_NAMES = Map.ofEntries(
            Map.entry("cockpit", "医保数据公开全景图"), Map.entry("A3", "数据归集中心"), Map.entry("A4", "指标配置"),
            Map.entry("A5", "图表与报告模板"), Map.entry("A6", "智能推荐"), Map.entry("A7", "病种专题工作台"),
            Map.entry("A8", "发布工作流"), Map.entry("A9", "流程设计器"), Map.entry("A10", "意见与申诉"),
            Map.entry("A11", "预警提醒"), Map.entry("A12", "用户权限"), Map.entry("A13", "展示策略"),
            Map.entry("A14", "审计日志"), Map.entry("A15", "外观配置"), Map.entry("B1", "本院全景"),
            Map.entry("B2", "病组下钻"), Map.entry("B3", "对标PK"), Map.entry("B4", "报告中心"),
            Map.entry("B5", "意见核对"), Map.entry("B6", "政策培训"), Map.entry("B7", "区域外患者"),
            Map.entry("C3", "外部监督"), Map.entry("D1", "移动端"));

    /** 角色展示顺序 */
    public static final List<String> ROLE_ORDER =
            List.of("convener", "admin", "analyst", "hospital", "county", "auditor", "observer");

    /** 超过多少天未登录判为"即将停用" */
    public static final int INACTIVE_DAYS = 30;

    private UserMatrix() {}

    /** 医保局端 / 机构端:医保局的四类身份在 bureau,医院、县区、社会监督在 org。 */
    public static String sideOf(String role) {
        return switch (role) {
            case "convener", "admin", "analyst", "auditor" -> "bureau";
            default -> "org";
        };
    }

    /** 某角色在某分组的访问程度(页面清单取自 {@link AccessPolicy#pagesFor})。 */
    public static Cell cell(String role, Group g) {
        List<String> mine = AccessPolicy.pagesFor(role);
        List<String> open = g.pages().stream().filter(mine::contains).toList();
        String level = open.isEmpty() ? "none" : AccessPolicy.isReadOnly(role) ? "readonly" : "access";
        return new Cell(g.id(), level, open, g.pages().size());
    }

    public static List<Cell> row(String role) {
        List<Cell> out = new ArrayList<>();
        for (Group g : GROUPS) out.add(cell(role, g));
        return out;
    }

    /** 能否审批(发布审批为召集人专属,取自 AccessPolicy 的操作权限)。 */
    public static boolean canApprove(String role) {
        return AccessPolicy.canAct(role, "A8", "approvePublish");
    }

    /** A12 操作的角色限制:停用/启用、新增申请仅召集人;复核可由召集人或行政管理组(须非申请人)执行。 */
    public static boolean mayAct(String role, String action) {
        return switch (action) {
            case "setUserEnabled", "requestAddUser" -> "convener".equals(role);
            case "reviewAddUser" -> "convener".equals(role) || "admin".equals(role);
            default -> false;
        };
    }

    /** on 正常 / expiring 即将停用(超过 30 天未登录) / off 已停用。从未登录视为正常。 */
    public static String statusOf(boolean enabled, Instant lastLogin, Instant now) {
        if (!enabled) return "off";
        if (lastLogin == null) return "on";
        return Duration.between(lastLogin, now).toDays() > INACTIVE_DAYS ? "expiring" : "on";
    }

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    /** 今天 09:02 / 昨天 08:47 / 3 天前 / 08-26 / 从未登录 */
    public static String lastLoginText(Instant last, Instant now, ZoneId zone) {
        if (last == null) return "从未登录";
        var t = last.atZone(zone);
        LocalDate today = now.atZone(zone).toLocalDate();
        long days = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), today);
        if (days <= 0) return "今天 " + HM.format(t);
        if (days == 1) return "昨天 " + HM.format(t);
        if (days <= 7) return days + " 天前";
        return MD.format(t);
    }
}
