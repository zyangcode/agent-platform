package com.ls.agent.core.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityApiStructureTest {

    private static final List<String> SECURITY_API_TYPES = List.of(
            "com.ls.agent.core.security.api.SecurityEventService",
            "com.ls.agent.core.security.api.SensitiveDataScanner",
            "com.ls.agent.core.security.command.RecordSecurityEventCommand",
            "com.ls.agent.core.security.dto.SensitiveDataFindingDTO"
    );

    @Test
    void securityApiCommandAndDtoTypesExist() throws ClassNotFoundException {
        for (String typeName : SECURITY_API_TYPES) {
            assertThat(Class.forName(typeName))
                    .as(typeName)
                    .isNotNull();
        }
    }
}
