package cn.ybdata.core.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * yb.auth.* — unified login settings.
 *
 * @param secret        HMAC-SHA256 key for session tokens (env YB_AUTH_SECRET); when blank a random
 *                      per-process key is generated (tokens then do not survive a restart)
 * @param devHeader     accept the legacy {@code X-YB-User} header / per-page demo identity for
 *                      requests without a bearer token (dev & existing tests); set false in production
 * @param ttl           session lifetime (8h)
 * @param smsCooldown   minimum interval between two SMS codes for one account
 * @param smsTtl        validity of an SMS code
 * @param demoSmsCode   the code every SMS "sends" in this demo (no SMS gateway wired)
 * @param maxFailures   failed logins per account inside {@code lockWindow} before lock-out
 * @param lockWindow    lock-out window
 */
@ConfigurationProperties(prefix = "yb.auth")
public record AuthProperties(String secret, Boolean devHeader, Duration ttl, Duration smsCooldown, Duration smsTtl,
                             String demoSmsCode, Integer maxFailures, Duration lockWindow) {
    public AuthProperties {
        if (secret == null) secret = "";
        if (devHeader == null) devHeader = true;
        if (ttl == null) ttl = Duration.ofHours(8);
        if (smsCooldown == null) smsCooldown = Duration.ofSeconds(60);
        if (smsTtl == null) smsTtl = Duration.ofMinutes(5);
        if (demoSmsCode == null || demoSmsCode.isBlank()) demoSmsCode = "123456";
        if (maxFailures == null) maxFailures = 5;
        if (lockWindow == null) lockWindow = Duration.ofMinutes(15);
    }
}
