package cn.ybdata.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

    @Test
    void pageMatrixByRole() {
        assertThat(AccessPolicy.pagesFor("convener")).isEqualTo(AccessPolicy.ALL_PAGES).hasSize(24);
        assertThat(AccessPolicy.pagesFor("admin")).containsExactly(
                "cockpit", "A1", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12", "A13", "A14", "A15");
        assertThat(AccessPolicy.pagesFor("analyst")).containsExactly("A1", "A6", "A7");
        assertThat(AccessPolicy.pagesFor("hospital")).containsExactly(
                "cockpit", "A1", "B1", "B2", "B3", "B4", "B5", "B6", "B7", "D1");
        assertThat(AccessPolicy.pagesFor("county")).containsExactly("cockpit", "A1", "A11", "C3");
        assertThat(AccessPolicy.pagesFor("auditor")).containsExactly("A1", "A14");
        assertThat(AccessPolicy.pagesFor("observer")).containsExactly("A1", "C3");
        assertThat(AccessPolicy.pagesFor("nobody")).isEmpty();
    }

    @Test
    void hospitalSeesOnlyItsSide() {
        for (String p : List.of("B1", "B3", "B7", "D1", "cockpit")) assertThat(AccessPolicy.canView("hospital", p)).as(p).isTrue();
        for (String p : List.of("A3", "A8", "A10", "A14", "C3")) assertThat(AccessPolicy.canView("hospital", p)).as(p).isFalse();
        assertThat(AccessPolicy.check("hospital", "GET", "pages/A10")).hasValueSatisfying(m -> assertThat(m).contains("A10"));
        assertThat(AccessPolicy.check("hospital", "GET", "pages/B4")).isEmpty();
        assertThat(AccessPolicy.check("hospital", "POST", "actions/B4/signReport")).isEmpty();
        assertThat(AccessPolicy.check("hospital", "POST", "actions/A10/replyFeedback")).isPresent();
    }

    @Test
    void auditorIsReadOnly() {
        assertThat(AccessPolicy.check("auditor", "GET", "pages/A14")).isEmpty();
        assertThat(AccessPolicy.check("auditor", "GET", "audit")).isEmpty();
        assertThat(AccessPolicy.check("auditor", "GET", "audit/verify")).isEmpty();
        assertThat(AccessPolicy.check("auditor", "POST", "actions/A14/anything"))
                .hasValueSatisfying(m -> assertThat(m).contains("只读"));
        assertThat(AccessPolicy.check("auditor", "POST", "audit/x")).isPresent();
        assertThat(AccessPolicy.check("auditor", "GET", "pages/A12")).isPresent();
    }

    @Test
    void observerOnlyPublicLayer() {
        assertThat(AccessPolicy.check("observer", "GET", "pages/C3")).isEmpty();
        assertThat(AccessPolicy.check("observer", "POST", "actions/C3/submitSuggestion")).isEmpty();
        assertThat(AccessPolicy.check("observer", "GET", "pages/cockpit")).isPresent();
        assertThat(AccessPolicy.check("observer", "GET", "audit")).isPresent();
        assertThat(AccessPolicy.check("observer", "GET", "analytics/drg/panorama")).isPresent();
    }

    @Test
    void approvalIsConvenerOnly() {
        assertThat(AccessPolicy.canAct("convener", "A8", "approvePublish")).isTrue();
        assertThat(AccessPolicy.canAct("admin", "A8", "approvePublish")).isFalse();
        assertThat(AccessPolicy.canAct("admin", "A8", "rejectPublish")).isFalse();
        assertThat(AccessPolicy.canAct("admin", "A8", "resetDemo")).isTrue();
        assertThat(AccessPolicy.canAct("convener", "B4", "signReport")).isTrue();
    }

    @Test
    void otherApiAreas() {
        assertThat(AccessPolicy.check("analyst", "GET", "analytics/topics/recommend")).isEmpty();
        assertThat(AccessPolicy.check("hospital", "GET", "analytics/topics/recommend")).isPresent();
        assertThat(AccessPolicy.check("hospital", "GET", "settings/appearance")).isEmpty();
        assertThat(AccessPolicy.check("hospital", "PUT", "settings/appearance")).isPresent();
        assertThat(AccessPolicy.check("admin", "PUT", "settings/appearance")).isEmpty();
        assertThat(AccessPolicy.check("hospital", "GET", "pages")).isEmpty();
        assertThat(AccessPolicy.check("observer", "GET", "auth/me")).isEmpty();
        assertThat(AccessPolicy.check("admin", "GET", "audit")).isEmpty();
    }

    @Test
    void publicEndpoints() {
        assertThat(AuthFilter.isPublic("POST", "auth/login")).isTrue();
        assertThat(AuthFilter.isPublic("POST", "auth/sms-code")).isTrue();
        assertThat(AuthFilter.isPublic("GET", "pages/A1")).isTrue();
        assertThat(AuthFilter.isPublic("GET", "auth/me")).isFalse();
        assertThat(AuthFilter.isPublic("GET", "pages/A3")).isFalse();
        assertThat(AuthFilter.isPublic("POST", "actions/A1/login")).isFalse();
    }
}
