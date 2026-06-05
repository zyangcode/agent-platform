package com.ls.agent.core.skill.api;

import com.ls.agent.core.skill.command.RegisterJarSkillCommand;
import com.ls.agent.core.skill.dto.JarSkillRegistrationResult;

public interface JarSkillService {

    JarSkillRegistrationResult register(RegisterJarSkillCommand command);
}
