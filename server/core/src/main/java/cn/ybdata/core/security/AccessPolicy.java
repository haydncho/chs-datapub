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
 * Other API areas: {@code /audit/**} convener · admin · auditor; {@code /analytics/**} convener · admin ·
 * analyst; {@code /settings/**} readable by everyone, writable by convener · admin; {@code /auth/**} and
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
            "A8/rejectPublish", Set.of("convener"));

    private static final Set<String> AUDIT_ROLES = Set.of("convener", "admin", "auditor");
    private static final Set<String> ANALYTICS_ROLES = Set.of("convener", "admin", "analyst");
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
            case "analytics" -> ANALYTICS_ROLES.contains(role) ? Optional.empty() : Optional.of("当前身份无权使用分析服务");
            case "settings" -> read || SETTINGS_WRITERS.contains(role) ? Optional.empty() : Optional.of("当前身份无权修改设置");
            default -> Optional.empty();
        };
    }
}
