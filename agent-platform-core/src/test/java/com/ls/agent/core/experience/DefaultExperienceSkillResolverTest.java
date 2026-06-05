package com.ls.agent.core.experience;

import com.ls.agent.core.experience.application.DefaultExperienceSkillResolver;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.experience.entity.ExperienceSkillEntity;
import com.ls.agent.core.experience.mapper.ExperienceSkillMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultExperienceSkillResolverTest {

    private final ExperienceSkillMapper mapper = mock(ExperienceSkillMapper.class);
    private final DefaultExperienceSkillResolver resolver = new DefaultExperienceSkillResolver(mapper);

    @Test
    void resolveReturnsScopedActiveExperienceSkillsRankedByKeywordAndProfileMatch() {
        ExperienceSkillEntity global = skill(1L, null, null, null, "java", new String[]{"spring"}, "General Spring note", 1);
        ExperienceSkillEntity profileKeyword = skill(2L, 20001L, 10001L, 50001L, "java",
                new String[]{"quota", "sse"}, "Use CAS for quota before SSE streaming", 2);
        ExperienceSkillEntity profileOnly = skill(3L, 20001L, 10001L, 50001L, "ops",
                new String[]{"deploy"}, "Deployment checklist", 3);
        when(mapper.selectList(any())).thenReturn(List.of(global, profileOnly, profileKeyword));

        List<ExperienceSkillDTO> result = resolver.resolve(
                1L, 20001L, 10001L, 50001L, "java", "quota sse stream", 2);

        assertThat(result).extracting(ExperienceSkillDTO::experienceSkillId).containsExactly(2L, 1L);
        assertThat(result.get(0).content()).isEqualTo("Use CAS for quota before SSE streaming");

        verify(mapper).selectList(any());
    }

    private ExperienceSkillEntity skill(
            Long id,
            Long applicationId,
            Long userId,
            Long profileId,
            String domain,
            String[] triggerKeywords,
            String content,
            int daysAgo
    ) {
        ExperienceSkillEntity entity = new ExperienceSkillEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setApplicationId(applicationId);
        entity.setUserId(userId);
        entity.setProfileId(profileId);
        entity.setCode("exp-" + id);
        entity.setName("Experience " + id);
        entity.setDomain(domain);
        entity.setTriggerKeywords(triggerKeywords);
        entity.setContent(content);
        entity.setStatus("ACTIVE");
        entity.setUpdatedAt(LocalDateTime.now().minusDays(daysAgo));
        return entity;
    }
}
