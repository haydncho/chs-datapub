package cn.ybdata.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class BureauCockpitScopeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final BureauCockpitScope scope = new BureauCockpitScope();

    private ObjectNode payload() throws Exception {
        return (ObjectNode) json.readTree("""
                {"identities":[
                   {"id":"conv","alerts":[
                      {"type":"预警","text":"某肛肠专科医院 · GG19 次均 +23.6%"},
                      {"type":"预警","text":"甲县人民医院 · ES35 再住院 8.7%"}]},
                   {"id":"hosp","alerts":[]}],
                 "institutions":[
                   {"name":"第一人民医院","district":"市区"},
                   {"name":"甲县人民医院","district":"甲县"},
                   {"name":"乙县中医院","district":"乙县"}],
                 "depts":[{"name":"神经内科"}],
                 "matrix":[{"name":"x"}],"matrixSummary":{"unpublished":3,"unread":2,"unanswered":1}}""");
    }

    private Actor actor(String role, String orgName) {
        return new Actor("测试", 1L, "t", role, "O1", orgName, 1L, "s", Actor.Source.SESSION);
    }

    @Test
    void appliesToEnforcedNonHospitalActorsOnly() {
        assertThat(scope.appliesTo("cockpit", actor("convener", "示例市医保局"))).isTrue();
        assertThat(scope.appliesTo("cockpit", actor("county", "甲县医保局"))).isTrue();
        assertThat(scope.appliesTo("cockpit", actor("hospital", "示例市第一人民医院"))).isFalse();
        assertThat(scope.appliesTo("cockpit", Actor.devDefault("x"))).isFalse();
        assertThat(scope.appliesTo("A3", actor("convener", "示例市医保局"))).isFalse();
    }

    @Test
    void bureauRolesKeepOnlyTheCityView() throws Exception {
        ObjectNode p = payload();
        scope.apply(p, actor("convener", "示例市医保局"));
        assertThat(p.path("identities")).hasSize(1);
        assertThat(p.path("identities").get(0).path("id").asText()).isEqualTo("conv");
        assertThat(p.path("institutions")).hasSize(3); // the city view keeps every institution
        assertThat(p.path("identities").get(0).path("alerts")).hasSize(2);
        assertThat(p.path("depts")).isEmpty(); // 本院科室明细 is not part of the 医保局 view
    }

    @Test
    void countyOnlySeesItsOwnDistrict() throws Exception {
        ObjectNode p = payload();
        scope.apply(p, actor("county", "甲县医保局"));
        assertThat(p.path("institutions")).hasSize(1);
        assertThat(p.path("institutions").get(0).path("name").asText()).isEqualTo("甲县人民医院");
        assertThat(p.path("identities").get(0).path("alerts")).hasSize(1);
        assertThat(p.path("identities").get(0).path("alerts").get(0).path("text").asText()).contains("甲县人民医院");
        assertThat(p.path("matrix")).isEmpty();
        assertThat(p.path("matrixSummary").path("unpublished").asInt()).isZero();
    }

    @Test
    void countyWithUnknownDistrictSeesNothingNamed() throws Exception {
        ObjectNode p = payload();
        scope.apply(p, actor("county", "未知区医保局"));
        assertThat(p.path("institutions")).isEmpty();
        assertThat(p.path("identities").get(0).path("alerts")).isEmpty();
    }
}
