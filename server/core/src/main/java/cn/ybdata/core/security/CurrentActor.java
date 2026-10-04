package cn.ybdata.core.security;

import jakarta.servlet.http.HttpServletRequest;

/** Access to the actor {@link AuthFilter} attached to the current request. */
public final class CurrentActor {

    private CurrentActor() {}

    /** @return the resolved actor, or null outside the filter (e.g. public endpoints) */
    public static Actor get(HttpServletRequest req) {
        Object a = req.getAttribute(Actor.REQUEST_ATTR);
        return a instanceof Actor actor ? actor : null;
    }

    /** "ip · browser" as stored in audit_event.terminal (max 64 chars). */
    public static String terminal(HttpServletRequest req) {
        String t = req.getRemoteAddr() + " · " + cn.ybdata.core.audit.AuditService.browser(req.getHeader("User-Agent"));
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
