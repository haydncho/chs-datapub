package cn.ybdata.core.security;

/**
 * Who is making an API call, resolved by {@link AuthFilter} for every {@code /api/v1/**} request.
 *
 * @param name       display name used as the audit actor (陈志远)
 * @param userId     app_user.id (null for the legacy dev fallback)
 * @param role       app_role.code of the active identity (convener / hospital …)
 * @param orgId      org of the active identity (H001 …)
 * @param identityId user_identity.id of the active identity
 * @param sessionId  auth_session.id when authenticated with a bearer token
 */
public record Actor(String name, Long userId, String login, String role, String orgId, String orgName,
                    Long identityId, String sessionId, Source source) {

    public enum Source {
        /** bearer token from the unified login */
        SESSION,
        /** tests only: X-YB-User header naming a known user (yb.auth.dev-header=true); access-checked */
        DEV_HEADER,
        /**
         * Unchecked placeholder kept for unit tests of the scope filters only — {@link AuthFilter} never
         * produces it: requests without credentials get 401.
         */
        DEV_DEFAULT
    }

    public static final String REQUEST_ATTR = Actor.class.getName();

    /** Access matrix and data scope apply to every actor resolved by {@link AuthFilter}. */
    public boolean enforced() {
        return source != Source.DEV_DEFAULT;
    }

    public boolean isHospital() {
        return enforced() && "hospital".equals(role);
    }

    public static Actor devDefault(String name) {
        return new Actor(name, null, null, null, null, null, null, null, Source.DEV_DEFAULT);
    }
}
