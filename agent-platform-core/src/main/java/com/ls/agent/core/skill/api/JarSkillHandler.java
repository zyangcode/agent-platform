package com.ls.agent.core.skill.api;

import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;

public interface JarSkillHandler {

    SkillExecuteResult execute(SkillExecuteCommand command);
}
