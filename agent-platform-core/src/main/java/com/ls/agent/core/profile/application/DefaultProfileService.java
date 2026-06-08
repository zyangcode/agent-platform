package com.ls.agent.core.profile.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.command.BindMcpToolsCommand;
import com.ls.agent.core.profile.command.BindSkillsCommand;
import com.ls.agent.core.profile.command.CreateProfileCommand;
import com.ls.agent.core.profile.command.UpdateProfileCommand;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.profile.dto.ProfileMcpToolBindingDTO;
import com.ls.agent.core.profile.dto.ProfileSkillBindingDTO;
import com.ls.agent.core.profile.entity.AgentProfileEntity;
import com.ls.agent.core.profile.entity.ProfileMcpToolEntity;
import com.ls.agent.core.profile.entity.ProfileSkillEntity;
import com.ls.agent.core.profile.mapper.AgentProfileMapper;
import com.ls.agent.core.profile.mapper.ProfileMcpToolMapper;
import com.ls.agent.core.profile.mapper.ProfileSkillMapper;
import com.ls.agent.core.skill.api.SkillQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultProfileService implements ProfileService {

    private final AgentProfileMapper profileMapper;
    private final ProfileSkillMapper profileSkillMapper;
    private final ProfileMcpToolMapper profileMcpToolMapper;
    private final ApplicationService applicationService;
    private final SkillQueryService skillQueryService;
    private final McpToolQueryService mcpToolQueryService;
    private final ObjectMapper objectMapper;

    public DefaultProfileService(
            AgentProfileMapper profileMapper,
            ProfileSkillMapper profileSkillMapper,
            ProfileMcpToolMapper profileMcpToolMapper,
            ApplicationService applicationService,
            SkillQueryService skillQueryService,
            McpToolQueryService mcpToolQueryService,
            ObjectMapper objectMapper
    ) {
        this.profileMapper = profileMapper;
        this.profileSkillMapper = profileSkillMapper;
        this.profileMcpToolMapper = profileMcpToolMapper;
        this.applicationService = applicationService;
        this.skillQueryService = skillQueryService;
        this.mcpToolQueryService = mcpToolQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ProfileDTO createProfile(CreateProfileCommand command) {
        Long tenantId = ProfileValidation.requireNonNull(command.tenantId(), "tenantId");
        Long ownerUserId = ProfileValidation.requireNonNull(command.ownerUserId(), "ownerUserId");
        Long applicationId = ProfileValidation.requireNonNull(command.applicationId(), "applicationId");
        applicationService.ensureApplicationOwned(tenantId, ownerUserId, applicationId);

        AgentProfileEntity profile = new AgentProfileEntity();
        profile.setTenantId(tenantId);
        profile.setOwnerUserId(ownerUserId);
        profile.setApplicationId(applicationId);
        profile.setName(ProfileValidation.normalizeRequired(command.name(), "name"));
        profile.setProfileType(ProfileValidation.normalizeRequired(command.profileType(), "profileType"));
        profile.setDescription(command.description());
        profile.setModelConfigId(ProfileValidation.requireNonNull(command.modelConfigId(), "modelConfigId"));
        profile.setPromptExtra(command.promptExtra());
        profile.setMemoryStrategy(ProfileValidation.normalizeMemoryStrategy(command.memoryStrategy(), objectMapper.createObjectNode()));
        profile.setMaxSteps(command.maxSteps() == null ? 6 : command.maxSteps());
        profile.setExecutionMode(ProfileValidation.normalizeExecutionMode(command.executionMode()));
        profile.setVisibility(ProfileValidation.normalizeRequired(command.visibility(), "visibility"));
        profile.setStatus(ProfileConstants.STATUS_DRAFT);
        profileMapper.insert(profile);
        return toProfileDTO(profile, List.of(), List.of());
    }

    @Override
    public PageResult<ProfileDTO> pageProfiles(Long tenantId, Long ownerUserId, Long applicationId, int pageNo, int pageSize) {
        applicationService.ensureApplicationOwned(tenantId, ownerUserId, applicationId);
        Page<AgentProfileEntity> page = profileMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<AgentProfileEntity>()
                        .eq(AgentProfileEntity::getTenantId, tenantId)
                        .eq(AgentProfileEntity::getOwnerUserId, ownerUserId)
                        .eq(AgentProfileEntity::getApplicationId, applicationId)
                        .orderByDesc(AgentProfileEntity::getUpdatedAt)
        );
        List<ProfileDTO> records = page.getRecords().stream()
                .map(profile -> toProfileDTO(profile, List.of(), List.of()))
                .toList();
        return PageResult.of(records, pageNo, pageSize, page.getTotal());
    }

    @Override
    public ProfileDTO getProfile(Long tenantId, Long ownerUserId, Long profileId) {
        AgentProfileEntity profile = getAccessibleProfile(tenantId, ownerUserId, profileId);
        List<ProfileSkillBindingDTO> skillBindings = profileSkillMapper.selectList(
                        new LambdaQueryWrapper<ProfileSkillEntity>()
                                .eq(ProfileSkillEntity::getProfileId, profileId))
                .stream()
                .map(entity -> new ProfileSkillBindingDTO(
                        entity.getSkillId(),
                        entity.getEnabledByDefault(),
                        entity.getRequired()))
                .toList();
        List<ProfileMcpToolBindingDTO> mcpToolBindings = profileMcpToolMapper.selectList(
                        new LambdaQueryWrapper<ProfileMcpToolEntity>()
                                .eq(ProfileMcpToolEntity::getProfileId, profileId))
                .stream()
                .map(entity -> new ProfileMcpToolBindingDTO(
                        entity.getMcpToolId(),
                        entity.getEnabledByDefault()))
                .toList();
        return toProfileDTO(profile, skillBindings, mcpToolBindings);
    }

    @Override
    @Transactional
    public ProfileDTO updateProfile(UpdateProfileCommand command) {
        AgentProfileEntity profile = getOwnedDraftProfile(command.tenantId(), command.ownerUserId(), command.profileId());
        profile.setName(ProfileValidation.normalizeRequired(command.name(), "name"));
        profile.setDescription(command.description());
        profile.setModelConfigId(ProfileValidation.requireNonNull(command.modelConfigId(), "modelConfigId"));
        profile.setPromptExtra(command.promptExtra());
        profile.setMemoryStrategy(ProfileValidation.normalizeMemoryStrategy(command.memoryStrategy(), objectMapper.createObjectNode()));
        profile.setMaxSteps(command.maxSteps() == null ? 6 : command.maxSteps());
        profile.setExecutionMode(ProfileValidation.normalizeExecutionMode(command.executionMode()));
        profile.setVisibility(ProfileValidation.normalizeRequired(command.visibility(), "visibility"));
        profileMapper.updateById(profile);
        return getProfile(command.tenantId(), command.ownerUserId(), profile.getId());
    }

    @Override
    @Transactional
    public ProfileDTO disableProfile(Long tenantId, Long ownerUserId, Long profileId) {
        AgentProfileEntity profile = getOwnedDraftProfile(tenantId, ownerUserId, profileId);
        profile.setStatus(ProfileConstants.STATUS_DISABLED);
        profileMapper.updateById(profile);
        return getProfile(tenantId, ownerUserId, profileId);
    }

    @Override
    @Transactional
    public void bindSkills(BindSkillsCommand command) {
        AgentProfileEntity profile = getOwnedDraftProfile(command.tenantId(), command.ownerUserId(), command.profileId());
        List<Long> skillIds = distinctIds(command.skillIds());
        if (!skillQueryService.areSkillsBindable(profile.getTenantId(), skillIds)) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Skill is unavailable");
        }
        profileSkillMapper.delete(new LambdaQueryWrapper<ProfileSkillEntity>()
                .eq(ProfileSkillEntity::getProfileId, profile.getId()));
        for (Long skillId : skillIds) {
            ProfileSkillEntity binding = new ProfileSkillEntity();
            binding.setProfileId(profile.getId());
            binding.setSkillId(skillId);
            binding.setEnabledByDefault(true);
            binding.setRequired(false);
            profileSkillMapper.insert(binding);
        }
    }

    @Override
    @Transactional
    public void bindMcpTools(BindMcpToolsCommand command) {
        AgentProfileEntity profile = getOwnedDraftProfile(command.tenantId(), command.ownerUserId(), command.profileId());
        List<Long> mcpToolIds = distinctIds(command.mcpToolIds());
        if (!mcpToolQueryService.areMcpToolsBindable(profile.getTenantId(), mcpToolIds)) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "MCP tool is unavailable");
        }
        profileMcpToolMapper.delete(new LambdaQueryWrapper<ProfileMcpToolEntity>()
                .eq(ProfileMcpToolEntity::getProfileId, profile.getId()));
        for (Long mcpToolId : mcpToolIds) {
            ProfileMcpToolEntity binding = new ProfileMcpToolEntity();
            binding.setProfileId(profile.getId());
            binding.setMcpToolId(mcpToolId);
            binding.setEnabledByDefault(true);
            profileMcpToolMapper.insert(binding);
        }
    }

    private AgentProfileEntity getAccessibleProfile(Long tenantId, Long ownerUserId, Long profileId) {
        AgentProfileEntity profile = profileMapper.selectById(profileId);
        if (profile == null || !tenantId.equals(profile.getTenantId())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Profile is unavailable");
        }
        boolean owned = ownerUserId.equals(profile.getOwnerUserId());
        boolean published = ProfileConstants.STATUS_PUBLISHED.equals(profile.getStatus());
        if (!owned && !published) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "Profile is not accessible");
        }
        return profile;
    }

    private AgentProfileEntity getOwnedDraftProfile(Long tenantId, Long ownerUserId, Long profileId) {
        AgentProfileEntity profile = getAccessibleProfile(tenantId, ownerUserId, profileId);
        if (!ownerUserId.equals(profile.getOwnerUserId()) || !ProfileConstants.STATUS_DRAFT.equals(profile.getStatus())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "Only owner can edit draft profile");
        }
        return profile;
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private ProfileDTO toProfileDTO(
            AgentProfileEntity profile,
            List<ProfileSkillBindingDTO> skillBindings,
            List<ProfileMcpToolBindingDTO> mcpToolBindings
    ) {
        return new ProfileDTO(
                profile.getId(),
                profile.getApplicationId(),
                profile.getName(),
                profile.getProfileType(),
                profile.getDescription(),
                profile.getModelConfigId(),
                profile.getPromptExtra(),
                profile.getMemoryStrategy(),
                profile.getMaxSteps(),
                profile.getExecutionMode(),
                profile.getVisibility(),
                profile.getStatus(),
                skillBindings,
                mcpToolBindings
        );
    }
}
