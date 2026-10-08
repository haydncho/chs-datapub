package cn.ybdata.core.auth;

import cn.ybdata.core.audit.AuditService;
import cn.ybdata.core.security.AccessPolicy;
import cn.ybdata.core.security.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 统一身份认证: certificate-PIN and account+SMS login, signed bearer sessions, identity switch, logout.
 * Every login, failure, switch and logout goes to the hash-chained audit trail (page A1).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_SMS_ATTEMPTS = 5;

    /** One identity a user holds (user_identity joined with role and org). */
    public record Identity(long id, String role, String roleName, String dataScope, String orgId, String orgName,
                           String label, String description, String zone, String tone, String initial,
                           String target, String who, String scope, String side) {}

    private record User(long id, String login, String name, String phone, boolean enabled) {}

    /** A freshly issued session. */
    public record Issued(String token, Instant expiresAt, Actor actor) {}

    public record SmsSent(long cooldownSeconds) {}

    private final JdbcClient jdbc;
    private final TokenService tokens;
    private final AuthProperties props;
    private final AuditService audit;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcClient jdbc, TokenService tokens, AuthProperties props, AuditService audit, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.props = props;
        this.audit = audit;
        this.json = json;
    }

    // ── SMS code ─────────────────────────────────────────────────────────────

    /**
     * Issue an SMS code for {@code account} (login name or phone). At most one per {@code smsCooldown} per
     * user — the login name and the phone number of one user share the cool-down (429 otherwise).
     * The answer is the same whether or not the account exists (no masked phone, same cool-down);
     * codes are only usable for real accounts.
     */
    public SmsSent requestSmsCode(String rawAccount) {
        String account = account(rawAccount);
        Optional<User> user = findUser(account);
        String key = key(account, user);
        OffsetDateTime now = now();
        Optional<OffsetDateTime> last = jdbc.sql("select sent_at from auth_sms_code where account = :a")
                .param("a", key).query(OffsetDateTime.class).optional();
        if (last.isPresent()) {
            long wait = props.smsCooldown().toSeconds() - Duration.between(last.get(), now).toSeconds();
            if (wait > 0) throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "验证码已发送,请 " + wait + " 秒后再试", wait);
        }
        // the row is written for unknown accounts too, so the cool-down cannot be used to probe accounts
        String code = user.isPresent() ? props.demoSmsCode() : String.format("%06d", random.nextInt(1_000_000));
        jdbc.sql("""
                insert into auth_sms_code (account, code_hash, sent_at, expires_at, attempts) values (:a, :h, :s, :e, 0)
                on conflict (account) do update set code_hash = excluded.code_hash, sent_at = excluded.sent_at,
                    expires_at = excluded.expires_at, attempts = 0""")
                .param("a", key).param("h", Passwords.sha256Hex(key + "|" + code))
                .param("s", now).param("e", now.plus(props.smsTtl())).update();
        user.ifPresent(u -> log.info("SMS code issued for {} (demo gateway: code {})", u.login(), props.demoSmsCode()));
        return new SmsSent(props.smsCooldown().toSeconds());
    }

    /**
     * Rate-limit key: the login name of the user {@code account} resolves to (so "wangq" and wangq's phone
     * number count together), or the trimmed account itself when it names nobody.
     */
    private static String key(String account, Optional<User> user) {
        return user.map(User::login).orElse(account);
    }

    // ── login ────────────────────────────────────────────────────────────────

    /** 端: 医保局端 / 机构端 (user_identity.side) */
    public static final String BUREAU = "bureau";
    public static final String ORG = "org";

    /** {@code side} 请求值规范化: 空 → null(不过滤);只接受 bureau / org,否则 400。 */
    public static String normalizeSide(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (!BUREAU.equals(s) && !ORG.equals(s)) throw new AuthException(HttpStatus.BAD_REQUEST, "不支持的端: " + s);
        return s;
    }

    /** @param side 所选端(bureau / org),null 表示不限 */
    public Issued login(String method, String rawAccount, String secret, String side, String remote, String terminal) {
        String wantSide = normalizeSide(side);
        if (!"cert".equals(method) && !"sms".equals(method)) throw new AuthException(HttpStatus.BAD_REQUEST, "不支持的登录方式");
        String account = account(rawAccount);
        if (secret == null || secret.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "cert".equals(method) ? "请输入证书 PIN 码" : "请输入短信验证码");
        }
        Optional<User> found = findUser(account);
        // failures are counted per user: the login name and the phone number share one lock-out
        String key = key(account, found);
        long failures = recentFailures(key);
        if (failures >= props.maxFailures()) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                    "登录失败次数过多,请 " + props.lockWindow().toMinutes() + " 分钟后再试", props.lockWindow().toSeconds());
        }

        Optional<User> user = found.filter(User::enabled);
        String reason = user.isEmpty() ? "unknown_account"
                : "cert".equals(method) ? (pinMatches(user.get().id(), secret.trim()) ? null : "bad_pin")
                : (smsMatches(key, secret.trim()) ? null : "bad_code");
        if (reason != null) {
            attempt(key, method, false, reason, remote);
            audit.record(clip(key), "A1", "loginFailed", json.valueToTree(Map.of("method", method, "reason", reason)), terminal);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "cert".equals(method) ? "证书 PIN 码错误" : "账号或短信验证码错误");
        }
        User u = user.get();
        attempt(key, method, true, null, remote);
        jdbc.sql("update app_user set last_login = now() where id = :id").param("id", u.id()).update();
        List<Identity> held = identities(u.id());
        if (held.isEmpty()) throw new AuthException(HttpStatus.FORBIDDEN, "该账号未分配任何身份,请联系管理员");
        Identity id = held.stream().filter(x -> wantSide == null || wantSide.equals(x.side())).findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.FORBIDDEN,
                        "该账号在" + sideName(wantSide) + "下没有可用身份,请改选另一端"));
        Issued issued = openSession(u, id, method, remote, null);
        audit.record(u.name(), "A1", "login", json.valueToTree(Map.of("method", method, "identity", id.label())), terminal);
        return issued;
    }

    /** Switch the session to another identity the user holds; the old token is revoked. */
    public Issued switchIdentity(Actor actor, long identityId, String remote, String terminal) {
        requireSession(actor);
        List<Identity> held = identities(actor.userId());
        Identity id = held.stream().filter(x -> x.id() == identityId).findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.FORBIDDEN, "未持有该身份"));
        // 身份切换只在登录时所选的端内进行;换端需重新登录并选择另一端
        Identity current = held.stream().filter(x -> x.id() == actor.identityId()).findFirst().orElse(null);
        if (current != null && !current.side().equals(id.side())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "该身份属于" + sideName(id.side()) + ",与当前所选端不符,请重新登录并选择该端");
        }
        var cur = jdbc.sql("select method, expires_at from auth_session where id = :id")
                .param("id", actor.sessionId())
                .query((rs, i) -> Map.entry(rs.getString(1), rs.getObject(2, OffsetDateTime.class))).single();
        revoke(actor.sessionId());
        User u = findUserById(actor.userId());
        Issued issued = openSession(u, id, cur.getKey(), remote, cur.getValue().toInstant());
        audit.record(u.name(), "A1", "selectIdentity",
                json.valueToTree(Map.of("identity", id.label(), "role", id.role(), "target", id.target())), terminal);
        return issued;
    }

    public void logout(Actor actor, String terminal) {
        requireSession(actor);
        revoke(actor.sessionId());
        audit.record(actor.name(), "A1", "logout", json.createObjectNode(), terminal);
    }

    // ── session resolution (AuthFilter) ──────────────────────────────────────

    /** Actor for a valid, unrevoked, unexpired bearer token. */
    public Optional<Actor> resolve(String token) {
        return tokens.verify(token, Instant.now()).flatMap(c -> jdbc.sql("""
                select u.id as uid, u.login, u.name, i.id as gid, i.role_code, i.org_id, o.name as org_name
                from auth_session s
                join app_user u on u.id = s.user_id
                join user_identity i on i.id = s.identity_id
                left join org o on o.id = i.org_id
                where s.id = :sid and s.user_id = :uid and s.identity_id = :gid
                  and s.revoked_at is null and s.expires_at > now() and u.enabled""")
                .param("sid", c.sid()).param("uid", c.uid()).param("gid", c.gid())
                .query((rs, i) -> new Actor(rs.getString("name"), rs.getLong("uid"), rs.getString("login"),
                        rs.getString("role_code"), rs.getString("org_id"), rs.getString("org_name"),
                        rs.getLong("gid"), c.sid(), Actor.Source.SESSION))
                .optional());
    }

    /** Legacy {@code X-YB-User} (login or display name) → that user's primary identity. */
    public Optional<Actor> devActor(String header) {
        if (header == null || header.isBlank() || header.length() > 64) return Optional.empty();
        return jdbc.sql("""
                select u.id as uid, u.login, u.name, i.id as gid, i.role_code, i.org_id, o.name as org_name
                from app_user u
                join user_identity i on i.user_id = u.id
                left join org o on o.id = i.org_id
                where (u.login = :h or u.name = :h) and u.enabled
                order by (u.login = :h) desc, i.sort_order, i.id limit 1""")
                .param("h", header.trim())
                .query((rs, i) -> new Actor(rs.getString("name"), rs.getLong("uid"), rs.getString("login"),
                        rs.getString("role_code"), rs.getString("org_id"), rs.getString("org_name"),
                        rs.getLong("gid"), null, Actor.Source.DEV_HEADER))
                .optional();
    }

    // ── GET /auth/me ─────────────────────────────────────────────────────────

    public Map<String, Object> me(Actor actor) {
        requireSession(actor);
        User u = findUserById(actor.userId());
        Identity cur = identities(u.id()).stream().filter(x -> x.id() == actor.identityId()).findFirst().orElseThrow();
        // 身份列表只含当前身份所在端的身份
        List<Identity> ids = identities(u.id()).stream().filter(x -> x.side().equals(cur.side())).toList();
        OffsetDateTime exp = jdbc.sql("select expires_at from auth_session where id = :id")
                .param("id", actor.sessionId()).query(OffsetDateTime.class).single();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", Map.of("login", u.login(), "name", u.name()));
        out.put("identity", view(cur));
        out.put("identities", ids.stream().map(AuthService::view).toList());
        out.put("viewer", viewer(u.name(), cur));
        out.put("pages", AccessPolicy.pagesFor(cur.role()));
        out.put("readOnly", AccessPolicy.isReadOnly(cur.role()));
        out.put("expiresAt", exp.toInstant().toString());
        return out;
    }

    static Map<String, Object> view(Identity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id());
        m.put("initial", i.initial());
        m.put("name", i.label());
        m.put("desc", i.description());
        m.put("zone", i.zone());
        m.put("tone", i.tone());
        m.put("target", i.target());
        if (i.who() != null) m.put("who", i.who());
        m.put("role", i.role());
        m.put("roleName", i.roleName());
        m.put("dataScope", i.dataScope());
        if (i.orgId() != null) m.put("orgId", i.orgId());
        if (i.orgName() != null) m.put("orgName", i.orgName());
        m.put("scope", i.scope());
        m.put("side", i.side());
        return m;
    }

    /** Header identity block, same shape as web/src/app/nav.ts {@code Viewer}. */
    static Map<String, Object> viewer(String name, Identity i) {
        return Map.of("name", name, "role", i.roleName(), "scope", i.scope(),
                "zone", Map.of("label", i.zone(), "tone", "ok".equals(i.tone()) ? "green" : "blue"),
                "org", i.orgName() == null ? "" : i.orgName());
    }

    public List<Identity> identities(long userId) {
        return jdbc.sql("""
                select i.id, i.role_code, r.name as role_name, r.data_scope, i.org_id, o.name as org_name, i.label,
                       i.description, i.zone, i.tone, i.initial, i.target, i.who, i.scope, i.side
                from user_identity i
                join app_role r on r.code = i.role_code
                left join org o on o.id = i.org_id
                where i.user_id = :u order by i.sort_order, i.id""")
                .param("u", userId)
                .query((rs, n) -> new Identity(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name"),
                        rs.getString("data_scope"), rs.getString("org_id"), rs.getString("org_name"), rs.getString("label"),
                        rs.getString("description"), rs.getString("zone"), rs.getString("tone"), rs.getString("initial"),
                        rs.getString("target"), rs.getString("who"), rs.getString("scope"), rs.getString("side")))
                .list();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Issued openSession(User u, Identity id, String method, String remote, Instant expiresAt) {
        String sid = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = expiresAt != null ? expiresAt : now.plus(tokens.ttl());
        jdbc.sql("""
                insert into auth_session (id, user_id, identity_id, method, issued_at, expires_at, remote)
                values (:id, :u, :g, :m, :iat, :exp, :r)""")
                .param("id", sid).param("u", u.id()).param("g", id.id()).param("m", method)
                .param("iat", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("exp", OffsetDateTime.ofInstant(exp, ZoneOffset.UTC)).param("r", clip(remote)).update();
        String token = tokens.sign(new TokenService.Claims(sid, u.id(), id.id(), now.getEpochSecond(), exp.getEpochSecond()));
        Actor actor = new Actor(u.name(), u.id(), u.login(), id.role(), id.orgId(), id.orgName(), id.id(), sid, Actor.Source.SESSION);
        return new Issued(token, Instant.ofEpochSecond(exp.getEpochSecond()), actor);
    }

    private void revoke(String sid) {
        jdbc.sql("update auth_session set revoked_at = now() where id = :id and revoked_at is null").param("id", sid).update();
    }

    private static void requireSession(Actor actor) {
        if (actor == null || actor.source() != Actor.Source.SESSION) throw new AuthException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期");
    }

    private boolean pinMatches(long userId, String pin) {
        return jdbc.sql("select secret_hash from user_credential where user_id = :u and kind = 'pin'")
                .param("u", userId).query(String.class).optional()
                .map(h -> Passwords.matches(pin, h)).orElse(false);
    }

    private boolean smsMatches(String account, String code) {
        var row = jdbc.sql("select code_hash, expires_at, attempts from auth_sms_code where account = :a")
                .param("a", account)
                .query((rs, i) -> new Object[] {rs.getString(1), rs.getObject(2, OffsetDateTime.class), rs.getInt(3)})
                .optional();
        if (row.isEmpty()) return false;
        String hash = (String) row.get()[0];
        OffsetDateTime exp = (OffsetDateTime) row.get()[1];
        int attempts = (Integer) row.get()[2];
        if (exp.isBefore(now()) || attempts >= MAX_SMS_ATTEMPTS) return false;
        boolean ok = java.security.MessageDigest.isEqual(hash.getBytes(), Passwords.sha256Hex(account + "|" + code).getBytes());
        if (ok) {
            // one-time: consumed on success (the cool-down restarts from the next request)
            jdbc.sql("update auth_sms_code set expires_at = now(), attempts = :max where account = :a")
                    .param("max", MAX_SMS_ATTEMPTS).param("a", account).update();
        } else {
            jdbc.sql("update auth_sms_code set attempts = attempts + 1 where account = :a").param("a", account).update();
        }
        return ok;
    }

    private long recentFailures(String account) {
        return jdbc.sql("""
                select count(*) from auth_attempt
                where account = :a and not success and at > now() - make_interval(secs => :w)
                  and at > coalesce((select max(at) from auth_attempt where account = :a and success), '-infinity')""")
                .param("a", account).param("w", props.lockWindow().toSeconds())
                .query(Long.class).single();
    }

    private void attempt(String account, String method, boolean ok, String reason, String remote) {
        jdbc.sql("insert into auth_attempt (account, method, success, reason, remote) values (:a, :m, :s, :r, :rem)")
                .param("a", account).param("m", method).param("s", ok).param("r", reason).param("rem", clip(remote)).update();
    }

    private Optional<User> findUser(String account) {
        return jdbc.sql("select id, login, name, phone, enabled from app_user where login = :a or phone = :a order by (login = :a) desc limit 1")
                .param("a", account)
                .query((rs, i) -> new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .optional();
    }

    private User findUserById(long id) {
        return jdbc.sql("select id, login, name, phone, enabled from app_user where id = :id").param("id", id)
                .query((rs, i) -> new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .single();
    }

    private static String account(String raw) {
        String a = raw == null ? "" : raw.trim();
        if (a.isEmpty()) throw new AuthException(HttpStatus.BAD_REQUEST, "请输入账号");
        if (a.length() > 64) throw new AuthException(HttpStatus.BAD_REQUEST, "账号过长");
        if (a.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) throw new AuthException(HttpStatus.BAD_REQUEST, "账号含非法字符");
        return a;
    }

    static String sideName(String side) {
        return ORG.equals(side) ? "机构端" : "医保局端";
    }

    static String mask(String phone) {
        if (phone == null || phone.length() < 7) return null;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String clip(String s) {
        if (s == null) return null;
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
