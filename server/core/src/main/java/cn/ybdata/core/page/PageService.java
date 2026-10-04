package cn.ybdata.core.page;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read model for the screens. The stored payload has exactly the shape of the
 * frontend seed (web/src/mock/{code}.ts); {@link PageOverlay}s then patch in
 * live state from the domain tables (e.g. which reports are signed).
 */
@Service
public class PageService {

    public static final Pattern CODE = Pattern.compile("^(cockpit|[ABCD]\\d{1,2})$");

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final List<PageOverlay> overlays;

    public PageService(JdbcClient jdbc, ObjectMapper json, List<PageOverlay> overlays) {
        this.jdbc = jdbc;
        this.json = json;
        this.overlays = overlays;
    }

    public ObjectNode get(String code) {
        requireCode(code);
        String raw = jdbc.sql("select payload::text from page_payload where code = :code")
                .param("code", code).query(String.class).optional()
                .orElseThrow(() -> new NoSuchElementException("no payload for page " + code));
        ObjectNode node;
        try {
            node = (ObjectNode) json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        for (PageOverlay o : overlays) {
            if (o.page().equals(code)) o.apply(node);
        }
        return node;
    }

    public void put(String code, String payloadJson, boolean overwrite) {
        requireCode(code);
        jdbc.sql(overwrite
                        ? "insert into page_payload (code, payload) values (:c, cast(:p as jsonb)) on conflict (code) do update set payload = excluded.payload, updated_at = now()"
                        : "insert into page_payload (code, payload) values (:c, cast(:p as jsonb)) on conflict (code) do nothing")
                .param("c", code).param("p", payloadJson).update();
    }

    public List<String> codes() {
        return jdbc.sql("select code from page_payload order by code").query(String.class).list();
    }

    static void requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) throw new IllegalArgumentException("bad page code: " + code);
    }
}
