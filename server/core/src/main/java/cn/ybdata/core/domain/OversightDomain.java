package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 外部监督 (C3): a 监督建议 is stored as a feedback item ({@code source = 'c3'}, kind 监督建议), so it is
 * handled in A10 like any 意见 and its reply flows back to the submitter's C3 view.
 */
@Configuration
public class OversightDomain {

    static final List<String> TOPICS = List.of("公开内容", "发布时效", "反馈闭环", "数据口径", "其他");
    static final int MIN_TEXT = 10;
    static final int MAX_TEXT = 500;

    private final JdbcClient jdbc;
    private final FeedbackDomain feedback;

    public OversightDomain(JdbcClient jdbc, FeedbackDomain feedback) {
        this.jdbc = jdbc;
        this.feedback = feedback;
    }

    @Bean
    ActionHandler c3Suggest() {
        return handler("C3", "submitSuggestion", (actor, p) -> {
            Caller who = Caller.resolve(jdbc, actor);
            String topic = FeedbackDomain.required(p, "topic", "建议类别", 16);
            if (!TOPICS.contains(topic)) throw new IllegalArgumentException("建议类别不正确:" + topic);
            String text = FeedbackDomain.required(p, "text", "建议内容", MAX_TEXT);
            if (text.length() < MIN_TEXT) throw new IllegalArgumentException("建议内容至少 " + MIN_TEXT + " 字");
            // the same open suggestion from the same person is accepted once (double click / resubmit)
            long dup = jdbc.sql("""
                    select count(*) from feedback_item
                    where source = 'c3' and submitted_by = :by and body = :b and status <> 'done'""")
                    .param("by", actor).param("b", text).query(Long.class).single();
            if (dup > 0) throw new IllegalArgumentException("相同内容的建议已提交,正在办理中");
            long open = jdbc.sql("select count(*) from feedback_item where source = 'c3' and submitted_by = :by and status <> 'done'")
                    .param("by", actor).query(Long.class).single();
            if (open >= 20) throw new IllegalArgumentException("待答复的建议已达 20 条,请等待答复后再提交");
            String id = feedback.nextId();
            String from = "county".equals(who.role()) && who.orgName() != null ? who.orgName() : "社会监督员 · " + actor;
            String head = text.length() > 24 ? text.substring(0, 24) + "…" : text;
            int n = jdbc.sql("""
                    insert into feedback_item (id, kind, org_id, org_name, title, location, report, status, body, source, submitted_by)
                    values (:id, '监督建议', :org, :from, :title, '外部监督视图', :topic, 'todo', :body, 'c3', :by)
                    on conflict (submitted_by, md5(body)) where source = 'c3' and status <> 'done' do nothing""")
                    .param("id", id).param("org", who.orgId()).param("from", FeedbackDomain.truncate(from, 64))
                    .param("title", FeedbackDomain.truncate(topic + " · " + head, 128)).param("topic", "监督建议 · " + topic)
                    .param("body", text).param("by", actor).update();
            if (n == 0) throw new IllegalArgumentException("相同内容的建议已提交,正在办理中");
            return Map.of("id", id);
        });
    }

    /** C3: the viewer's own suggestions with status and reply. */
    @Bean
    PageOverlay c3Overlay() {
        return overlay("C3", payload -> {
            ArrayNode topics = payload.putArray("suggestionTopics");
            TOPICS.forEach(topics::add);
            ArrayNode mine = payload.putArray("mySuggestions");
            Caller who = Caller.viewer(jdbc, "C3");
            if (who == null || who.name() == null) return;
            LocalDate today = Sla.today();
            for (FeedbackDomain.Row r : feedback.rows(null, who.name(), "c3")) {
                mine.add(feedback.itemJson(payload.objectNode(), r, today));
            }
        });
    }
}
