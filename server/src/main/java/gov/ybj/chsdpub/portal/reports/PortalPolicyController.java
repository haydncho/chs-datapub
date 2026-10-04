package gov.ybj.chsdpub.portal.reports;

import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Array;
import java.time.LocalDate;
import java.util.*;

/**
 * B6 政策指南与培训：文件检索（分组方案 / 基准点数 / 特例单议）、只读查看（写审计）、课件进度、随堂单选题。
 * 试题正确答案只在服务端；作答后由分析引擎判分，才返回正确选项。
 */
@RestController
@RequestMapping("/api/v1/portal/policy")
public class PortalPolicyController {

    public static final List<String> CATEGORIES = List.of("分组方案", "基准点数", "特例单议");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final EngineClient engine;

    public PortalPolicyController(JdbcTemplate jdbc, AuditService audit, EngineClient engine) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.engine = engine;
    }

    public record Doc(long id, String category, String title, String docNo, LocalDate issuedOn, String format) {}

    @GetMapping("/docs")
    public Map<String, Object> docs(@RequestParam(required = false) String category, @RequestParam(required = false) String q) {
        StringBuilder sql = new StringBuilder("select id, category, title, doc_no, issued_on, format from pr_policy_doc where true");
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank() && !"全部".equals(category)) {
            if (!CATEGORIES.contains(category)) throw ApiException.validation("文件类别不正确");
            sql.append(" and category = ?");
            args.add(category);
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" and (title ilike ? or doc_no ilike ?)");
            args.add(like);
            args.add(like);
        }
        sql.append(" order by sort");
        List<Doc> rows = jdbc.query(sql.toString(), (rs, i) -> new Doc(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, LocalDate.class), rs.getString(6)), args.toArray());
        return Map.of("categories", CATEGORIES, "rows", rows);
    }

    /** 只读查看器打开文件（带水印）：写审计「查阅」。 */
    @PostMapping("/docs/{id}/view")
    public Map<String, Object> view(@PathVariable long id) {
        AuthUser u = CurrentUser.get();
        List<String> t = jdbc.queryForList("select title from pr_policy_doc where id = ?", String.class, id);
        if (t.isEmpty()) throw ApiException.notFound("文件不存在");
        audit.record(u, AuditService.VIEW, "政策文件《" + t.get(0) + "》· 只读查看器", "成功");
        return Map.of("message", "已在只读查看器中打开(带水印)");
    }

    public record Course(long id, String title, int minutes, int pct) {}

    @GetMapping("/courses")
    public List<Course> courses() {
        long uid = CurrentUser.get().userId();
        return jdbc.query("""
                select c.id, c.title, c.minutes, coalesce(p.pct, 0) from pr_course c
                left join pr_course_progress p on p.course_id = c.id and p.user_id = ? order by c.sort""",
                (rs, i) -> new Course(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4)), uid);
    }

    public record Graded(int picked, boolean correct, int correctIndex, String result, String explain) {}

    /** 题面不含正确答案；已作答时附带判分结果。 */
    public record Quiz(long id, String courseTitle, String question, List<String> options, Graded answered) {}

    private record QuizRow(long id, String courseTitle, String question, List<String> options, int answer, String explain) {}

    private QuizRow quizRow(Long id) {
        String where = id == null ? "" : " where q.id = ?";
        Object[] args = id == null ? new Object[0] : new Object[]{id};
        return jdbc.query("select q.id, c.title, q.question, q.options, q.answer, q.explain from pr_quiz q join pr_course c on c.id = q.course_id"
                + where + " order by q.id limit 1", (rs, i) -> {
            Array arr = rs.getArray(4);
            List<String> opts = List.of((String[]) arr.getArray());
            return new QuizRow(rs.getLong(1), rs.getString(2), rs.getString(3), opts, rs.getInt(5), rs.getString(6));
        }, args).stream().findFirst().orElse(null);
    }

    private static String letterResult(boolean ok, int answer) {
        return ok ? "回答正确" : "回答错误,正确答案为 " + (char) ('A' + answer);
    }

    @GetMapping("/quiz")
    public Map<String, Object> quiz() {
        long uid = CurrentUser.get().userId();
        QuizRow q = quizRow(null);
        Map<String, Object> m = new HashMap<>();
        if (q == null) {
            m.put("quiz", null);
            return m;
        }
        Graded g = jdbc.query("select picked, correct from pr_quiz_answer where user_id = ? and quiz_id = ?",
                (rs, i) -> new Graded(rs.getInt(1), rs.getBoolean(2), q.answer(), letterResult(rs.getBoolean(2), q.answer()), q.explain()),
                uid, q.id()).stream().findFirst().orElse(null);
        m.put("quiz", new Quiz(q.id(), q.courseTitle(), q.question(), q.options(), g));
        return m;
    }

    public record AnswerReq(Integer picked) {}

    @PostMapping("/quiz/{id}/answer")
    @Transactional
    public Graded answer(@PathVariable long id, @RequestBody(required = false) AnswerReq req) {
        long uid = CurrentUser.get().userId();
        QuizRow q = quizRow(id);
        if (q == null) throw ApiException.notFound("试题不存在");
        if (req == null || req.picked() == null || req.picked() < 0 || req.picked() >= q.options().size())
            throw ApiException.validation("请选择一个选项");
        Integer done = jdbc.query("select picked from pr_quiz_answer where user_id = ? and quiz_id = ?", (rs, i) -> rs.getInt(1), uid, id)
                .stream().findFirst().orElse(null);
        if (done != null) throw ApiException.conflict("本题已作答");
        Map<String, Object> r = engine.post("/v1/portal-reports/quiz/grade", Map.of("items", List.of(Map.of(
                "quizId", q.id(), "optionCount", q.options().size(), "picked", req.picked(), "answer", q.answer()))));
        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) r.get("items")).get(0);
        boolean correct = Boolean.TRUE.equals(item.get("correct"));
        int correctIndex = ((Number) item.get("correctIndex")).intValue();
        try {
            jdbc.update("insert into pr_quiz_answer (user_id, quiz_id, picked, correct) values (?, ?, ?, ?)", uid, id, req.picked(), correct);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("本题已作答");
        }
        return new Graded(req.picked(), correct, correctIndex, (String) item.get("result"), q.explain());
    }
}
