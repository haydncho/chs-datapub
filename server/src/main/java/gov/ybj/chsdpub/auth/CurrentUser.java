package gov.ybj.chsdpub.auth;

import gov.ybj.chsdpub.common.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static AuthUser get() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) return u;
        throw ApiException.unauthorized("未登录或登录已过期");
    }

    public static AuthUser orNull() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthUser u ? u : null;
    }
}
