package cn.ybdata.core.action;

import com.fasterxml.jackson.databind.JsonNode;

/** Domain side-effect for one page action, e.g. B4 / signReport. */
public interface ActionHandler {
    String page();

    String action();

    /** @return optional result returned to the client (may be null) */
    Object handle(String actor, JsonNode payload);
}
