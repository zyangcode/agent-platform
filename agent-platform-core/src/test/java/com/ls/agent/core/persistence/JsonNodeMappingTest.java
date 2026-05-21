package com.ls.agent.core.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonNodeMappingTest {

    private static final List<Class<?>> JSON_NODE_ENTITIES = List.of(
            com.ls.agent.core.agent.entity.ConversationEntity.class,
            com.ls.agent.core.agent.entity.ConversationMessageEntity.class,
            com.ls.agent.core.model.entity.ModelConfigEntity.class,
            com.ls.agent.core.profile.entity.AgentProfileEntity.class,
            com.ls.agent.core.profile.entity.ProfileMcpToolEntity.class,
            com.ls.agent.core.profile.entity.ProfileSkillEntity.class,
            com.ls.agent.core.skill.entity.SkillEntity.class,
            com.ls.agent.core.skill.entity.SkillVersionEntity.class,
            com.ls.agent.core.mcp.entity.McpServerEntity.class,
            com.ls.agent.core.mcp.entity.McpToolEntity.class
    );

    @Test
    void jsonNodeEntitiesEnableAutoResultMap() {
        List<String> missingAutoResultMap = JSON_NODE_ENTITIES.stream()
                .filter(entityClass -> {
                    TableName tableName = entityClass.getAnnotation(TableName.class);
                    return tableName == null || !tableName.autoResultMap();
                })
                .map(Class::getName)
                .toList();

        assertThat(missingAutoResultMap).isEmpty();
    }

    @Test
    void jsonNodeFieldsDeclareJsonNodeTypeHandler() {
        List<String> missingTypeHandler = new ArrayList<>();

        for (Class<?> entityClass : JSON_NODE_ENTITIES) {
            for (Field field : entityClass.getDeclaredFields()) {
                if (!JsonNode.class.equals(field.getType())) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField == null || !JsonNodeTypeHandler.class.equals(tableField.typeHandler())) {
                    missingTypeHandler.add(entityClass.getSimpleName() + "." + field.getName());
                }
            }
        }

        assertThat(missingTypeHandler).isEmpty();
    }
}
