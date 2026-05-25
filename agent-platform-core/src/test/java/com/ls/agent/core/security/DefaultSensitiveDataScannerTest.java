package com.ls.agent.core.security;

import com.ls.agent.core.security.application.DefaultSensitiveDataScanner;
import com.ls.agent.core.security.dto.SensitiveDataFindingDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSensitiveDataScannerTest {

    private final DefaultSensitiveDataScanner scanner = new DefaultSensitiveDataScanner();

    @Test
    void scanFindsPhoneEmailIdCardAndApiKeyWithMaskedSampleAndHash() {
        List<SensitiveDataFindingDTO> findings = scanner.scan("""
                phone 13812345678
                email alice@example.com
                id 11010119900307123X
                key sk-abcdefghijklmnopqrstuvwxyz123456
                """, "REQUEST_MESSAGE");

        assertThat(findings).extracting(SensitiveDataFindingDTO::eventType)
                .contains("PHONE", "EMAIL", "ID_CARD", "API_KEY_PATTERN");
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.location()).isEqualTo("REQUEST_MESSAGE");
            assertThat(finding.sourceTextHash()).hasSize(64);
            assertThat(finding.maskedSample()).doesNotContain("13812345678", "alice@example.com", "11010119900307123X");
        });
    }

    @Test
    void scanFindsSensitiveDataAdjacentToChineseCharacters() {
        List<SensitiveDataFindingDTO> findings = scanner.scan("我的手机号是13812345678邮箱是test@abc.com", "REQUEST_MESSAGE");

        assertThat(findings).extracting(SensitiveDataFindingDTO::eventType)
                .contains("PHONE", "EMAIL");
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.location()).isEqualTo("REQUEST_MESSAGE");
            assertThat(finding.sourceTextHash()).hasSize(64);
        });
    }

    @Test
    void scanReturnsEmptyListWhenTextHasNoSensitiveData() {
        List<SensitiveDataFindingDTO> findings = scanner.scan("hello normal request", "REQUEST_MESSAGE");

        assertThat(findings).isEmpty();
    }
}
