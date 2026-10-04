package gov.ybj.chsdpub.config;

import gov.ybj.chsdpub.auth.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * 两类令牌：
 * - 登录票据（typ=ticket，5 分钟）：认证通过、尚未选择身份；
 * - 会话令牌（typ=session）：已选择身份，携带角色与数据范围。
 */
@Service
public class JwtService {

    private static final Duration TICKET_TTL = Duration.ofMinutes(5);

    private final SecretKey key;
    private final Duration ttl;

    public record Session(AuthUser user, long tokenVersion) {}

    public record Ticket(long userId, String method) {}

    public JwtService(AppProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("未配置 JWT 密钥或长度不足 32 字符（app.jwt.secret / JWT_SECRET）");
        }
        this.key = Keys.hmacShaKeyFor(sha256(secret));
        this.ttl = Duration.ofHours(props.jwt().ttlHours());
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String issueTicket(long userId, String method) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(userId)).claim("typ", "ticket").claim("method", method)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(TICKET_TTL))).signWith(key).compact();
    }

    public Optional<Ticket> parseTicket(String token) {
        return claims(token).filter(c -> "ticket".equals(c.get("typ", String.class)))
                .map(c -> new Ticket(Long.parseLong(c.getSubject()), c.get("method", String.class)));
    }

    public String issueSession(AuthUser u, long tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(u.userId()))
                .claim("typ", "session")
                .claim("iid", u.identityId())
                .claim("username", u.username())
                .claim("name", u.name())
                .claim("role", u.role())
                .claim("roleLabel", u.roleLabel())
                .claim("org", u.org())
                .claim("scope", u.scope())
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<Session> parseSession(String token) {
        return claims(token).filter(c -> "session".equals(c.get("typ", String.class))).map(c -> new Session(
                new AuthUser(Long.parseLong(c.getSubject()), c.get("iid", Number.class).longValue(), c.get("username", String.class),
                        c.get("name", String.class), c.get("role", String.class), c.get("roleLabel", String.class),
                        c.get("org", String.class), c.get("scope", String.class)),
                c.get("tv", Number.class).longValue()));
    }

    private Optional<Claims> claims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
