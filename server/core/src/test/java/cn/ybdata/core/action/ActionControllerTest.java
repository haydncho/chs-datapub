package cn.ybdata.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionControllerTest {

    private static ActionHandler handler(String page, String action) {
        return new ActionHandler() {
            public String page() { return page; }
            public String action() { return action; }
            public Object handle(String actor, JsonNode payload) { return null; }
        };
    }

    private final ActionController c = new ActionController(null, new ObjectMapper(), List.of(handler("A8", "approvePublish")));

    private JsonNode parse(String s) {
        return c.parse(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void onlyRegisteredOrRecordOnlyActionsAreKnown() {
        assertThat(c.known("A8", "approvePublish")).isTrue();
        assertThat(c.known("A8", "urgeSign")).isTrue();
        assertThat(c.known("A8", "qaTmpBogusAction")).isFalse();
        assertThat(c.known("C3", "deleteAllRecords")).isFalse();
        assertThat(c.known("A1", "login")).isFalse(); // login events are written by the auth service only
        assertThat(c.known("A14", "exportAudit")).isFalse(); // written by the export endpoint only
    }

    @Test
    void payloadMustBeASmallObject() throws Exception {
        assertThat(parse("").isObject()).isTrue();
        assertThat(parse("null").isObject()).isTrue();
        for (String bad : new String[] {"[1]", "\"x\"", "1", "{", "{} {}"}) {
            assertThatThrownBy(() -> parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        byte[] big = new byte[ActionController.MAX_BODY + 1];
        assertThat(ActionController.readLimited(new ByteArrayInputStream(big))).isNull();
        assertThat(ActionController.readLimited(new ByteArrayInputStream(new byte[ActionController.MAX_BODY]))).hasSize(ActionController.MAX_BODY);
    }

    @Test
    void fieldRules() {
        ActionController.validate(parse("{\"toStep\":3,\"x\":[1,{\"y\":\"z\"}],\"step\":2.0}"), 0);
        for (String bad : new String[] {
                "{\"toStep\":2.9}", "{\"a\":\"x\\u0000y\"}", "{\"a\":\"" + "x".repeat(ActionController.MAX_TEXT + 1) + "\"}",
                "{\"a\":" + "[".repeat(20) + "]".repeat(20) + "}"}) {
            assertThatThrownBy(() -> ActionController.validate(parse(bad), 0)).as(bad.length() > 60 ? bad.substring(0, 60) : bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
