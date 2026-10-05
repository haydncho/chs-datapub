package cn.ybdata.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 登录页「选择端」的请求值规范化与端名称。 */
class AuthSideTest {

    @Test
    void emptySideMeansNoFilter() {
        assertThat(AuthService.normalizeSide(null)).isNull();
        assertThat(AuthService.normalizeSide("")).isNull();
        assertThat(AuthService.normalizeSide("  ")).isNull();
    }

    @Test
    void onlyBureauAndOrgAreAccepted() {
        assertThat(AuthService.normalizeSide("bureau")).isEqualTo("bureau");
        assertThat(AuthService.normalizeSide(" org ")).isEqualTo("org");
        assertThatThrownBy(() -> AuthService.normalizeSide("admin")).isInstanceOfSatisfying(AuthException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> AuthService.normalizeSide("BUREAU")).isInstanceOf(AuthException.class);
    }

    @Test
    void sideNames() {
        assertThat(AuthService.sideName("bureau")).isEqualTo("医保局端");
        assertThat(AuthService.sideName("org")).isEqualTo("机构端");
    }
}
