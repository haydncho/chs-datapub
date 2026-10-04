package cn.ybdata.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Session tokens: {@code yb1.<base64url(claims)>.<base64url(HMAC-SHA256(key, "yb1." + claims))>}
 * where claims = {@code sid|uid|gid|iat|exp} (epoch seconds). JDK crypto only.
 *
 * <p>The signature makes a token tamper-evident and self-expiring; the server additionally keeps an
 * {@code auth_session} row per sid so logout / identity switch revoke it.
 */
@Component
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String PREFIX = "yb1";
    private static final String ALG = "HmacSHA256";
    private static final Pattern SID = Pattern.compile("^[0-9a-f-]{36}$");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    /** Signed contents of a session token. */
    public record Claims(String sid, long uid, long gid, long iat, long exp) {
        public Instant expiresAt() {
            return Instant.ofEpochSecond(exp);
        }
    }

    private final SecretKeySpec key;
    private final Duration ttl;

    @Autowired
    public TokenService(AuthProperties props) {
        this(keyFrom(props.secret()), props.ttl());
    }

    public TokenService(byte[] secret, Duration ttl) {
        if (secret.length < 32) throw new IllegalArgumentException("auth secret must be at least 32 bytes");
        this.key = new SecretKeySpec(secret, ALG);
        this.ttl = ttl;
    }

    private static byte[] keyFrom(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("YB_AUTH_SECRET not set — using a random per-process signing key (sessions end on restart)");
            byte[] k = new byte[32];
            new SecureRandom().nextBytes(k);
            return k;
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) return raw;
        // short secrets are stretched with SHA-256 so the HMAC key is always 256 bits
        log.warn("YB_AUTH_SECRET is shorter than 32 bytes — use a longer random value in production");
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public Duration ttl() {
        return ttl;
    }

    /** Issue a token for session {@code sid} valid for {@link #ttl()} from {@code now}. */
    public String issue(String sid, long uid, long gid, Instant now) {
        long iat = now.getEpochSecond();
        return sign(new Claims(sid, uid, gid, iat, iat + ttl.toSeconds()));
    }

    public String sign(Claims c) {
        if (!SID.matcher(c.sid()).matches()) throw new IllegalArgumentException("bad sid");
        String body = B64.encodeToString((c.sid() + "|" + c.uid() + "|" + c.gid() + "|" + c.iat() + "|" + c.exp())
                .getBytes(StandardCharsets.UTF_8));
        String signed = PREFIX + "." + body;
        return signed + "." + B64.encodeToString(mac(signed));
    }

    /** @return the claims when the signature is valid and the token has not expired at {@code now} */
    public Optional<Claims> verify(String token, Instant now) {
        if (token == null || token.length() > 512) return Optional.empty();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) return Optional.empty();
        try {
            byte[] expected = mac(parts[0] + "." + parts[1]);
            byte[] given = B64D.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, given)) return Optional.empty();
            String[] f = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8).split("\\|", -1);
            if (f.length != 5 || !SID.matcher(f[0]).matches()) return Optional.empty();
            Claims c = new Claims(f[0], Long.parseLong(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4]));
            if (now.getEpochSecond() >= c.exp() || c.iat() > now.getEpochSecond() + 60) return Optional.empty();
            return Optional.of(c);
        } catch (IllegalArgumentException e) { // bad base64 / number
            return Optional.empty();
        }
    }

    private byte[] mac(String data) {
        try {
            Mac m = Mac.getInstance(ALG);
            m.init(key);
            return m.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
