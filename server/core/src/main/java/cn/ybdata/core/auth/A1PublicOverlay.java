package cn.ybdata.core.auth;

import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * {@code GET /pages/A1} is public (served before login), so it must not name accounts: the login a demo
 * certificate belongs to is read locally by the client's certificate reader, never sent by the server.
 * Older seeds still carry {@code cert.account}; it is dropped here.
 */
@Component
public class A1PublicOverlay implements PageOverlay {

    @Override
    public String page() {
        return "A1";
    }

    @Override
    public void apply(ObjectNode payload) {
        JsonNode cert = payload.get("cert");
        if (cert instanceof ObjectNode c) c.remove("account");
    }
}
