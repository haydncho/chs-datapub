package gov.ybj.chsdpub.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与页面可见范围（菜单按角色裁剪，接口按同一张表鉴权，见 SecurityConfig）。
 * 三员分立：安全管理员只管授权、看不到业务数据；审计员只读审计日志。
 */
public final class Roles {

    public static final String CONVENER = "CONVENER";            // 召集人
    public static final String ADMIN_GROUP = "ADMIN_GROUP";      // 行政管理组
    public static final String HANDLER = "HANDLER";              // 意见承办人
    public static final String ANALYST = "ANALYST";              // 委托分析团队（受控环境）
    public static final String EXPERT = "EXPERT";                // 专家组成员（列席，只读）
    public static final String SECURITY_ADMIN = "SECURITY_ADMIN";
    public static final String AUDITOR = "AUDITOR";
    public static final String INSTITUTION = "INSTITUTION";      // 定点医疗机构（机构门户，本院具名 + 同级匿名分位）
    public static final String COUNTY = "COUNTY";                // 县区医保部门（本县区具名，其他县区汇总）
    public static final String PROVINCE = "PROVINCE";            // 省级 / 其他统筹区（仅汇总层，无机构级字段）
    public static final String SUPERVISOR = "SUPERVISOR";        // 外部监督只读席位（限时）

    private Roles() {}

    /** 页面编号 → 可访问角色。 */
    public static final Map<String, Set<String>> PAGES;

    static {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("W0", Set.of(CONVENER, ADMIN_GROUP));
        m.put("W1", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A2", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A3", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A4", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A5", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A6", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A7", Set.of(CONVENER, ADMIN_GROUP, ANALYST, EXPERT));
        m.put("A8", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A9", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A10", Set.of(CONVENER, ADMIN_GROUP, HANDLER));
        m.put("A11", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A12", Set.of(SECURITY_ADMIN));
        m.put("A13", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A14", Set.of(AUDITOR));
        for (String b : List.of("B1", "B2", "B3", "B4", "B5", "B6", "B7", "D1")) m.put(b, Set.of(INSTITUTION));
        m.put("C1", Set.of(COUNTY));
        m.put("C2", Set.of(PROVINCE));
        m.put("C3", Set.of(SUPERVISOR));
        m.put("E1", Set.of(CONVENER, ADMIN_GROUP, ANALYST, AUDITOR, INSTITUTION, COUNTY, PROVINCE));
        PAGES = Map.copyOf(m);
    }

    public static List<String> pagesOf(String role) {
        return PAGES.entrySet().stream().filter(e -> e.getValue().contains(role)).map(Map.Entry::getKey)
                .sorted(java.util.Comparator.comparing((String k) -> k.charAt(0)).thenComparingInt(k -> Integer.parseInt(k.substring(1))))
                .toList();
    }

    public static String[] of(String page) {
        return PAGES.get(page).toArray(String[]::new);
    }
}
