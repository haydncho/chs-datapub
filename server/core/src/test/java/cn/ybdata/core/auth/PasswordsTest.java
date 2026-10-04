package cn.ybdata.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordsTest {

    @Test
    void hashAndMatch() {
        String h = Passwords.hash("123456");
        assertThat(h).startsWith("pbkdf2_sha256$120000$");
        assertThat(Passwords.matches("123456", h)).isTrue();
        assertThat(Passwords.matches("123457", h)).isFalse();
        assertThat(Passwords.hash("123456")).isNotEqualTo(h); // salted
    }

    @Test
    void demoPinSeededByV4Matches() {
        // chenzy's row in V4__auth_identity.sql
        assertThat(Passwords.matches("123456",
                "pbkdf2_sha256$120000$Jj7tPRx4+GHnZXw9rUJK9w==$6BkP+6umneAjFWJpq/ue4XAwFFKFCdFohHxKaTuvqRU=")).isTrue();
    }

    @Test
    void malformedHashesNeverMatch() {
        assertThat(Passwords.matches("123456", null)).isFalse();
        assertThat(Passwords.matches(null, "x")).isFalse();
        assertThat(Passwords.matches("123456", "md5$1$a$b")).isFalse();
        assertThat(Passwords.matches("123456", "pbkdf2_sha256$x$a$b")).isFalse();
    }

    @Test
    void masksPhones() {
        assertThat(AuthService.mask("13800000001")).isEqualTo("138****0001");
        assertThat(AuthService.mask(null)).isNull();
    }
}
