package com.ls.agent.core.persistence;

import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.CreatedEntity;
import com.ls.agent.core.support.persistence.VersionedEntity;
import org.junit.jupiter.api.Test;

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
}
