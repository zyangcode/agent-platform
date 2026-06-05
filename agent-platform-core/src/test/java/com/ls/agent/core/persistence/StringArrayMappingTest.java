package com.ls.agent.core.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.StringArrayTypeHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StringArrayMappingTest {

    private static final List<Class<?>> STRING_ARRAY_ENTITIES = Arrays.asList(
            com.ls.agent.core.experience.entity.ExperienceSkillEntity.class,
            com.ls.agent.core.memory.entity.MemoryEntity.class
    );

    @Test
    void stringArrayEntitiesEnableAutoResultMap() {
        List<String> missingAutoResultMap = STRING_ARRAY_ENTITIES.stream()
                .filter(entityClass -> {
                    TableName tableName = entityClass.getAnnotation(TableName.class);
                    return tableName == null || !tableName.autoResultMap();
                })
                .map(Class::getName)
                .toList();

        assertThat(missingAutoResultMap).isEmpty();
    }

    @Test
    void stringArrayFieldsDeclareStringArrayTypeHandler() {
        List<String> missingTypeHandler = new ArrayList<>();

        for (Class<?> entityClass : STRING_ARRAY_ENTITIES) {
            for (Field field : entityClass.getDeclaredFields()) {
                if (!String[].class.equals(field.getType())) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField == null || !StringArrayTypeHandler.class.equals(tableField.typeHandler())) {
                    missingTypeHandler.add(entityClass.getSimpleName() + "." + field.getName());
                }
            }
        }

        assertThat(missingTypeHandler).isEmpty();
    }
}
