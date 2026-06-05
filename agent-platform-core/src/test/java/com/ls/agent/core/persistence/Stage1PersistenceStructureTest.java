package com.ls.agent.core.persistence;

import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.CreatedEntity;
import com.ls.agent.core.support.persistence.VersionedEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Stage1PersistenceStructureTest {

    private static final Map<String, Class<?>> ENTITY_SUPER_TYPES = Map.ofEntries(
            Map.entry("com.ls.agent.core.identity.entity.TenantEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.identity.entity.UserEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.identity.entity.RoleEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.identity.entity.UserRoleEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.identity.entity.ApplicationEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.identity.entity.ApiKeyEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.model.entity.ModelProviderEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.model.entity.ModelConfigEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.profile.entity.AgentProfileEntity", VersionedEntity.class),
            Map.entry("com.ls.agent.core.profile.entity.ProfileSkillEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.profile.entity.ProfileMcpToolEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.skill.entity.SkillEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.skill.entity.SkillVersionEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.skill.entity.SkillArtifactEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.mcp.entity.McpServerEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.mcp.entity.McpToolEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.agent.entity.ConversationEntity", BaseEntity.class),
            Map.entry("com.ls.agent.core.agent.entity.ConversationMessageEntity", CreatedEntity.class),
            Map.entry("com.ls.agent.core.memory.entity.MemoryEntity", BaseEntity.class)
    );

    private static final String[] MAPPER_NAMES = {
            "com.ls.agent.core.identity.mapper.TenantMapper",
            "com.ls.agent.core.identity.mapper.UserMapper",
            "com.ls.agent.core.identity.mapper.RoleMapper",
            "com.ls.agent.core.identity.mapper.UserRoleMapper",
            "com.ls.agent.core.identity.mapper.ApplicationMapper",
            "com.ls.agent.core.identity.mapper.ApiKeyMapper",
            "com.ls.agent.core.model.mapper.ModelProviderMapper",
            "com.ls.agent.core.model.mapper.ModelConfigMapper",
            "com.ls.agent.core.profile.mapper.AgentProfileMapper",
            "com.ls.agent.core.profile.mapper.ProfileSkillMapper",
            "com.ls.agent.core.profile.mapper.ProfileMcpToolMapper",
            "com.ls.agent.core.skill.mapper.SkillMapper",
            "com.ls.agent.core.skill.mapper.SkillVersionMapper",
            "com.ls.agent.core.skill.mapper.SkillArtifactMapper",
            "com.ls.agent.core.mcp.mapper.McpServerMapper",
            "com.ls.agent.core.mcp.mapper.McpToolMapper",
            "com.ls.agent.core.agent.mapper.ConversationMapper",
            "com.ls.agent.core.agent.mapper.ConversationMessageMapper",
            "com.ls.agent.core.memory.mapper.MemoryMapper"
    };

    @Test
    void stage1EntitiesExistAndUseTableMatchingBaseClasses() throws ClassNotFoundException {
        for (Map.Entry<String, Class<?>> entry : ENTITY_SUPER_TYPES.entrySet()) {
            Class<?> entityClass = Class.forName(entry.getKey());

            assertThat(entityClass.getSuperclass())
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void stage1MappersExist() throws ClassNotFoundException {
        for (String mapperName : MAPPER_NAMES) {
            assertThat(Class.forName(mapperName))
                    .as(mapperName)
                    .isInterface();
        }
    }

    @Test
    void appliedSeedMigrationKeepsOriginalSkillRowsAndLaterMigrationUpdatesRealHandlers() throws IOException {
        String seedSql = readMigration("db/migration/V005__init_seed_data.sql");
        String updateSql = readMigration("db/migration/V010__update_builtin_skill_real_handlers.sql");

        assertThat(seedSql).contains(
                "Mock weather query skill",
                "Mock search skill",
                "{\"handler\": \"mock:weather\"}",
                "{\"handler\": \"mock:search\"}"
        );
        assertThat(seedSql).doesNotContain("builtin:open-meteo-weather", "builtin:wikipedia-opensearch");

        assertThat(updateSql).contains(
                "Open-Meteo weather query skill",
                "Wikipedia OpenSearch skill",
                "builtin:open-meteo-weather",
                "builtin:wikipedia-opensearch"
        );
    }

    @Test
    void skillMigrationCreatesArtifactMetadataTable() throws IOException {
        String skillSql = readMigration("db/migration/V012__init_skill_artifacts.sql");

        assertThat(skillSql).contains(
                "create table skill_artifacts",
                "skill_version_id bigint not null references skill_versions (id)",
                "artifact_type varchar(32) not null",
                "storage_path varchar(512) not null",
                "checksum varchar(128) not null",
                "idx_skill_artifacts_version"
        );
    }

    @Test
    void mcpWeatherDemoMigrationRegistersBuiltinStdioServerAndTool() throws IOException {
        String weatherMcpSql = readMigration("db/migration/V017__init_demo_weather_mcp.sql");

        assertThat(weatherMcpSql).contains(
                "Bundled Weather MCP",
                "builtin-demo-weather-mcp",
                "weather.current",
                "Get current demo weather by city"
        );
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
