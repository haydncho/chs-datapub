package cn.ybdata.core.page;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Patches live domain state into a page payload before it is served. */
public interface PageOverlay {
    String page();

    void apply(ObjectNode payload);
}
