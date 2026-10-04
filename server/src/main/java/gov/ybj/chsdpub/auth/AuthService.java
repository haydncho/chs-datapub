package gov.ybj.chsdpub.auth;

import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Roles;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import gov.ybj.chsdpub.config.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统一身份认证（A1）：数字证书（UKey）或 账号 + 密码 + 短信验证码。
 * 认证通过后发放 5 分钟登录票据；选择本次身份后发放会话令牌。
 */
@Service
public class AuthService {

    public static final String CA = "CA";
    public static final String PASSWORD = "PASSWORD";

    private final JdbcTemplate jdbc;
    private final JwtService jwt;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AuthService(JdbcTemplate jdbc, JwtService jwt, AuditService audit, PasswordEncoder encoder, AppProperties props) {
        this.jdbc = jdbc;
        this.jwt = jwt;
        this.audit = audit;
        this.encoder = encoder;
        this.props = props;
    }

    record UserRow(long id, String username, String name, String phone, String certIssuer, LocalDate certExpires,
                   boolean enabled, int failed, OffsetDateTime lockedUntil, String passwordHash, long tokenVersion) {}

    public record Identity(long id, String role, String roleLabel, String org, String orgDetail, String scope, String home,
                           String homeLabel) {}

    public record Cert(String holder, String org, String issuer, LocalDate expires) {}

    public record LoginResult(String ticket, String name, List<Identity> identities) {}

    public record Me(String username, String name, String role, String roleLabel, String org, String scope,
                     List<String> pages) {}

    public record SessionResult(String token, Me user) {}

    private UserRow user(String username) {
        List<UserRow> r = jdbc.query("""
                select id, username, name, phone_masked, cert_issuer, cert_expires, enabled, failed_count, locked_until,
                       password_hash, token_version from app_user where username = ?""",
                (rs, i) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getObject(6, LocalDate.class), rs.getBoolean(7), rs.getInt(8), rs.getObject(9, OffsetDateTime.class),
                        rs.getString(10), rs.getLong(11)), username);
        return r.isEmpty() ? null : r.get(0);
    }

    /** 已识别的 UKey 证书。演示环境模拟识别；生产环境由专网网关传入客户端证书（未接入时返回 null）。 */
    public Cert recognizedCert() {
        if (!props.demo().enabled() || Texts.blank(props.demo().caHolder())) return null;
        UserRow u = user(props.demo().caHolder());
        if (u == null || u.certIssuer() == null) return null;
        String org = jdbc.queryForObject("select org from user_identity where user_id = ? order by sort limit 1", String.class, u.id());
        return new Cert(u.name(), org, u.certIssuer(), u.certExpires());
    }

    public Map<String, Object> sendSms(String username) {
        UserRow u = user(username);
        if (u == null || !u.enabled()) throw ApiException.validation("账号不存在或已停用");
        // 短信网关对接点：生产环境经政务短信平台下发；演示环境固定验证码
        return Map.of("sentTo", u.phone(), "resendSeconds", 60);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String method, String username, String password, String smsCode, String pin) {
        UserRow u;
        if (CA.equals(method)) {
            Cert cert = recognizedCert();
            if (cert == null) throw ApiException.validation("未识别到数字证书,请插入 UKey 后重试");
            if (Texts.blank(pin)) throw ApiException.validation("请输入证书 PIN 码");
            u = user(props.demo().caHolder());
        } else if (PASSWORD.equals(method)) {
            if (Texts.blank(username) || Texts.blank(password)) throw ApiException.validation("请输入账号和密码");
            if (Texts.blank(smsCode)) throw ApiException.validation("请输入短信验证码");
            u = user(username.trim());
            if (u == null) {
                audit.record("未知", "—", AuditService.LOGIN, "账号不存在:" + Texts.truncate(username, 32), "失败", null, AuditService.currentIp());
                throw ApiException.unauthorized("账号或密码错误");
            }
            checkLock(u);
            boolean pwdOk = props.demo().enabled() || (u.passwordHash() != null && encoder.matches(password, u.passwordHash()));
            boolean smsOk = props.demo().enabled() ? smsCode.replace(" ", "").equals(props.demo().smsCode()) : false;
            if (!pwdOk || !smsOk) {
                fail(u);
                throw ApiException.unauthorized(!pwdOk ? "账号或密码错误" : "短信验证码错误");
            }
        } else {
            throw ApiException.validation("不支持的认证方式");
        }
        if (!u.enabled()) throw ApiException.forbidden("账号已停用");
        jdbc.update("update app_user set failed_count = 0, locked_until = null where id = ?", u.id());
        return new LoginResult(jwt.issueTicket(u.id(), method), u.name(), identities(u.id()));
    }

    private void checkLock(UserRow u) {
        if (u.lockedUntil() != null && u.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw ApiException.locked("账号已锁定,请 " + props.security().loginLockMinutes() + " 分钟后再试");
        }
    }

    private void fail(UserRow u) {
        int n = u.failed() + 1;
        int max = props.security().loginMaxFailures();
        if (n >= max) {
            jdbc.update("update app_user set failed_count = 0, locked_until = now() + make_interval(mins => ?) where id = ?",
                    props.security().loginLockMinutes(), u.id());
            audit.record(u.name(), "—", AuditService.LOGIN, "连续 " + max + " 次密码错误", "已锁定", null, AuditService.currentIp());
            throw ApiException.locked("连续 " + max + " 次认证失败,账号已锁定 " + props.security().loginLockMinutes() + " 分钟");
        }
        jdbc.update("update app_user set failed_count = ? where id = ?", n, u.id());
    }

    public List<Identity> identities(long userId) {
        return jdbc.query("""
                select id, role, role_label, org, org_detail, scope, home, home_label from user_identity
                where user_id = ? order by sort""",
                (rs, i) -> new Identity(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8)), userId);
    }

    @Transactional
    public SessionResult selectIdentity(String ticket, long identityId) {
        JwtService.Ticket t = jwt.parseTicket(ticket).orElseThrow(() -> ApiException.unauthorized("登录票据已过期,请重新认证"));
        Identity id = identities(t.userId()).stream().filter(x -> x.id() == identityId).findFirst()
                .orElseThrow(() -> ApiException.forbidden("该身份不属于当前账号"));
        Map<String, Object> u = jdbc.queryForMap("select username, name, token_version from app_user where id = ?", t.userId());
        AuthUser au = new AuthUser(t.userId(), id.id(), (String) u.get("username"), (String) u.get("name"), id.role(),
                id.roleLabel(), id.org(), id.scope());
        audit.record(au, AuditService.LOGIN, (CA.equals(t.method()) ? "数字证书登录" : "账号 + 短信验证码登录") + " · " + id.roleLabel(), "成功");
        return new SessionResult(jwt.issueSession(au, ((Number) u.get("token_version")).longValue()), me(au));
    }

    public Me me(AuthUser u) {
        return new Me(u.username(), u.name(), u.role(), u.roleLabel(), u.org(), u.scope(), Roles.pagesOf(u.role()));
    }

    /** 退出登录：令牌版本 +1，已签发的会话令牌全部失效。 */
    @Transactional
    public void logout(AuthUser u) {
        jdbc.update("update app_user set token_version = token_version + 1 where id = ?", u.userId());
    }
}
