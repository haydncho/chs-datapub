package cn.ybdata.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HospitalScopeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Actor hospital = new Actor("李敏", 5L, "limin", "hospital", "H001", "示例市第一人民医院", 9L, "sid", Actor.Source.SESSION);
    private final Actor h002 = new Actor("钱某", 6L, "qa_h2", "hospital", "H002", "示例市第二人民医院", 10L, "sid", Actor.Source.SESSION);
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

    // ── B3 对标PK ──

    /** B3 seed shape: peers listed in metric order (the very ordering that used to leak) */
    private static final String B3 = """
            {'subtitle':'同级组', 'peers':['示例市妇幼保健院','示例市第二人民医院','示例市第一人民医院','示例市中医院','示例市第三人民医院','示例市肿瘤医院'],
             'ownIndex':2,
             'metrics':[{'name':'CMI值','tier':'pct','values':[1.31,1.18,1.12,1.06,0.98,0.91],'ownValue':'1.12'},
                        {'name':'次均费用(千元)','tier':'anon','values':[11.8,12.4,12.9,13.4,14.1,15.2],'ownValue':'12.9'},
                        {'name':'医保外费用占比(%)','tier':'anon','values':[3.1,4.2,6.8,5.0,5.6,7.4],'ownValue':'6.8%'},
                        {'name':'结算清单质控率(%)','tier':'named','values':[98.3,97.4,96.4,96.2,95.1,94.0],'ownValue':'96.4%'}],
             'ranking':{'metric':'结算清单质控率(%)'}}""";

    private ObjectNode b3(Actor who) throws Exception {
        ObjectNode p = read(B3);
        new HospitalBenchmarkScope().apply(p, who);
        return p;
    }

    /** the same B3 payload with two OTHER institutions' values swapped in every anonymous metric (a different truth) */
    private ObjectNode b3Swapped(Actor who, int a, int b) throws Exception {
        ObjectNode p = read(B3);
        for (JsonNode m : p.path("metrics")) {
            if ("named".equals(m.path("tier").asText())) continue;
            ArrayNode v = (ArrayNode) m.path("values");
            JsonNode x = v.get(a);
            v.set(a, v.get(b));
            v.set(b, x);
        }
        new HospitalBenchmarkScope().apply(p, who);
        return p;
    }

    private static String anonymousPart(ObjectNode p) {
        StringBuilder sb = new StringBuilder(p.path("peers").toString()).append(p.path("ownIndex"));
        for (JsonNode m : p.path("metrics")) if (!"named".equals(m.path("tier").asText())) sb.append(m);
        return sb.toString();
    }

    @Test
    void anonymousTiersCarryNoValueAlignedWithThePeerList() throws Exception {
        ObjectNode p = b3(hospital);
        for (JsonNode m : p.path("metrics")) {
            if ("named".equals(m.path("tier").asText())) {
                assertThat(m.path("values")).hasSize(p.path("peers").size());
                continue;
            }
            // no per-institution array at all — only 本院 and an unordered distribution of the others
            assertThat(m.has("values")).isFalse();
            assertThat(m.path("others")).hasSize(5);
            assertThat(m.path("own").isNumber()).isTrue();
        }
        assertThat(p.path("metrics").get(0).path("own").asDouble()).isEqualTo(1.12);
        assertThat(p.path("metrics").get(0).path("others").toString()).isEqualTo("[0.91,0.98,1.06,1.18,1.31]");
        // peers in a data-independent order (by name); 本院 follows the viewer's organisation
        assertThat(p.path("peers").get(p.path("ownIndex").asInt()).asText()).isEqualTo("示例市第一人民医院");
    }

    @Test
    void anonymousPayloadIsIdenticalWhicheverInstitutionHoldsWhichValue() throws Exception {
        // Swapping the true values of any two other institutions changes who-has-what but must not change
        // a single byte the hospital receives for the anonymous tiers: no list position can map a value to a name.
        String base = anonymousPart(b3(hospital));
        int[] others = {0, 1, 3, 4, 5};
        for (int a : others) {
            for (int b : others) {
                if (a >= b) continue;
                assertThat(anonymousPart(b3Swapped(hospital, a, b))).as("swap %d/%d", a, b).isEqualTo(base);
            }
        }
    }

    @Test
    void namedTierStaysAlignedWithTheReorderedPeers() throws Exception {
        ObjectNode p = b3(hospital);
        JsonNode named = p.path("metrics").get(3).path("values");
        JsonNode peers = p.path("peers");
        Map<String, Double> byName = new HashMap<>();
        for (int i = 0; i < peers.size(); i++) byName.put(peers.get(i).asText(), named.get(i).asDouble());
        assertThat(byName).containsEntry("示例市妇幼保健院", 98.3).containsEntry("示例市第一人民医院", 96.4)
                .containsEntry("示例市肿瘤医院", 94.0);
        assertThat(p.path("ranking").path("metric").asText()).isEqualTo("结算清单质控率(%)");
    }

    @Test
    void ownPositionFollowsTheViewersOrganisationNotTheStoredIndex() throws Exception {
        ObjectNode p = b3(h002);
        assertThat(p.path("peers").get(p.path("ownIndex").asInt()).asText()).isEqualTo("示例市第二人民医院");
        JsonNode cmi = p.path("metrics").get(0);
        assertThat(cmi.path("own").asDouble()).isEqualTo(1.18);
        assertThat(cmi.path("ownValue").asText()).isEqualTo("1.18");
        assertThat(p.path("metrics").get(2).path("ownValue").asText()).isEqualTo("4.2%");
        assertThat(cmi.path("others").toString()).contains("1.12").doesNotContain("1.18");
    }

    @Test
    void hospitalOutsideThePeerGroupGetsNoOwnPosition() throws Exception {
        Actor h010 = new Actor("王某", 7L, "qa_cty", "hospital", "H010", "甲县人民医院", 11L, "sid", Actor.Source.SESSION);
        ObjectNode p = b3(h010);
        assertThat(p.path("ownIndex").asInt()).isEqualTo(-1);
        assertThat(p.path("noOwnData").asBoolean()).isTrue();
        for (JsonNode m : p.path("metrics")) {
            assertThat(m.has("values")).isFalse();
            assertThat(m.path("ownValue").asText()).isEqualTo("—");
        }
        assertThat(p.path("ranking").path("metric").asText()).isEmpty();
        assertThat(p.toString()).doesNotContain("96.4").doesNotContain("1.12");
    }

    @Test
    void benchmarkWithoutNamedTierAnonymisesPeers() throws Exception {
        ObjectNode p = read("""
                {'peers':['甲院','本院','丙院'], 'ownIndex':1,
                 'metrics':[{'name':'CMI','tier':'pct','values':[3,1,2],'ownValue':'1'}],
                 'ranking':{'metric':'CMI'}}""");
        Actor own = new Actor("李敏", 5L, "limin", "hospital", "H001", "本院", 9L, "sid", Actor.Source.SESSION);
        new HospitalBenchmarkScope().apply(p, own);
        assertThat(p.path("peers").toString()).doesNotContain("甲院").doesNotContain("丙院").contains("本院");
        assertThat(p.path("ranking").path("metric").asText()).isEmpty();
    }

    // ── 全景图 peers ──

    private static final String COCKPIT = """
            {'identities':[{'id':'hosp','name':'李敏','org':'x','orgShort':'x','kpis':[{'label':'CMI'}],'eff':[],'errs':[],
                            'alerts':[{'text':'8月报告'}],'loop':[{'name':'签收'}],'money':{'budget':'94.9%','spend':'4,862万','balance':'−246.7万'},
                            'recipients':['李敏 · 医保办'],'todo':'签收 1'}],
             'institutions':[], 'flows':[], 'matrix':[],
             'hospRecorded':[452], 'hospPaid':[441], 'depts':[{'name':'神经内科'}],
             'peers':[{'name':'CMI 值','values':[1.31,1.18,1.12,1.06,0.98,0.91],'higherIsBetter':true,'value':'1.12','pct':'P68'},
                      {'name':'次均费用','values':[11800,12400,12860,13400,14100,15200],'higherIsBetter':false,'value':'12,860','pct':'P55'}],
             'peerSummary':{'better':'3 / 7 项','watch':'医保外费用占比','cmiPct':'P68','diffPct':'P45'}}""";

    @Test
    void cockpitPeersAreAnUnorderedDistributionAroundTheOwnSlot() throws Exception {
        ObjectNode p = read(COCKPIT);
        new HospitalCockpitScope().apply(p, hospital);
        JsonNode cmi = p.path("peers").get(0);
        assertThat(cmi.path("ownIndex").asInt()).isEqualTo(2);
        assertThat(cmi.path("values").get(2).asDouble()).isEqualTo(1.12);
        assertThat(cmi.path("others").toString()).isEqualTo("[0.91,0.98,1.06,1.18,1.31]");
        // the stored (B3-aligned) order is gone
        assertThat(cmi.path("values").toString()).isNotEqualTo("[1.31,1.18,1.12,1.06,0.98,0.91]");

        // swapping two other institutions' true values leaves the payload unchanged
        ObjectNode q = read(COCKPIT.replace("[1.31,1.18,1.12", "[1.18,1.31,1.12").replace("[11800,12400", "[12400,11800"));
        new HospitalCockpitScope().apply(q, hospital);
        assertThat(q.path("peers").toString()).isEqualTo(p.path("peers").toString());
    }

    @Test
    void cockpitForAnotherHospitalHasNoForeignOwnData() throws Exception {
        ObjectNode p = read(COCKPIT);
        new HospitalCockpitScope().apply(p, h002);
        JsonNode h = p.path("identities").get(0);
        assertThat(h.path("orgShort").asText()).isEqualTo("示例市第二人民医院");
        assertThat(h.path("kpis")).isEmpty();
        assertThat(h.path("alerts")).isEmpty();
        assertThat(h.path("loop")).isEmpty();
        assertThat(p.path("peers")).isEmpty();
        assertThat(p.path("depts")).isEmpty();
        assertThat(p.path("hospRecorded")).isEmpty();
        assertThat(p.path("noOwnData").asBoolean()).isTrue();
        assertThat(p.toString()).doesNotContain("李敏").doesNotContain("4,862").doesNotContain("P68").doesNotContain("神经内科");
    }

    // ── 本院具名 pages for another hospital ──

    @Test
    void ownDataPagesAreEmptiedForAHospitalWhoseDataIsNotStored() throws Exception {
        var f = new HospitalOwnDataScope();
        assertThat(f.appliesTo("B1", hospital)).isFalse();
        assertThat(f.appliesTo("B1", convener)).isFalse();
        assertThat(f.appliesTo("B1", h002)).isTrue();
        assertThat(f.appliesTo("B6", h002)).isFalse();

        ObjectNode b1 = read("""
                {'hospital':{'name':'示例市第一人民医院','subtitle':'x'},'todos':{'reportsToSign':1},
                 'kpis':[{'name':'CMI','value':'1.12'}],'settlement':{'billed':4862.4,'drgPaid':4615.7},
                 'lineage':{'version':'v2.1'},'defaultDrg':'BR25','drgs':[{'code':'BR25','cases':286}]}""");
        f.apply("B1", b1, h002);
        assertThat(b1.path("hospital").path("name").asText()).isEqualTo("示例市第二人民医院");
        assertThat(b1.path("kpis")).isEmpty();
        assertThat(b1.path("drgs")).isEmpty();
        assertThat(b1.path("noOwnData").asBoolean()).isTrue();
        assertThat(b1.path("lineage").path("version").asText()).isEqualTo("v2.1");
        assertThat(b1.toString()).doesNotContain("第一人民医院").doesNotContain("4862");

        ObjectNode b4 = read("""
                {'reports':[{'name':'8月报告'}],'issuer':'示例市医疗保障局','audience':'定向发布 · 示例市第一人民医院',
                 'coverKpis':[{'label':'本月偏离'}],'toc':[{'n':'一'}],'overview':{'text':'8 月本院 3,412 例'},
                 'signer':'李敏 医保办主任','checkNote':'x','correctionNote':'x','diff':{'rows':[{'item':'GG19'}]},'readLog':{'count':6,'last':'李敏'}}""");
        f.apply("B4", b4, h002);
        assertThat(b4.path("contents")).isEmpty(); // the list is released per institution by ReportScope
        assertThat(b4.path("issuer").asText()).isEqualTo("示例市医疗保障局");
        assertThat(b4.toString()).doesNotContain("第一人民医院").doesNotContain("李敏").doesNotContain("3,412");

        ObjectNode d1 = read("""
                {'hospital':'示例市第一人民医院','home':{'deviation':{'value':'−246.7'},'kpis':[{'label':'CMI'}]},
                 'todos':[{'id':'sign'}],'report':{'title':'8月'},'alert':{'title':'IU29'},'receiptCategories':['其他'],
                 'messages':[{'label':'x'}],'me':{'name':'李敏','org':'示例市第一人民医院 · 本院具名','rows':[{'k':'签收记录','v':'x'}]}}""");
        f.apply("D1", d1, h002);
        assertThat(d1.path("todos")).isEmpty();
        assertThat(d1.path("receiptCategories")).hasSize(1);
        assertThat(d1.toString()).doesNotContain("第一人民医院").doesNotContain("李敏").doesNotContain("246.7").doesNotContain("IU29");

        Map<String, String> pages = Map.of(
                "B2", "{'basis':'口径','groups':[{'code':'BR25','teams':[{'name':'神经内科一组'}]}]}",
                "B5", "{'draft':'BR25 专题','subtitle':'截止','remainingDays':2,'items':[{'id':'cases','value':'286 例'}]}",
                "B7", "{'subtitle':'2026年8月','kpis':[{'value':'506 人次'}],'sources':[{'name':'甲县'}],'drgs':[{'code':'BR25'}]}");
        for (var e : pages.entrySet()) {
            ObjectNode x = read(e.getValue());
            f.apply(e.getKey(), x, h002);
            assertThat(x.path("noOwnData").asBoolean()).isTrue();
            assertThat(x.toString()).as(e.getKey()).doesNotContain("BR25").doesNotContain("286").doesNotContain("506");
        }
    }
}
