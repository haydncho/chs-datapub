package cn.ybdata.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class HospitalScopeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Actor hospital = new Actor("李敏", 5L, "limin", "hospital", "H001", "示例市第一人民医院", 9L, "sid", Actor.Source.SESSION);
    private final Actor convener = new Actor("陈志远", 1L, "chenzy", "convener", "YBJ", "示例市医保局", 1L, "sid", Actor.Source.SESSION);

    private ObjectNode read(String s) throws Exception {
        return (ObjectNode) json.readTree(s.replace('\'', '"'));
    }

    @Test
    void cockpitKeepsOnlyOwnIdentityAndDropsNamedInstitutionData() throws Exception {
        ObjectNode p = read("""
                {'identities':[{'id':'conv','name':'陈志远','alerts':[{'text':'某肛肠专科医院 · GG19'}]},
                               {'id':'hosp','name':'李敏','org':'x','orgShort':'x'}],
                 'institutions':[{'name':'第二人民医院','diff':-639}],
                 'flows':[{'name':'省会市'}],
                 'matrix':[{'name':'CMI值'}], 'matrixSummary':{'unpublished':3,'unread':3,'unanswered':2},
                 'drgs':[{'code':'BR25'}], 'depts':[{'name':'神经内科'}]}""");
        var f = new HospitalCockpitScope();
        assertThat(f.appliesTo("cockpit", hospital)).isTrue();
        assertThat(f.appliesTo("cockpit", convener)).isFalse();
        assertThat(f.appliesTo("cockpit", Actor.devDefault(null))).isFalse();
        assertThat(f.appliesTo("B1", hospital)).isFalse();
        f.apply(p, hospital);
        assertThat(p.path("identities")).hasSize(1);
        assertThat(p.path("identities").get(0).path("id").asText()).isEqualTo("hosp");
        assertThat(p.path("identities").get(0).path("orgShort").asText()).isEqualTo("示例市第一人民医院");
        assertThat(p.path("institutions")).isEmpty();
        assertThat(p.path("flows")).isEmpty();
        assertThat(p.path("matrix")).isEmpty();
        assertThat(p.path("matrixSummary").path("unpublished").asInt()).isZero();
        assertThat(p.path("drgs")).hasSize(1);
        assertThat(p.path("depts")).hasSize(1);
        assertThat(p.toString()).doesNotContain("某肛肠专科医院").doesNotContain("第二人民医院");
    }

    @Test
    void benchmarkDetachesAnonymousValuesFromPeerNames() throws Exception {
        ObjectNode p = read("""
                {'peers':['甲院','乙院','本院','丙院'], 'ownIndex':2,
                 'metrics':[{'name':'CMI','tier':'pct','values':[3,1,9,2]},
                            {'name':'次均','tier':'anon','values':[8,4,5,6]},
                            {'name':'质控率','tier':'named','values':[4,3,2,1]}],
                 'ranking':{'metric':'质控率'}}""");
        new HospitalBenchmarkScope().apply(p, hospital);
        assertThat(p.path("metrics").get(0).path("values").toString()).isEqualTo("[1,2,9,3]");
        assertThat(p.path("metrics").get(1).path("values").toString()).isEqualTo("[4,6,5,8]");
        assertThat(p.path("metrics").get(2).path("values").toString()).isEqualTo("[4,3,2,1]");
        assertThat(p.path("peers").get(0).asText()).isEqualTo("甲院");
        assertThat(p.path("ranking").path("metric").asText()).isEqualTo("质控率");
    }

    @Test
    void benchmarkWithoutNamedTierAnonymisesPeers() throws Exception {
        ObjectNode p = read("""
                {'peers':['甲院','本院','丙院'], 'ownIndex':1,
                 'metrics':[{'name':'CMI','tier':'pct','values':[3,1,2]}],
                 'ranking':{'metric':'CMI'}}""");
        new HospitalBenchmarkScope().apply(p, hospital);
        assertThat(p.path("peers").toString()).isEqualTo("[\"同级机构 A\",\"本院\",\"同级机构 B\"]");
        assertThat(p.path("ranking").path("metric").asText()).isEmpty();
    }
}
