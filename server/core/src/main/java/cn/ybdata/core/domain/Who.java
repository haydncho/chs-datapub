package cn.ybdata.core.domain;

import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import java.util.Set;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The identity behind the current action call, for role checks inside {@link cn.ybdata.core.action.ActionHandler}s
 * (the handler signature only carries the display name). Legacy unauthenticated demo calls
 * ({@link Actor.Source#DEV_DEFAULT}) have no role and therefore pass none of these checks.
 */
final class Who {
    private Who() {}

    /** @return the resolved actor of the current request, or null outside a request */
    static Actor current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            return CurrentActor.get(a.getRequest());
        }
        return null;
    }

    static boolean hasRole(Actor a, Set<String> roles) {
        return a != null && a.enforced() && a.role() != null && roles.contains(a.role());
    }

    /** @throws AccessDeniedException (403) unless the caller holds one of {@code roles} */
    static Actor require(String why, Set<String> roles) {
        Actor a = current();
        if (!hasRole(a, roles)) throw new AccessDeniedException(why);
        return a;
    }

    /** the 定点医疗机构 identity (role hospital, with an organisation) of the caller — nobody acts on an institution's behalf */
    static Actor requireHospital(String why) {
        Actor a = require(why, Set.of("hospital"));
        if (a.orgId() == null || a.orgId().isBlank()) throw new AccessDeniedException(why);
        return a;
    }

    /** audit / log name of an actor (falls back to the handler's actor string) */
    static String name(Actor a, String fallback) {
        return a != null && a.name() != null ? a.name() : fallback;
    }
}
