package cn.ybdata.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String SID = "6f1c2c1e-6f0e-4c8e-9d5e-0a1b2c3d4e5f";
    private static final Instant T0 = Instant.parse("2026-10-04T08:00:00Z");

    private final TokenService tokens = new TokenService(KEY, Duration.ofHours(8));

    @Test
    void signThenVerifyRoundTrips() {
        String t = tokens.issue(SID, 5, 7, T0);
        assertThat(t).startsWith("yb1.").matches("^yb1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
        var c = tokens.verify(t, T0.plusSeconds(60)).orElseThrow();
        assertThat(c.sid()).isEqualTo(SID);
        assertThat(c.uid()).isEqualTo(5);
        assertThat(c.gid()).isEqualTo(7);
        assertThat(c.exp() - c.iat()).isEqualTo(8 * 3600);
        assertThat(c.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(8)));
    }

    @Test
    void expiresAfterEightHours() {
        String t = tokens.issue(SID, 5, 7, T0);
        assertThat(tokens.verify(t, T0.plus(Duration.ofHours(8)).minusSeconds(1))).isPresent();
        assertThat(tokens.verify(t, T0.plus(Duration.ofHours(8)))).isEmpty();
        assertThat(tokens.verify(t, T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    void rejectsTokensIssuedInTheFuture() {
        String t = tokens.issue(SID, 5, 7, T0.plus(Duration.ofHours(1)));
        assertThat(tokens.verify(t, T0)).isEmpty();
    }

    @Test
    void rejectsTamperedClaims() {
        String t = tokens.issue(SID, 5, 7, T0);
        String[] p = t.split("\\.");
        // same signature, claims rewritten to another user / identity / later expiry
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (SID + "|1|1|" + T0.getEpochSecond() + "|" + T0.plus(Duration.ofDays(30)).getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        assertThat(tokens.verify(p[0] + "." + forged + "." + p[2], T0)).isEmpty();
        // flipped signature character
        char last = p[2].charAt(0);
        String badSig = (last == 'A' ? 'B' : 'A') + p[2].substring(1);
        assertThat(tokens.verify(p[0] + "." + p[1] + "." + badSig, T0)).isEmpty();
    }

    @Test
    void rejectsOtherKeysAndGarbage() {
        String t = new TokenService("ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8), Duration.ofHours(8)).issue(SID, 5, 7, T0);
        assertThat(tokens.verify(t, T0)).isEmpty();
        assertThat(tokens.verify(null, T0)).isEmpty();
        assertThat(tokens.verify("", T0)).isEmpty();
        assertThat(tokens.verify("yb1.x.y", T0)).isEmpty();
        assertThat(tokens.verify("yb1.%%%.%%%", T0)).isEmpty();
        assertThat(tokens.verify("yb2." + t.substring(4), T0)).isEmpty();
        assertThat(tokens.verify(t + ".extra", T0)).isEmpty();
    }

    @Test
    void refusesShortKeysAndBadSessionIds() {
        assertThatThrownBy(() -> new TokenService(new byte[16], Duration.ofHours(8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokens.issue("x|1", 5, 7, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankSecretFallsBackToRandomKeyAndShortSecretIsStretched() {
        var random = new TokenService(new AuthProperties("", null, null, null, null, null, null, null));
        assertThat(random.verify(random.issue(SID, 1, 1, Instant.now()), Instant.now())).isPresent();
        var shortKey = new TokenService(new AuthProperties("short", null, null, null, null, null, null, null));
        assertThat(shortKey.ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(shortKey.verify(shortKey.issue(SID, 1, 1, Instant.now()), Instant.now())).isPresent();
    }
}
