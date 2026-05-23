package com.ls.agent.core.persistence;

import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.CreatedEntity;
import com.ls.agent.core.support.persistence.VersionedEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Stage2PersistenceStructureTest {

    private static final Map<String, Class<?>> TRACE_ENTITY_SUPER_TYPES = Map.of(
            "com.ls.agent.core.trace.entity.TraceRootEntity", BaseEntity.class,
            "com.ls.agent.core.trace.entity.TraceSpanEntity", CreatedEntity.class
    );

    private static final Map<String, Class<?>> QUOTA_ENTITY_SUPER_TYPES = Map.of(
            "com.ls.agent.core.quota.entity.QuotaConfigEntity", VersionedEntity.class,
            "com.ls.agent.core.quota.entity.QuotaReservationEntity", VersionedEntity.class,
            "com.ls.agent.core.quota.entity.TokenUsageLogEntity", CreatedEntity.class
    );

    private static final Map<String, Class<?>> SECURITY_ENTITY_SUPER_TYPES = Map.of(
            "com.ls.agent.core.security.entity.SecurityEventEntity", CreatedEntity.class
    );

    private static final String[] TRACE_MAPPER_NAMES = {
            "com.ls.agent.core.trace.mapper.TraceRootMapper",
            "com.ls.agent.core.trace.mapper.TraceSpanMapper"
    };

    private static final String[] QUOTA_MAPPER_NAMES = {
            "com.ls.agent.core.quota.mapper.QuotaConfigMapper",
            "com.ls.agent.core.quota.mapper.QuotaReservationMapper",
            "com.ls.agent.core.quota.mapper.TokenUsageLogMapper"
    };

    private static final String[] SECURITY_MAPPER_NAMES = {
            "com.ls.agent.core.security.mapper.SecurityEventMapper"
    };

    @Test
    void traceMigrationCreatesRootSpanAndTokenUsageTables() throws IOException {
        String sql = readMigration("db/migration/V006__init_trace.sql");

        assertThat(sql).contains(
                "create table trace_roots",
                "create table trace_spans",
                "create table token_usage_logs",
                "trace_id varchar(64) not null unique",
                "trace_id varchar(64) not null references trace_roots (trace_id)",
                "metadata jsonb not null default '{}'::jsonb",
                "attributes jsonb not null default '{}'::jsonb",
                "idx_trace_roots_tenant_started",
                "idx_trace_spans_trace_started",
                "idx_token_usage_trace"
        );
    }

    @Test
    void quotaMigrationCreatesConfigAndReservationTables() throws IOException {
        String sql = readMigration("db/migration/V007__init_quota_reservation.sql");

        assertThat(sql).contains(
                "create table quota_configs",
                "create table quota_reservations",
                "unique (tenant_id, subject_type, subject_id)",
                "trace_id varchar(64) not null unique",
                "version int not null default 0",
                "idx_quota_configs_subject",
                "idx_quota_reservations_trace",
                "idx_quota_reservations_status"
        );
    }

    @Test
    void securityMigrationCreatesSecurityEventsTableWithoutRawSensitiveText() throws IOException {
        String sql = readMigration("db/migration/V008__init_security_events.sql");

        assertThat(sql).contains(
                "create table security_events",
                "event_type varchar(64) not null",
                "location varchar(64) not null",
                "source_text_hash varchar(64) not null",
                "masked_sample text not null",
                "action varchar(32) not null",
                "idx_security_events_trace",
                "idx_security_events_tenant_created"
        );
        assertThat(sql).doesNotContain("source_text text", "raw_text", "plain_text");
    }

    @Test
    void stage2TraceEntitiesExistAndUseExpectedBaseClasses() throws ClassNotFoundException {
        for (Map.Entry<String, Class<?>> entry : TRACE_ENTITY_SUPER_TYPES.entrySet()) {
            Class<?> entityClass = Class.forName(entry.getKey());

            assertThat(entityClass.getSuperclass())
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void stage2QuotaEntitiesExistAndUseExpectedBaseClasses() throws ClassNotFoundException {
        for (Map.Entry<String, Class<?>> entry : QUOTA_ENTITY_SUPER_TYPES.entrySet()) {
            Class<?> entityClass = Class.forName(entry.getKey());

            assertThat(entityClass.getSuperclass())
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void stage2SecurityEntitiesExistAndUseExpectedBaseClasses() throws ClassNotFoundException {
        for (Map.Entry<String, Class<?>> entry : SECURITY_ENTITY_SUPER_TYPES.entrySet()) {
            Class<?> entityClass = Class.forName(entry.getKey());

            assertThat(entityClass.getSuperclass())
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void stage2TraceMappersExist() throws ClassNotFoundException {
        for (String mapperName : TRACE_MAPPER_NAMES) {
            assertThat(Class.forName(mapperName))
                    .as(mapperName)
                    .isInterface();
        }
    }

    @Test
    void stage2QuotaMappersExist() throws ClassNotFoundException {
        for (String mapperName : QUOTA_MAPPER_NAMES) {
            assertThat(Class.forName(mapperName))
                    .as(mapperName)
                    .isInterface();
        }
    }

    @Test
    void stage2SecurityMappersExist() throws ClassNotFoundException {
        for (String mapperName : SECURITY_MAPPER_NAMES) {
            assertThat(Class.forName(mapperName))
                    .as(mapperName)
                    .isInterface();
        }
    }

    private String readMigration(String resourcePath) throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream)
                    .as(resourcePath + " should exist")
                    .isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
