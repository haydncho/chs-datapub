package cn.ybdata.core.action;

import java.util.Map;

/** Demo identity per page (mirrors web/src/app/nav.ts VIEWERS) for unauthenticated dev-mode calls (yb.auth.dev-header=true). */
final class Actors {
    private static final Map<String, String> BY_PAGE = Map.ofEntries(
            Map.entry("A7", "张悦"), Map.entry("A8", "陈志远"), Map.entry("A10", "王倩"),
            Map.entry("A12", "陈志远"), Map.entry("A13", "陈志远"), Map.entry("A15", "陈志远"),
            Map.entry("A14", "赵安"), Map.entry("C3", "周敏"), Map.entry("cockpit", "陈志远"));

    private Actors() {}

    static String defaultFor(String page) {
        if (page.startsWith("B") || page.equals("D1")) return "李敏";
        return BY_PAGE.getOrDefault(page, "李华");
    }
}
