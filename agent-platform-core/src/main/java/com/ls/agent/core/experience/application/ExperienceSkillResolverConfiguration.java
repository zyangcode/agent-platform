package com.ls.agent.core.experience.application;

import com.ls.agent.core.experience.api.ExperienceSkillResolver;
import com.ls.agent.core.experience.mapper.ExperienceSkillMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExperienceSkillResolverConfiguration {

    @Bean
    @ConditionalOnBean(ExperienceSkillMapper.class)
    public ExperienceSkillResolver defaultExperienceSkillResolver(ExperienceSkillMapper experienceSkillMapper) {
        return new DefaultExperienceSkillResolver(experienceSkillMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ExperienceSkillResolver.class)
    public ExperienceSkillResolver noopExperienceSkillResolver() {
        return new NoopExperienceSkillResolver();
    }
}
