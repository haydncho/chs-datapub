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

    private Roles() {}

    /** 页面编号 → 可访问角色。 */
    public static final Map<String, Set<String>> PAGES;

    static {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("A3", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A5", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A7", Set.of(CONVENER, ADMIN_GROUP, ANALYST, EXPERT));
        m.put("A9", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A10", Set.of(CONVENER, ADMIN_GROUP, HANDLER));
        m.put("A11", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A12", Set.of(SECURITY_ADMIN));
        m.put("A13", Set.of(CONVENER, ADMIN_GROUP));
        m.put("A14", Set.of(AUDITOR));
        PAGES = Map.copyOf(m);
    }

    public static List<String> pagesOf(String role) {
        return PAGES.entrySet().stream().filter(e -> e.getValue().contains(role)).map(Map.Entry::getKey)
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a.substring(1)), Integer.parseInt(b.substring(1))))
                .toList();
    }

    public static String[] of(String page) {
        return PAGES.get(page).toArray(String[]::new);
    }
}
