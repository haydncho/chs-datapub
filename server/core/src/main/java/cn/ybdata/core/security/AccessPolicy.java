package cn.ybdata.core.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 分级可见 — which identity (role) may open which screen and call which API.
 *
 * <pre>
 * role       pages
 * convener   all screens (cockpit, A*, B*, C3, D1)
 * admin      cockpit, every A page
 * analyst    A1, A6, A7                (受控分析环境)
 * hospital   A1, cockpit, B1–B7, D1          (本院具名; payloads are scope-filtered)
 * county     A1, cockpit, A11, C3            (本县具名)
 * auditor    A1, A14 — read-only (no actions)
 * observer   A1, C3                          (公开层)
 * </pre>
 *
 * Other API areas: {@code /audit/**} convener · admin · auditor; {@code /analytics/**} follows the screen that
 * renders it ({@link #ANALYTICS_PAGES}: alerts → A11, topics / drg → A6 · A7, health → everyone);
 * {@code /settings/**} readable by everyone, writable by convener · admin; {@code /auth/**} and
 * {@code /pages} (code list) by everyone authenticated. 召集人审批 actions (A8 approve/reject) are convener-only.
 */
public final class AccessPolicy {

    public static final List<String> ALL_PAGES = Stream.of(
            Stream.of("cockpit", "A1"),
            IntStream.rangeClosed(3, 15).mapToObj(i -> "A" + i),
            IntStream.rangeClosed(1, 7).mapToObj(i -> "B" + i),
            Stream.of("C3", "D1")).flatMap(s -> s).toList();

    private static final Set<String> A_PAGES = ALL_PAGES.stream().filter(p -> p.startsWith("A")).collect(Collectors.toSet());
    private static final Set<String> B_PAGES = ALL_PAGES.stream().filter(p -> p.startsWith("B")).collect(Collectors.toSet());

    private static final Map<String, Set<String>> PAGES = Map.of(
            "convener", Set.copyOf(ALL_PAGES),
            "admin", union(A_PAGES, Set.of("cockpit")),
            "analyst", Set.of("A1", "A6", "A7"),
            "hospital", union(B_PAGES, Set.of("A1", "cockpit", "D1")),
            "county", Set.of("A1", "cockpit", "A11", "C3"),
            "auditor", Set.of("A1", "A14"),
            "observer", Set.of("A1", "C3"));

    /** identities that may look but never change anything */
    private static final Set<String> READ_ONLY = Set.of("auditor");

    /** page/action pairs reserved for specific roles */
    private static final Map<String, Set<String>> ACTION_ROLES = Map.of(
            "A8/approvePublish", Set.of("convener"),
            "A8/rejectPublish", Set.of("convener"),
            // 用户与权限(A12):停用 / 新增申请仅召集人;复核可由召集人或行政管理组完成(申请人不能复核自己,见 UserDomain)
            "A12/setUserEnabled", Set.of("convener"),
            "A12/requestAddUser", Set.of("convener"),
            "A12/reviewAddUser", Set.of("convener", "admin"));

    private static final Set<String> AUDIT_ROLES = Set.of("convener", "admin", "auditor");
    /**
     * analytics endpoint (first path segment below {@code analytics/}) → the screens that use it; a role may
     * call it when it may open one of them. {@code health} is open to every authenticated caller.
     */
    static final Map<String, List<String>> ANALYTICS_PAGES = Map.of(
            "alerts", List.of("A11"),
            "topics", List.of("A6", "A7"),
            "drg", List.of("A6", "A7"));
    /** audit action name for calls refused by the access matrix (A14 type 权限) */
    public static final String DENIED_ACTION = "accessDenied";
    private static final Set<String> SETTINGS_WRITERS = Set.of("convener", "admin");

    private AccessPolicy() {}

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> s = new LinkedHashSet<>(a);
        s.addAll(b);
        return Set.copyOf(s);
    }

    /** Screens the role may open, in navigation order. */
    public static List<String> pagesFor(String role) {
        Set<String> s = PAGES.getOrDefault(role, Set.of());
        return ALL_PAGES.stream().filter(s::contains).toList();
    }

    public static boolean canView(String role, String page) {
        return PAGES.getOrDefault(role, Set.of()).contains(page);
    }

    public static boolean isReadOnly(String role) {
        return READ_ONLY.contains(role);
    }

    public static boolean canAct(String role, String page, String action) {
        if (!canView(role, page) || isReadOnly(role)) return false;
        Set<String> only = ACTION_ROLES.get(page + "/" + action);
        return only == null || only.contains(role);
    }

    static boolean canUseAnalytics(String role, String endpoint) {
        if ("health".equals(endpoint)) return PAGES.containsKey(role);
        return ANALYTICS_PAGES.getOrDefault(endpoint, List.of()).stream().anyMatch(page -> canView(role, page));
    }

    /**
     * The screen a request path belongs to, for auditing refusals ({@code pages/B3} → B3, {@code actions/A8/x} → A8,
     * {@code audit/**} → A14, {@code settings/appearance} → A15, {@code analytics/alerts/…} → A11); null when none.
     */
    public static String pageOf(String path) {
        String[] p = path.split("/");
        if (p.length < 2) return null;
        return switch (p[0]) {
            case "pages", "actions" -> p[1];
            case "audit" -> "A14";
            case "settings" -> "appearance".equals(p[1]) ? "A15" : "A13";
            case "analytics" -> ANALYTICS_PAGES.getOrDefault(p[1], List.of("A6")).get(0);
            default -> null;
        };
    }

    /**
     * @param path request path below {@code /api/v1/} (e.g. {@code pages/B3}, {@code actions/A8/approvePublish})
     * @return empty when allowed, otherwise the reason (served as a 403)
     */
    public static Optional<String> check(String role, String method, String path) {
        String[] p = path.split("/");
        String area = p.length > 0 ? p[0] : "";
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        return switch (area) {
            case "pages" -> p.length < 2 || canView(role, p[1])
                    ? Optional.empty() : Optional.of("当前身份无权访问页面 " + p[1]);
            case "actions" -> {
                if (p.length < 3) yield Optional.empty(); // malformed; the controller answers 4xx
                if (canAct(role, p[1], p[2])) yield Optional.empty();
                yield Optional.of(isReadOnly(role) ? "当前身份为只读,不能执行操作"
                        : canView(role, p[1]) ? "当前身份无权执行 " + p[1] + "/" + p[2]
                        : "当前身份无权访问页面 " + p[1]);
            }
            case "audit" -> AUDIT_ROLES.contains(role) && read ? Optional.empty() : Optional.of("当前身份无权查阅审计日志");
            case "analytics" -> canUseAnalytics(role, p.length > 1 ? p[1] : "") ? Optional.empty() : Optional.of("当前身份无权使用分析服务");
            case "settings" -> read || SETTINGS_WRITERS.contains(role) ? Optional.empty() : Optional.of("当前身份无权修改设置");
            default -> Optional.empty();
        };
    }
}
