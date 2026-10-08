package cn.ybdata.core.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * yb.auth.* — unified login settings.
 *
 * @param secret        HMAC-SHA256 key for session tokens (env YB_AUTH_SECRET); when blank a random
 *                      per-process key is generated (tokens then do not survive a restart)
 * @param devHeader     tests only (default false): accept {@code X-YB-User} naming a known user in place of a
 *                      bearer token; the request is access-checked as that user's primary identity
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
        if (devHeader == null) devHeader = false;
        if (ttl == null) ttl = Duration.ofHours(8);
        if (smsCooldown == null) smsCooldown = Duration.ofSeconds(60);
        if (smsTtl == null) smsTtl = Duration.ofMinutes(5);
        if (demoSmsCode == null || demoSmsCode.isBlank()) demoSmsCode = "123456";
        if (maxFailures == null) maxFailures = 5;
        if (lockWindow == null) lockWindow = Duration.ofMinutes(15);
    }
}
