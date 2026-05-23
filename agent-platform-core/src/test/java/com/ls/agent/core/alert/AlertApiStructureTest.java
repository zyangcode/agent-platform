package com.ls.agent.core.alert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertApiStructureTest {

    private static final List<String> ALERT_API_TYPES = List.of(
            "com.ls.agent.core.alert.api.AlertEventService",
            "com.ls.agent.core.alert.command.RecordAlertEventCommand"
    );

    @Test
    void alertApiAndCommandTypesExist() throws ClassNotFoundException {
        for (String typeName : ALERT_API_TYPES) {
            assertThat(Class.forName(typeName))
                    .as(typeName)
                    .isNotNull();
        }
    }
}
