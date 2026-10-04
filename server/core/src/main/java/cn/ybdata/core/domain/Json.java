package cn.ybdata.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.function.BiConsumer;

/** Small helpers for patching page payloads. */
final class Json {
    private Json() {}

    static String text(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) throw new IllegalArgumentException("missing field: " + field);
        return v.asText();
    }

    static String textOr(JsonNode n, String field, String dflt) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() ? dflt : v.asText();
    }

    /** For each object in payload[array] whose [key] is in rows, call patch(object, row). */
    static <T> void patchArray(ObjectNode payload, String array, String key, Map<String, T> rows,
                               BiConsumer<ObjectNode, T> patch) {
        JsonNode arr = payload.get(array);
        if (!(arr instanceof ArrayNode a)) return;
        for (JsonNode el : a) {
            if (el instanceof ObjectNode o && o.hasNonNull(key)) {
                T row = rows.get(o.get(key).asText());
                if (row != null) patch.accept(o, row);
            }
        }
    }
}
