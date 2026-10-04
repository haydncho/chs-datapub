package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void patchArrayUpdatesMatchingRowsOnly() throws Exception {
        ObjectNode p = (ObjectNode) json.readTree("""
                {"reports":[{"name":"a","status":"sign"},{"name":"b","status":"sign"},{"status":"x"}]}""");
        Json.patchArray(p, "reports", "name", Map.of("b", "signed"), (o, s) -> o.put("status", s));
        assertThat(p.toString()).isEqualTo("{\"reports\":[{\"name\":\"a\",\"status\":\"sign\"},{\"name\":\"b\",\"status\":\"signed\"},{\"status\":\"x\"}]}");
    }

    @Test
    void patchArrayIgnoresMissingArray() {
        ObjectNode p = json.createObjectNode();
        Json.patchArray(p, "reports", "name", Map.of("b", "signed"), (o, s) -> o.put("status", s));
        assertThat(p.isEmpty()).isTrue();
    }

    @Test
    void textRequiresField() {
        assertThatThrownBy(() -> Json.text(json.createObjectNode(), "id")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Json.textOr(json.createObjectNode(), "id", "d")).isEqualTo("d");
    }
}
