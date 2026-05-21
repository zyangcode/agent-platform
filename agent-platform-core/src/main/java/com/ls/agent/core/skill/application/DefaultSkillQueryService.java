package com.ls.agent.core.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.skill.entity.SkillEntity;
import com.ls.agent.core.skill.entity.SkillVersionEntity;
import com.ls.agent.core.skill.mapper.SkillMapper;
import com.ls.agent.core.skill.mapper.SkillVersionMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DefaultSkillQueryService implements SkillQueryService, SkillRegistry {

    private static final String STATUS_ENABLED = "ENABLED";

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;

    public DefaultSkillQueryService(SkillMapper skillMapper, SkillVersionMapper skillVersionMapper) {
        this.skillMapper = skillMapper;
        this.skillVersionMapper = skillVersionMapper;
    }

    @Override
    public boolean areSkillsBindable(Long tenantId, List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return true;
        }
        Long count = skillMapper.selectCount(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .eq(SkillEntity::getStatus, STATUS_ENABLED)
                .in(SkillEntity::getId, skillIds));
        return count == skillIds.stream().distinct().count();
    }

    @Override
    public List<SkillDTO> listSkills(Long tenantId, String scope, String status) {
        List<SkillEntity> skills = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .eq(scope != null && !scope.isBlank(), SkillEntity::getScope, scope)
                .eq(status != null && !status.isBlank(), SkillEntity::getStatus, status)
                .orderByAsc(SkillEntity::getId));
        return toDTOs(skills);
    }

    @Override
    public List<SkillDTO> listAvailableSkills(Long tenantId, List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return listSkills(tenantId, "GLOBAL", STATUS_ENABLED);
        }
        List<SkillEntity> skills = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .eq(SkillEntity::getStatus, STATUS_ENABLED)
                .in(SkillEntity::getId, skillIds)
                .orderByAsc(SkillEntity::getId));
        return toDTOs(skills);
    }

    private List<SkillDTO> toDTOs(List<SkillEntity> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<Long> versionIds = skills.stream()
                .map(SkillEntity::getCurrentVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersionEntity> versions = versionIds.isEmpty()
                ? Collections.emptyMap()
                : skillVersionMapper.selectBatchIds(versionIds).stream()
                        .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        return skills.stream()
                .map(skill -> {
                    SkillVersionEntity version = versions.get(skill.getCurrentVersionId());
                    return new SkillDTO(
                            skill.getId(),
                            skill.getCode(),
                            skill.getName(),
                            skill.getDescription(),
                            skill.getSkillType(),
                            skill.getScope(),
                            skill.getStatus(),
                            version == null ? null : version.getParameterSchema()
                    );
                })
                .toList();
    }
}
