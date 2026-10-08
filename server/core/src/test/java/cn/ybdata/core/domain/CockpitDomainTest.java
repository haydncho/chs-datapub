package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CockpitDomainTest {

    private final ObjectMapper json = new ObjectMapper();

    private static Actor actor(String login, String role, String org) {
        return new Actor("某人", 1L, login, role, org, null, 1L, "s", Actor.Source.SESSION);
    }

    private JsonNode body(String s) throws Exception {
        return json.readTree(s);
    }

    @Test
    void hospitalMayOnlyUseOwnViewAndBureauOnlyConv() {
        assertThat(CockpitDomain.mayUse(actor("limin", "hospital", "H001"), "hosp")).isTrue();
        assertThat(CockpitDomain.mayUse(actor("limin", "hospital", "H001"), "conv")).isFalse();
        assertThat(CockpitDomain.mayUse(actor("lihua", "admin", "YBJ"), "conv")).isTrue();
        assertThat(CockpitDomain.mayUse(actor("lihua", "admin", "YBJ"), "hosp")).isFalse();
        assertThat(CockpitDomain.mayUse(Actor.devDefault(null), "hosp")).isTrue();
        assertThat(CockpitDomain.mayUse(null, "conv")).isTrue();
    }

    @Test
    void identityIsValidatedAgainstActor() throws Exception {
        Actor hosp = actor("limin", "hospital", "H001");
        assertThat(CockpitDomain.identityOf(body("{\"identity\":\"hosp\"}"), hosp)).isEqualTo("hosp");
        assertThatThrownBy(() -> CockpitDomain.identityOf(body("{\"identity\":\"conv\"}"), hosp)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> CockpitDomain.identityOf(body("{\"identity\":\"x\"}"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CockpitDomain.identityOf(body("{}"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerIsLoginOrDevName() {
        assertThat(CockpitDomain.ownerOf(actor("chenzy", "convener", "YBJ"))).isEqualTo("chenzy");
        assertThat(CockpitDomain.ownerOf(Actor.devDefault("测试"))).isEqualTo("dev:测试");
        assertThat(CockpitDomain.ownerOf(null)).isEqualTo("dev:陈志远");
    }

    @Test
    void ackScopeIsSharedByBureauAndPerHospital() {
        assertThat(CockpitDomain.ackScope(actor("chenzy", "convener", "YBJ"), "conv")).isEqualTo("bureau");
        assertThat(CockpitDomain.ackScope(actor("lihua", "admin", "YBJ"), "conv")).isEqualTo("bureau");
        assertThat(CockpitDomain.ackScope(actor("cty", "county", "C01"), "conv")).isEqualTo("county:C01");
        assertThat(CockpitDomain.ackScope(actor("limin", "hospital", "H001"), "hosp")).isEqualTo("org:H001");
        assertThat(CockpitDomain.ackScope(actor("h2", "hospital", "H002"), "hosp")).isEqualTo("org:H002");
        assertThat(CockpitDomain.ackScope(null, "hosp")).isEqualTo("org:H001");
    }

    @Test
    void pickManyRequiresAtLeastOneKnownValueAndKeepsOptionOrder() throws Exception {
        List<String> opts = List.of("大屏快照", "核心指标摘要", "告警汇总");
        assertThat(CockpitDomain.pickMany(body("{\"c\":[\"告警汇总\",\"大屏快照\",\"告警汇总\"]}"), "c", opts, "空", "内容"))
                .containsExactly("大屏快照", "告警汇总");
        assertThatThrownBy(() -> CockpitDomain.pickMany(body("{\"c\":[]}"), "c", opts, "请至少选择 1 项推送内容", "内容"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("请至少选择 1 项推送内容");
        assertThatThrownBy(() -> CockpitDomain.pickMany(body("{}"), "c", opts, "空", "内容"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("空");
        assertThatThrownBy(() -> CockpitDomain.pickMany(body("{\"c\":[\"别的\"]}"), "c", opts, "空", "内容"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持的内容");
        assertThatThrownBy(() -> CockpitDomain.pickMany(body("{\"c\":\"大屏快照\"}"), "c", opts, "空", "内容"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pickOneMustBeAnOption() throws Exception {
        assertThat(CockpitDomain.pickOne(body("{\"f\":\"邮件\"}"), "f", List.of("政务微信", "邮件"), "渠道")).isEqualTo("邮件");
        assertThatThrownBy(() -> CockpitDomain.pickOne(body("{\"f\":\"传真\"}"), "f", List.of("政务微信", "邮件"), "渠道"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
