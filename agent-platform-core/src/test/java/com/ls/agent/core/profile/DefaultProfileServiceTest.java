package com.ls.agent.core.profile;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.profile.application.DefaultProfileService;
import com.ls.agent.core.profile.command.BindMcpToolsCommand;
import com.ls.agent.core.profile.command.BindSkillsCommand;
import com.ls.agent.core.profile.command.CreateProfileCommand;
import com.ls.agent.core.profile.command.UpdateProfileCommand;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.profile.entity.AgentProfileEntity;
import com.ls.agent.core.profile.entity.ProfileMcpToolEntity;
import com.ls.agent.core.profile.entity.ProfileSkillEntity;
import com.ls.agent.core.profile.mapper.AgentProfileMapper;
import com.ls.agent.core.profile.mapper.ProfileMcpToolMapper;
import com.ls.agent.core.profile.mapper.ProfileSkillMapper;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultProfileServiceTest {

    private final AgentProfileMapper profileMapper = mock(AgentProfileMapper.class);
    private final ProfileSkillMapper profileSkillMapper = mock(ProfileSkillMapper.class);
    private final ProfileMcpToolMapper profileMcpToolMapper = mock(ProfileMcpToolMapper.class);
    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final SkillQueryService skillQueryService = mock(SkillQueryService.class);
    private final McpToolQueryService mcpToolQueryService = mock(McpToolQueryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultProfileService service = new DefaultProfileService(
            profileMapper,
            profileSkillMapper,
            profileMcpToolMapper,
            applicationService,
            skillQueryService,
            mcpToolQueryService,
            objectMapper
    );

    @Test
    void createProfilePersistsDraftOwnedByCurrentUser() {
        service.createProfile(new CreateProfileCommand(
                1L,
                10001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage 1 assistant",
                1L,
                "Be concise.",
                objectMapper.createObjectNode().put("shortTermEnabled", true),
                5,
                "TEAM",
                "PRIVATE"
        ));

        verify(applicationService).ensureApplicationOwned(1L, 10001L, 20001L);
        ArgumentCaptor<AgentProfileEntity> captor = ArgumentCaptor.forClass(AgentProfileEntity.class);
        verify(profileMapper).insert(captor.capture());
        AgentProfileEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(1L);
        assertThat(entity.getOwnerUserId()).isEqualTo(10001L);
        assertThat(entity.getApplicationId()).isEqualTo(20001L);
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
        assertThat(entity.getExecutionMode()).isEqualTo("TEAM");
        assertThat(entity.getMemoryStrategy().get("shortTermEnabled").asBoolean()).isTrue();
    }

    @Test
    void createProfileRejectsInvalidMemoryStrategyMode() {
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand(
                1L,
                10001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage 1 assistant",
                1L,
                "Be concise.",
                objectMapper.createObjectNode().put("mode", "READWRITE"),
                5,
                "BASIC",
                "PRIVATE"
        ))).isInstanceOf(BizException.class)
                .hasMessageContaining("memoryStrategy.mode");
    }

    @Test
    void pageProfilesFiltersByTenantUserAndApplication() {
        AgentProfileEntity profile = draftProfile();
        Page<AgentProfileEntity> page = Page.of(2, 20);
        page.setRecords(List.of(profile));
        page.setTotal(1);
        when(profileMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<ProfileDTO> result = service.pageProfiles(1L, 10001L, 20001L, 2, 20);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).profileId()).isEqualTo(50001L);
        assertThat(result.records().get(0).name()).isEqualTo("General Assistant");
        verify(applicationService).ensureApplicationOwned(1L, 10001L, 20001L);
    }

    @Test
    void getProfileDetailReturnsBindingsForOwnedProfile() {
        when(profileMapper.selectById(50001L)).thenReturn(draftProfile());
        ProfileSkillEntity binding = new ProfileSkillEntity();
        binding.setSkillId(1L);
        binding.setEnabledByDefault(true);
        binding.setRequired(false);
        when(profileSkillMapper.selectList(any())).thenReturn(List.of(binding));
        ProfileMcpToolEntity mcpBinding = new ProfileMcpToolEntity();
        mcpBinding.setMcpToolId(1L);
        mcpBinding.setEnabledByDefault(true);
        when(profileMcpToolMapper.selectList(any())).thenReturn(List.of(mcpBinding));

        ProfileDTO result = service.getProfile(1L, 10001L, 50001L);

        assertThat(result.profileId()).isEqualTo(50001L);
        assertThat(result.skillBindings()).extracting("skillId").containsExactly(1L);
        assertThat(result.mcpToolBindings()).extracting("mcpToolId").containsExactly(1L);
    }

    @Test
    void getProfileRejectsOtherUsersDraftProfile() {
        AgentProfileEntity profile = draftProfile();
        profile.setOwnerUserId(99999L);
        when(profileMapper.selectById(50001L)).thenReturn(profile);

        assertThatThrownBy(() -> service.getProfile(1L, 10001L, 50001L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void bindSkillsReplacesBindingsAfterCheckingSkillAvailability() {
        when(profileMapper.selectById(50001L)).thenReturn(draftProfile());
        when(skillQueryService.areSkillsBindable(1L, List.of(1L, 2L))).thenReturn(true);

        service.bindSkills(new BindSkillsCommand(1L, 10001L, 50001L, List.of(1L, 2L)));

        verify(profileSkillMapper).delete(any());
        verify(profileSkillMapper, times(2)).insert(any(ProfileSkillEntity.class));
    }

    @Test
    void updateProfileUpdatesEditableDraftFields() {
        when(profileMapper.selectById(50001L)).thenReturn(draftProfile());
        when(profileSkillMapper.selectList(any())).thenReturn(List.of());
        when(profileMcpToolMapper.selectList(any())).thenReturn(List.of());

        ProfileDTO result = service.updateProfile(new UpdateProfileCommand(
                1L,
                10001L,
                50001L,
                "Updated Assistant",
                "Updated description",
                2L,
                "Updated prompt.",
                objectMapper.createObjectNode().put("mode", "READ_ONLY"),
                3,
                "TEAM",
                "PRIVATE"
        ));

        ArgumentCaptor<AgentProfileEntity> captor = ArgumentCaptor.forClass(AgentProfileEntity.class);
        verify(profileMapper).updateById(captor.capture());
        AgentProfileEntity entity = captor.getValue();
        assertThat(entity.getName()).isEqualTo("Updated Assistant");
        assertThat(entity.getDescription()).isEqualTo("Updated description");
        assertThat(entity.getModelConfigId()).isEqualTo(2L);
        assertThat(entity.getPromptExtra()).isEqualTo("Updated prompt.");
        assertThat(entity.getMemoryStrategy().get("mode").asText()).isEqualTo("READ_ONLY");
        assertThat(entity.getMaxSteps()).isEqualTo(3);
        assertThat(entity.getExecutionMode()).isEqualTo("TEAM");
        assertThat(result.modelConfigId()).isEqualTo(2L);
        assertThat(result.executionMode()).isEqualTo("TEAM");
    }

    @Test
    void updateProfileRejectsInvalidMemoryStrategyMode() {
        when(profileMapper.selectById(50001L)).thenReturn(draftProfile());

        assertThatThrownBy(() -> service.updateProfile(new UpdateProfileCommand(
                1L,
                10001L,
                50001L,
                "Updated Assistant",
                "Updated description",
                2L,
                "Updated prompt.",
                objectMapper.createObjectNode().put("mode", "READWRITE"),
                3,
                "BASIC",
                "PRIVATE"
        ))).isInstanceOf(BizException.class)
                .hasMessageContaining("memoryStrategy.mode");
    }

    @Test
    void disableProfileMarksOwnedDraftProfileDisabled() {
        when(profileMapper.selectById(50001L)).thenReturn(draftProfile());
        when(profileSkillMapper.selectList(any())).thenReturn(List.of());
        when(profileMcpToolMapper.selectList(any())).thenReturn(List.of());

        ProfileDTO result = service.disableProfile(1L, 10001L, 50001L);

        assertThat(result.status()).isEqualTo("DISABLED");
        ArgumentCaptor<AgentProfileEntity> captor = ArgumentCaptor.forClass(AgentProfileEntity.class);
        verify(profileMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void bindMcpToolsReplacesBindingsAfterCheckingToolAvailability() {
        when(profileMapper.selectById(50001L)).thenReturn(draftProfile());
        when(mcpToolQueryService.areMcpToolsBindable(1L, List.of(1L))).thenReturn(true);

        service.bindMcpTools(new BindMcpToolsCommand(1L, 10001L, 50001L, List.of(1L)));

        verify(profileMcpToolMapper).delete(any());
        verify(profileMcpToolMapper).insert(any(ProfileMcpToolEntity.class));
    }

    private AgentProfileEntity draftProfile() {
        AgentProfileEntity profile = new AgentProfileEntity();
        profile.setId(50001L);
        profile.setTenantId(1L);
        profile.setOwnerUserId(10001L);
        profile.setApplicationId(20001L);
        profile.setName("General Assistant");
        profile.setProfileType("GENERAL");
        profile.setDescription("Stage 1 assistant");
        profile.setModelConfigId(1L);
        profile.setPromptExtra("Be concise.");
        profile.setMemoryStrategy(objectMapper.createObjectNode().put("shortTermEnabled", true));
        profile.setMaxSteps(5);
        profile.setExecutionMode("BASIC");
        profile.setVisibility("PRIVATE");
        profile.setStatus("DRAFT");
        return profile;
    }
}
