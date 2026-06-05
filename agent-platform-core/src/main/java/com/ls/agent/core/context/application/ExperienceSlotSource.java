package com.ls.agent.core.context.application;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;

import java.util.List;

public class ExperienceSlotSource implements ContextSlotSource {

    private final List<ExperienceSkillDTO> experienceSkills;

    public ExperienceSlotSource(List<ExperienceSkillDTO> experienceSkills) {
        this.experienceSkills = experienceSkills == null ? List.of() : List.copyOf(experienceSkills);
    }

    @Override
    public boolean supports(ContextSlotKind kind) {
        return ContextSlotKind.EXPERIENCE.equals(kind);
    }

    @Override
    public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
        if (!supports(slot.kind()) || experienceSkills.isEmpty()) {
            return ContextSlotContent.empty(slot.kind());
        }
        StringBuilder builder = new StringBuilder("Experience refs:\n");
        int used = 0;
        boolean truncated = false;
        for (ExperienceSkillDTO skill : experienceSkills) {
            String line = "- " + nullToEmpty(skill.code()) + ": " + nullToEmpty(skill.content()) + '\n';
            int tokens = estimateTokens(line);
            if (used + tokens > slot.tokenBudget()) {
                truncated = true;
                break;
            }
            builder.append(line);
            used += tokens;
        }
        if (used == 0) {
            return new ContextSlotContent(ContextSlotKind.EXPERIENCE, "", 0, truncated);
        }
        return new ContextSlotContent(ContextSlotKind.EXPERIENCE, builder.toString(), used, truncated);
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
