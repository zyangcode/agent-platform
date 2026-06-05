package com.ls.agent.core.skill;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.skill.application.DefaultSkillQueryService;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.skill.entity.SkillEntity;
import com.ls.agent.core.skill.entity.SkillVersionEntity;
import com.ls.agent.core.skill.mapper.SkillMapper;
import com.ls.agent.core.skill.mapper.SkillVersionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSkillQueryServiceTest {

    private final SkillMapper skillMapper = mock(SkillMapper.class);
    private final SkillVersionMapper skillVersionMapper = mock(SkillVersionMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultSkillQueryService service = new DefaultSkillQueryService(skillMapper, skillVersionMapper);

    @Test
    void listSkillsReturnsEnabledGlobalSkillsWithParameterSchema() {
        SkillEntity calculator = skill(1L, "calculator", "Calculator", "GLOBAL", "ENABLED", 11L);
        SkillVersionEntity version = version(11L);
        when(skillMapper.selectList(any(Wrapper.class))).thenReturn(List.of(calculator));
        when(skillVersionMapper.selectBatchIds(List.of(11L))).thenReturn(List.of(version));

        List<SkillDTO> result = service.listSkills(1L, "GLOBAL", "ENABLED");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skillId()).isEqualTo(1L);
        assertThat(result.get(0).code()).isEqualTo("calculator");
        assertThat(result.get(0).parameterSchema().get("properties").has("expression")).isTrue();
    }

    @Test
    void listAvailableSkillsWithExplicitEmptyIdsReturnsNoSkills() {
        List<SkillDTO> result = service.listAvailableSkills(1L, List.of());

        assertThat(result).isEmpty();
        verify(skillMapper, never()).selectList(any(Wrapper.class));
        verify(skillVersionMapper, never()).selectBatchIds(any());
    }

    private SkillEntity skill(Long id, String code, String name, String scope, String status, Long versionId) {
        SkillEntity entity = new SkillEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription("Built-in skill");
        entity.setSkillType("BUILTIN");
        entity.setScope(scope);
        entity.setStatus(status);
        entity.setCurrentVersionId(versionId);
        return entity;
    }

    private SkillVersionEntity version(Long id) {
        SkillVersionEntity entity = new SkillVersionEntity();
        entity.setId(id);
        entity.setSkillId(1L);
        entity.setVersion("1.0.0");
        entity.setParameterSchema(objectMapper.createObjectNode()
                .put("type", "object")
                .set("properties", objectMapper.createObjectNode()
                        .set("expression", objectMapper.createObjectNode().put("type", "string"))));
        entity.setStatus("READY");
        return entity;
    }
}
