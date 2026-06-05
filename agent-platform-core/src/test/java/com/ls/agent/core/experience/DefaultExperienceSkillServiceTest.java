package com.ls.agent.core.experience;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.experience.application.DefaultExperienceSkillService;
import com.ls.agent.core.experience.command.CreateExperienceSkillCommand;
import com.ls.agent.core.experience.command.UpdateExperienceSkillCommand;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.experience.entity.ExperienceSkillEntity;
import com.ls.agent.core.experience.mapper.ExperienceSkillMapper;
import com.ls.agent.core.identity.api.ApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultExperienceSkillServiceTest {

    private final ExperienceSkillMapper mapper = mock(ExperienceSkillMapper.class);
    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final DefaultExperienceSkillService service = new DefaultExperienceSkillService(mapper, applicationService);

    @Test
    void createExperienceSkillPersistsActivePromptSkillInApplicationScope() {
        service.create(new CreateExperienceSkillCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "support-refund-tone",
                "Support refund tone",
                "SUPPORT",
                List.of("refund", "tone"),
                "Use empathy first."
        ));

        verify(applicationService).ensureApplicationOwned(1L, 10001L, 20001L);
        ArgumentCaptor<ExperienceSkillEntity> captor = ArgumentCaptor.forClass(ExperienceSkillEntity.class);
        verify(mapper).insert(captor.capture());
        ExperienceSkillEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(1L);
        assertThat(entity.getApplicationId()).isEqualTo(20001L);
        assertThat(entity.getUserId()).isEqualTo(10001L);
        assertThat(entity.getProfileId()).isEqualTo(50001L);
        assertThat(entity.getCode()).isEqualTo("support-refund-tone");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getTriggerKeywords()).containsExactly("refund", "tone");
    }

    @Test
    void pageExperienceSkillsReturnsOnlyCurrentApplicationScope() {
        ExperienceSkillEntity entity = activeSkill();
        Page<ExperienceSkillEntity> page = Page.of(1, 20);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<ExperienceSkillDTO> result = service.page(1L, 10001L, 20001L, 1, 20);

        verify(applicationService).ensureApplicationOwned(1L, 10001L, 20001L);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).code()).isEqualTo("support-refund-tone");
        assertThat(result.records().get(0).content()).isEqualTo("Use empathy first.");
    }

    @Test
    void updateExperienceSkillRejectsOtherUsersSkill() {
        ExperienceSkillEntity entity = activeSkill();
        entity.setUserId(99999L);
        when(mapper.selectById(70001L)).thenReturn(entity);

        assertThatThrownBy(() -> service.update(new UpdateExperienceSkillCommand(
                1L,
                10001L,
                20001L,
                70001L,
                "support-refund-tone",
                "Support refund tone",
                "SUPPORT",
                List.of("refund"),
                "Updated"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void disableExperienceSkillMarksOwnedSkillInactive() {
        when(mapper.selectById(70001L)).thenReturn(activeSkill());

        ExperienceSkillDTO result = service.disable(1L, 10001L, 20001L, 70001L);

        ArgumentCaptor<ExperienceSkillEntity> captor = ArgumentCaptor.forClass(ExperienceSkillEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
        assertThat(result.experienceSkillId()).isEqualTo(70001L);
    }

    private ExperienceSkillEntity activeSkill() {
        ExperienceSkillEntity entity = new ExperienceSkillEntity();
        entity.setId(70001L);
        entity.setTenantId(1L);
        entity.setApplicationId(20001L);
        entity.setUserId(10001L);
        entity.setProfileId(50001L);
        entity.setCode("support-refund-tone");
        entity.setName("Support refund tone");
        entity.setDomain("SUPPORT");
        entity.setTriggerKeywords(new String[]{"refund", "tone"});
        entity.setContent("Use empathy first.");
        entity.setStatus("ACTIVE");
        return entity;
    }
}
