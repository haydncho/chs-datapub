package cn.ybdata.core.page;

import cn.ybdata.core.security.Actor;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Row-level data scope (分级可见): removes or anonymises the parts of a page payload the
 * viewer's identity may not see. Runs after the {@link PageOverlay}s, per request.
 */
public interface PageScopeFilter {

    /** @return true when this filter must run for {@code code} as seen by {@code actor} */
    boolean appliesTo(String code, Actor actor);

    void apply(ObjectNode payload, Actor actor);

    /** Called by the read model; override when one filter serves several pages and needs the code. */
    default void apply(String code, ObjectNode payload, Actor actor) {
        apply(payload, actor);
    }
}
