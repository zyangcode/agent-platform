package com.ls.agent.core.experience.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.experience.api.ExperienceSkillService;
import com.ls.agent.core.experience.command.CreateExperienceSkillCommand;
import java.util.List;
import com.ls.agent.core.experience.command.UpdateExperienceSkillCommand;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.experience.entity.ExperienceSkillEntity;
import com.ls.agent.core.experience.mapper.ExperienceSkillMapper;
import com.ls.agent.core.identity.api.ApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultExperienceSkillService implements ExperienceSkillService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ExperienceSkillMapper mapper;
    private final ApplicationService applicationService;

    public DefaultExperienceSkillService(ExperienceSkillMapper mapper, ApplicationService applicationService) {
        this.mapper = mapper;
        this.applicationService = applicationService;
    }

    @Override
    @Transactional
    public ExperienceSkillDTO create(CreateExperienceSkillCommand command) {
        Long tenantId = requireNonNull(command.tenantId(), "tenantId");
        Long ownerUserId = requireNonNull(command.ownerUserId(), "ownerUserId");
        Long applicationId = requireNonNull(command.applicationId(), "applicationId");
        applicationService.ensureApplicationOwned(tenantId, ownerUserId, applicationId);

        ExperienceSkillEntity entity = new ExperienceSkillEntity();
        entity.setTenantId(tenantId);
        entity.setApplicationId(applicationId);
        entity.setUserId(ownerUserId);
        entity.setProfileId(command.profileId());
        applyEditableFields(entity, command.code(), command.name(), command.domain(), command.triggerKeywords(), command.content());
        entity.setStatus(STATUS_ACTIVE);
        mapper.insert(entity);
        return toDTO(entity);
    }

    @Override
    public PageResult<ExperienceSkillDTO> page(Long tenantId, Long ownerUserId, Long applicationId, int pageNo, int pageSize) {
        applicationService.ensureApplicationOwned(tenantId, ownerUserId, applicationId);
        Page<ExperienceSkillEntity> page = mapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<ExperienceSkillEntity>()
                        .eq(ExperienceSkillEntity::getTenantId, tenantId)
                        .eq(ExperienceSkillEntity::getApplicationId, applicationId)
                        .eq(ExperienceSkillEntity::getUserId, ownerUserId)
                        .orderByDesc(ExperienceSkillEntity::getUpdatedAt)
        );
        return PageResult.of(
                page.getRecords().stream().map(this::toDTO).toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    @Override
    @Transactional
    public ExperienceSkillDTO update(UpdateExperienceSkillCommand command) {
        ExperienceSkillEntity entity = getOwned(command.tenantId(), command.ownerUserId(), command.applicationId(), command.experienceSkillId());
        applyEditableFields(entity, command.code(), command.name(), command.domain(), command.triggerKeywords(), command.content());
        mapper.updateById(entity);
        return toDTO(entity);
    }

    @Override
    @Transactional
    public ExperienceSkillDTO disable(Long tenantId, Long ownerUserId, Long applicationId, Long experienceSkillId) {
        ExperienceSkillEntity entity = getOwned(tenantId, ownerUserId, applicationId, experienceSkillId);
        entity.setStatus(STATUS_DISABLED);
        mapper.updateById(entity);
        return toDTO(entity);
    }

    private ExperienceSkillEntity getOwned(Long tenantId, Long ownerUserId, Long applicationId, Long experienceSkillId) {
        applicationService.ensureApplicationOwned(tenantId, ownerUserId, applicationId);
        ExperienceSkillEntity entity = mapper.selectById(experienceSkillId);
        if (entity == null
                || !tenantId.equals(entity.getTenantId())
                || !applicationId.equals(entity.getApplicationId())
                || !ownerUserId.equals(entity.getUserId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "Experience skill is not accessible");
        }
        return entity;
    }

    private void applyEditableFields(
            ExperienceSkillEntity entity,
            String code,
            String name,
            String domain,
            List<String> triggerKeywords,
            String content
    ) {
        entity.setCode(requiredText(code, "code"));
        entity.setName(requiredText(name, "name"));
        entity.setDomain(blankToNull(domain));
        entity.setTriggerKeywords(normalizeKeywords(triggerKeywords));
        entity.setContent(requiredText(content, "content"));
    }

    private String[] normalizeKeywords(List<String> triggerKeywords) {
        if (triggerKeywords == null || triggerKeywords.isEmpty()) {
            return new String[0];
        }
        return triggerKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .distinct()
                .toArray(String[]::new);
    }

    private ExperienceSkillDTO toDTO(ExperienceSkillEntity entity) {
        String[] keywords = entity.getTriggerKeywords();
        return new ExperienceSkillDTO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDomain(),
                keywords == null ? List.of() : List.of(keywords),
                entity.getContent()
        );
    }

    private Long requireNonNull(Long value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is required");
        }
        return value;
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is required");
        }
        return value.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
