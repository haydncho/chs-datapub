package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;

import cn.ybdata.core.action.ActionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Platform settings: 外观配置 (A15) and 展示策略 rules (A13). */
@Configuration
@RestController
public class SettingDomain {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public SettingDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void put(String key, JsonNode value, String actor) {
        jdbc.sql("""
                insert into setting (key, value, updated_by) values (:k, cast(:v as jsonb), :by)
                on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by, updated_at = now()""")
                .param("k", key).param("v", value.toString()).param("by", actor).update();
    }

    Optional<JsonNode> get(String key) {
        return jdbc.sql("select value::text from setting where key = :k").param("k", key)
                .query(String.class).optional().map(s -> {
                    try {
                        return json.readTree(s);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    @GetMapping("/api/v1/settings/{key}")
    public JsonNode read(@PathVariable String key) {
        if (!key.matches("appearance|display_policy")) throw new IllegalArgumentException("unknown setting");
        return get(key).orElse(json.createObjectNode());
    }

    @Bean
    ActionHandler a15Publish() {
        return handler("A15", "publishAppearance", (actor, p) -> {
            put("appearance", p, actor);
            return null;
        });
    }

    @Bean
    ActionHandler a15Reset() {
        return handler("A15", "resetAppearance", (actor, p) -> {
            jdbc.sql("delete from setting where key = 'appearance'").update();
            return null;
        });
    }

    @Bean
    ActionHandler a13SetRule() {
        return handler("A13", "setPolicyRule", (actor, p) -> {
            String k = Json.text(p, "key");
            if (!k.matches("minOrg|minCase|wm|exp|named")) throw new IllegalArgumentException("unknown rule " + k);
            ObjectNode cur = (ObjectNode) get("display_policy").orElse(json.createObjectNode());
            cur.set(k, p.get("value"));
            put("display_policy", cur, actor);
            return cur;
        });
    }
}
