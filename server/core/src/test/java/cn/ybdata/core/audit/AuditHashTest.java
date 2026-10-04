package cn.ybdata.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditHashTest {

    @Test
    void sha256IsHexAndStable() {
        assertThat(AuditService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(AuditService.GENESIS).hasSize(64).matches("0+");
    }
}
