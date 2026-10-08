package cn.ybdata.core.domain;

import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Operation state of the A3–A7 workbenches: one JSON value per (page, key) in {@code page_state}
 * (V13). Handlers write it, the page overlays merge it into the served payload.
 * Also reads the raw seeded page payload (without overlays) for validation.
 */
@Component
public class PageState {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public PageState(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<ObjectNode> get(String page, String key) {
        return jdbc.sql("select value::text from page_state where page = :p and key = :k")
                .param("p", page).param("k", key).query(String.class).optional().map(this::object);
    }

    /** current value, or a fresh empty object */
    public ObjectNode getOrNew(String page, String key) {
        return get(page, key).orElseGet(json::createObjectNode);
    }

    public void put(String page, String key, JsonNode value, String actor) {
        jdbc.sql("""
                insert into page_state (page, key, value, updated_by) values (:p, :k, cast(:v as jsonb), :by)
                on conflict (page, key) do update set value = excluded.value, updated_by = excluded.updated_by, updated_at = now()""")
                .param("p", page).param("k", key).param("v", value.toString())
                .param("by", actor == null ? "system" : actor.length() > 32 ? actor.substring(0, 32) : actor).update();
    }

    /** all keys of a page starting with {@code prefix}, in key order */
    public Map<String, ObjectNode> all(String page, String prefix) {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        jdbc.sql("select key, value::text from page_state where page = :p and key like :k order by key")
                .param("p", page).param("k", prefix.replace("%", "\\%") + "%")
                .query((rs, i) -> Map.entry(rs.getString(1), rs.getString(2))).list()
                .forEach(e -> out.put(e.getKey().substring(prefix.length()), object(e.getValue())));
        return out;
    }

    public void clear(String page) {
        jdbc.sql("delete from page_state where page = :p").param("p", page).update();
    }

    /** the seeded payload of a page (no overlays), or an empty object */
    public ObjectNode seed(String page) {
        return jdbc.sql("select payload::text from page_payload where code = :c").param("c", page)
                .query(String.class).optional().map(this::object).orElseGet(json::createObjectNode);
    }

    public ObjectMapper json() {
        return json;
    }

    private ObjectNode object(String raw) {
        try {
            JsonNode n = json.readTree(raw);
            return n instanceof ObjectNode o ? o : json.createObjectNode();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── actor helpers (role checks live in the handlers, see FIX-COMMON) ──

    /** the actor of the current request, or null outside a request / in legacy dev mode */
    static Actor currentActor() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            Actor who = CurrentActor.get(a.getRequest());
            return who != null && who.enforced() ? who : null;
        }
        return null;
    }

    static String currentRole() {
        Actor who = currentActor();
        return who == null ? null : who.role();
    }

    /** refuse unless the (enforced) actor has one of {@code roles}; unauthenticated dev calls pass */
    static void requireRole(Set<String> roles, String message) {
        String role = currentRole();
        if (role != null && !roles.contains(role)) throw new AccessDeniedException(message);
    }
}
