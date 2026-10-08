package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageScopeFilter;
import cn.ybdata.core.page.PageService;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * B6 政策培训: learning state per user (table training_progress, V12).
 * <ul>
 *   <li>{@code B6/startCourse {id}} — opens a course (状态 学习中);</li>
 *   <li>{@code B6/submitQuiz {id, answers: int[]}} — grades the 测验 against the answer key in the stored
 *       B6 payload; a score ≥ {@link #PASS} completes the course. Returns {score, passed, correct, total}.</li>
 * </ul>
 * The B6 payload served to a viewer carries that viewer's own state ({@code started}, {@code done},
 * {@code score}), never the answer key, and the subtitle counts the viewer's 本院 colleagues who finished
 * every 必修 course.
 */
@Configuration
public class TrainingDomain {

    static final int PASS = 60;

    private final JdbcClient jdbc;
    private final PageService pages;

    public TrainingDomain(JdbcClient jdbc, PageService pages) {
        this.jdbc = jdbc;
        this.pages = pages;
    }

    record Viewer(String key, String org) {}

    /** user key + organisation of the identity making the current request */
    static Viewer viewer(String actorName) {
        Actor a = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            a = CurrentActor.get(sra.getRequest());
        }
        return viewer(a, actorName);
    }

    static Viewer viewer(Actor a, String fallbackName) {
        String key = a != null && a.login() != null ? a.login() : a != null && a.name() != null ? a.name() : fallbackName;
        return new Viewer(key, a == null ? null : a.orgId());
    }

    private JsonNode course(String id) {
        for (JsonNode c : pages.get("B6").path("courses")) {
            if (id.equals(c.path("id").asText())) return c;
        }
        throw new IllegalArgumentException("未知课程: " + id);
    }

    @Bean
    ActionHandler b6StartCourse() {
        return handler("B6", "startCourse", (actor, p) -> {
            String id = Json.text(p, "id");
            course(id);
            Viewer v = viewer(actor);
            jdbc.sql("""
                    insert into training_progress (user_key, org_id, course_id) values (:u, :o, :c)
                    on conflict (user_key, course_id) do nothing""")
                    .param("u", v.key()).param("o", v.org()).param("c", id).update();
            return null;
        });
    }

    @Bean
    ActionHandler b6SubmitQuiz() {
        return handler("B6", "submitQuiz", (actor, p) -> {
            String id = Json.text(p, "id");
            JsonNode quiz = course(id).path("quiz");
            JsonNode answers = p.path("answers");
            if (quiz.isEmpty()) throw new IllegalArgumentException("该课程没有测验");
            if (!answers.isArray() || answers.size() != quiz.size()) {
                throw new IllegalArgumentException("请完成全部 " + quiz.size() + " 道测验题后再提交");
            }
            int correct = 0;
            for (int i = 0; i < quiz.size(); i++) {
                if (answers.get(i).canConvertToInt() && answers.get(i).asInt() == quiz.get(i).path("answer").asInt(-1)) correct++;
            }
            int score = Math.round(correct * 100f / quiz.size());
            boolean passed = score >= PASS;
            Viewer v = viewer(actor);
            jdbc.sql("""
                    insert into training_progress (user_key, org_id, course_id, attempts, last_score, best_score, completed_at)
                    values (:u, :o, :c, 1, :s, :s, case when :pass then now() end)
                    on conflict (user_key, course_id) do update set
                        attempts = training_progress.attempts + 1,
                        last_score = :s,
                        best_score = greatest(coalesce(training_progress.best_score, 0), :s),
                        org_id = coalesce(excluded.org_id, training_progress.org_id),
                        completed_at = coalesce(training_progress.completed_at, case when :pass then now() end)""")
                    .param("u", v.key()).param("o", v.org()).param("c", id).param("s", score).param("pass", passed)
                    .update();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("score", score);
            out.put("passed", passed);
            out.put("correct", correct);
            out.put("total", quiz.size());
            return out;
        });
    }

    record Progress(boolean done, Integer score) {}

    /** B6 as seen by one viewer: own progress patched in, answer key removed. */
    @Bean
    PageScopeFilter b6Progress() {
        return new PageScopeFilter() {
            @Override
            public boolean appliesTo(String code, Actor actor) {
                return "B6".equals(code);
            }

            @Override
            public void apply(ObjectNode payload, Actor actor) {
                Viewer v = viewer(actor, actor.name());
                Map<String, Progress> mine = new LinkedHashMap<>();
                if (v.key() != null) {
                    jdbc.sql("select course_id, completed_at is not null, best_score from training_progress where user_key = :u")
                            .param("u", v.key())
                            .query((rs, i) -> Map.entry(rs.getString(1), new Progress(rs.getBoolean(2), (Integer) rs.getObject(3))))
                            .list().forEach(e -> mine.put(e.getKey(), e.getValue()));
                }
                int required = 0;
                for (JsonNode c : payload.path("courses")) {
                    if (!(c instanceof ObjectNode o)) continue;
                    if ("必修".equals(o.path("tag").asText())) required++;
                    Progress pr = mine.get(o.path("id").asText());
                    o.put("started", pr != null);
                    o.put("done", pr != null && pr.done());
                    if (pr != null && pr.done() && pr.score() != null) o.put("score", pr.score());
                    else o.putNull("score");
                    for (JsonNode q : o.path("quiz")) if (q instanceof ObjectNode qo) qo.remove("answer");
                }
                if (v.org() != null && required > 0) {
                    int finished = jdbc.sql("""
                            select count(*) from (
                              select user_key from training_progress
                              where org_id = :o and completed_at is not null and course_id in (:ids)
                              group by user_key having count(*) = :n) t""")
                            .param("o", v.org()).param("ids", requiredIds(payload)).param("n", required)
                            .query(Integer.class).single();
                    payload.put("subtitle", "读懂报告 · 理解口径 · 本院 " + finished + " 人已完成全部必修");
                }
            }
        };
    }

    private static java.util.List<String> requiredIds(ObjectNode payload) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonNode c : payload.path("courses")) if ("必修".equals(c.path("tag").asText())) ids.add(c.path("id").asText());
        return ids;
    }
}
