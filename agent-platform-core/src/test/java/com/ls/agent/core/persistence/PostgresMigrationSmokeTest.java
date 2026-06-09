package com.ls.agent.core.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.quota.entity.QuotaUsageEntity;
import com.ls.agent.core.quota.mapper.QuotaUsageMapper;
import com.ls.agent.core.rag.entity.KnowledgeChunkEntity;
import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import com.ls.agent.core.support.persistence.MybatisPlusConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationsCreateSearchVectorsAndMappersCanRunFullTextSearchOnRealPostgres() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();

            DataSource dataSource = context.getBean(DataSource.class);
            seedFullTextSearchData(dataSource);

            assertSearchColumnTriggerAndIndexExist(dataSource, "memories",
                    "trg_memories_search_vector", "idx_memories_search_vector");
            assertSearchColumnTriggerAndIndexExist(dataSource, "knowledge_chunks",
                    "trg_knowledge_chunks_search_vector", "idx_knowledge_chunks_search_vector");
            assertSearchVectorContains(dataSource, "memories", 9001L, "kotlin");
            assertSearchVectorContains(dataSource, "knowledge_chunks", 9101L, "vector");

            MemoryMapper memoryMapper = context.getBean(MemoryMapper.class);
            List<MemoryEntity> memories = memoryMapper.searchActiveMemories(
                    1L, 9001L, 1L, 9001L, List.of("kotlin"), "kotlin preference", 10);

            assertThat(memories)
                    .extracting(MemoryEntity::getId)
                    .contains(9001L, 9002L);
            assertThat(memories)
                    .filteredOn(memory -> memory.getId().equals(9001L))
                    .singleElement()
                    .extracting(MemoryEntity::getKeywordScore)
                    .isNotNull();

            KnowledgeChunkMapper chunkMapper = context.getBean(KnowledgeChunkMapper.class);
            List<KnowledgeChunkEntity> chunks = chunkMapper.searchActiveChunks(
                    1L, 9001L, 1L, 9001L, List.of("vector"), "vector recall", 10);

            assertThat(chunks)
                    .extracting(KnowledgeChunkEntity::getId)
                    .contains(9101L, 9102L);
            assertThat(chunks)
                    .filteredOn(chunk -> chunk.getId().equals(9101L))
                    .singleElement()
                    .satisfies(chunk -> {
                        assertThat(chunk.getTitle()).isEqualTo("Profile RAG Guide");
                        assertThat(chunk.getSourceUri()).isEqualTo("file://profile-rag.md");
                        assertThat(chunk.getKeywordScore()).isPositive();
                    });

            List<KnowledgeChunkEntity> chineseChunks = chunkMapper.searchActiveChunks(
                    1L, 9001L, 1L, 9001L, List.of("长期", "记忆"), "长期 记忆", 10);

            assertThat(chineseChunks)
                    .extracting(KnowledgeChunkEntity::getId)
                    .contains(9103L);
            assertThat(chineseChunks)
                    .filteredOn(chunk -> chunk.getId().equals(9103L))
                    .singleElement()
                    .satisfies(chunk -> {
                        assertThat(chunk.getTitle()).isEqualTo("中文 RAG 指南");
                        assertThat(chunk.getKeywordScore()).isPositive();
                    });
        });
    }

    @Test
    void quotaUsageMigrationAndMapperEnforcePeriodLimitOnRealPostgres() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();

            DataSource dataSource = context.getBean(DataSource.class);
            seedQuotaUsageData(dataSource);

            QuotaUsageMapper quotaUsageMapper = context.getBean(QuotaUsageMapper.class);
            QuotaUsageEntity dailyUsage = quotaUsage("APPLICATION", 9901L, "DAY", "20260609", 700L);
            QuotaUsageEntity monthlyUsage = quotaUsage("APPLICATION", 9901L, "MONTH", "202606", 700L);

            assertThat(quotaUsageMapper.reserveTokens(dailyUsage, monthlyUsage, 1000L, 2000L)).isEqualTo(1);
            assertThat(quotaUsageMapper.reserveTokens(dailyUsage, monthlyUsage, 1000L, 2000L)).isZero();

            quotaUsageMapper.adjustReservedTokens(1L, "APPLICATION", 9901L, "20260609", "202606", -700L);
            assertThat(quotaUsageMapper.reserveTokens(dailyUsage, monthlyUsage, 1000L, 2000L)).isEqualTo(1);
        });
    }

    private void seedFullTextSearchData(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into applications (id, tenant_id, owner_user_id, name, description, status)
                    values (9001, 1, 1, 'Smoke App', 'PostgreSQL migration smoke app', 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    insert into agent_profiles (
                        id, tenant_id, owner_user_id, application_id, name, profile_type,
                        description, model_config_id, prompt_extra, memory_strategy,
                        max_steps, visibility, status
                    )
                    values (
                        9001, 1, 1, 9001, 'Smoke Profile', 'ASSISTANT',
                        'Profile for migration smoke test', 1, null,
                        '{"mode": "READ_WRITE"}'::jsonb, 6, 'PRIVATE', 'ACTIVE'
                    )
                    """);
            statement.executeUpdate("""
                    insert into memories (
                        id, tenant_id, user_id, application_id, profile_id,
                        memory_type, memory_category, content, keywords, tags,
                        importance, confidence, status, metadata
                    )
                    values (
                        9001, 1, 1, 9001, 9001,
                        'PREFERENCE', 'preference', 'User prefers kotlin for backend agents.',
                        array['kotlin', 'backend'], array['kotlin', 'agent'],
                        0.900, 0.950, 'ACTIVE', '{}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into memories (
                        id, tenant_id, user_id, application_id, profile_id,
                        memory_type, memory_category, content, keywords, tags,
                        importance, confidence, status, metadata
                    )
                    values (
                        9002, 1, 1, null, null,
                        'FACT', 'fact', 'Global memory mentions kotlin preference for recall fallback.',
                        array['kotlin'], array['global'],
                        0.500, 0.900, 'ACTIVE', '{}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into knowledge_documents (
                        id, tenant_id, application_id, profile_id, owner_user_id,
                        title, source_type, source_uri, doc_hash, status, metadata
                    )
                    values (
                        9101, 1, 9001, 9001, 1,
                        'Profile RAG Guide', 'TEXT', 'file://profile-rag.md',
                        'profile-rag-hash', 'INDEXED', '{}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into knowledge_documents (
                        id, tenant_id, application_id, profile_id, owner_user_id,
                        title, source_type, source_uri, doc_hash, status, metadata
                    )
                    values (
                        9102, 1, 9001, null, 1,
                        'Global RAG Guide', 'TEXT', 'file://global-rag.md',
                        'global-rag-hash', 'INDEXED', '{}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into knowledge_documents (
                        id, tenant_id, application_id, profile_id, owner_user_id,
                        title, source_type, source_uri, doc_hash, status, metadata
                    )
                    values (
                        9103, 1, 9001, 9001, 1,
                        '中文 RAG 指南', 'TEXT', 'file://cn-rag.md',
                        'cn-rag-hash', 'INDEXED', '{}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into knowledge_chunks (
                        id, tenant_id, application_id, document_id, chunk_index,
                        content, content_hash, token_count, vector_id, status, metadata
                    )
                    values (
                        9101, 1, 9001, 9101, 0,
                        'Vector recall should use trigger populated search_vector.',
                        'chunk-profile-hash', 12, 'chunk-profile-vector', 'ACTIVE',
                        '{"documentTitle": "Profile RAG Guide", "headingPath": "Memory Search"}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into knowledge_chunks (
                        id, tenant_id, application_id, document_id, chunk_index,
                        content, content_hash, token_count, vector_id, status, metadata
                    )
                    values (
                        9102, 1, 9001, 9102, 0,
                        'Global vector recall document should remain visible to a profile scoped query.',
                        'chunk-global-hash', 10, 'chunk-global-vector', 'ACTIVE',
                        '{"documentTitle": "Global RAG Guide", "headingPath": "Shared Search"}'::jsonb
                    )
                    """);
            statement.executeUpdate("""
                    insert into knowledge_chunks (
                        id, tenant_id, application_id, document_id, chunk_index,
                        content, content_hash, token_count, vector_id, status, metadata
                    )
                    values (
                        9103, 1, 9001, 9103, 0,
                        '记忆系统支持长期记忆、RAG 检索和中文关键词召回。',
                        'chunk-cn-hash', 18, null, 'ACTIVE',
                        '{"documentTitle": "中文 RAG 指南", "headingPath": "记忆系统"}'::jsonb
                    )
                    """);
        }
    }

    private void seedQuotaUsageData(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into applications (id, tenant_id, owner_user_id, name, description, status)
                    values (9901, 1, 1, 'Quota Smoke App', 'PostgreSQL quota smoke app', 'ACTIVE')
                    """);
        }
    }

    private QuotaUsageEntity quotaUsage(
            String subjectType,
            Long subjectId,
            String periodType,
            String periodKey,
            Long tokens
    ) {
        QuotaUsageEntity usage = new QuotaUsageEntity();
        usage.setTenantId(1L);
        usage.setSubjectType(subjectType);
        usage.setSubjectId(subjectId);
        usage.setPeriodType(periodType);
        usage.setPeriodKey(periodKey);
        usage.setReservedTokens(tokens);
        return usage;
    }

    private void assertSearchColumnTriggerAndIndexExist(
            DataSource dataSource,
            String tableName,
            String triggerName,
            String indexName
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(exists(connection, """
                    select 1
                    from information_schema.columns
                    where table_name = ?
                      and column_name = 'search_vector'
                      and udt_name = 'tsvector'
                    """, tableName)).as(tableName + ".search_vector").isTrue();
            assertThat(exists(connection, """
                    select 1
                    from information_schema.triggers
                    where event_object_table = ?
                      and trigger_name = ?
                    """, tableName, triggerName)).as(triggerName).isTrue();
            assertThat(exists(connection, """
                    select 1
                    from pg_indexes
                    where tablename = ?
                      and indexname = ?
                      and indexdef ilike '% using gin %'
                    """, tableName, indexName)).as(indexName).isTrue();
        }
    }

    private void assertSearchVectorContains(
            DataSource dataSource,
            String tableName,
            Long id,
            String queryText
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select search_vector @@ websearch_to_tsquery('simple', ?) as matched
                     from %s
                     where id = ?
                     """.formatted(tableName))) {
            statement.setString(1, queryText);
            statement.setLong(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean("matched")).isTrue();
            }
        }
    }

    private boolean exists(Connection connection, String sql, String... params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        FlywayAutoConfiguration.class,
                        MybatisPlusAutoConfiguration.class,
                        SqlInitializationAutoConfiguration.class
                ))
                .withUserConfiguration(MybatisPlusConfiguration.class, MapperTestConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.flyway.locations=classpath:db/migration",
                        "spring.sql.init.mode=never"
                );
    }

    @TestConfiguration
    static class MapperTestConfiguration {
    }
}
