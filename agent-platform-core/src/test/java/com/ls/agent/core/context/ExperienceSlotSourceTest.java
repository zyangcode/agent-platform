package com.ls.agent.core.context;

import com.ls.agent.core.context.application.ExperienceSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceSlotSourceTest {

    @Test
    void fetchBuildsExperienceRefsWithinBudget() {
        ExperienceSlotSource source = new ExperienceSlotSource(List.of(
                experience("weather", "Use weather tools before giving outdoor sports advice."),
                experience("style", "Answer in concise Chinese."),
                experience("long", "This long experience reference should not fit into the tiny experience slot budget.")
        ));

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.EXPERIENCE, 30),
                command()
        );

        assertThat(source.supports(ContextSlotKind.EXPERIENCE)).isTrue();
        assertThat(source.supports(ContextSlotKind.TASK_MEMORY)).isFalse();
        assertThat(content.kind()).isEqualTo(ContextSlotKind.EXPERIENCE);
        assertThat(content.content())
                .contains("Experience refs:")
                .contains("- weather: Use weather tools before giving outdoor sports advice.")
                .contains("- style: Answer in concise Chinese.")
                .doesNotContain("This long experience");
        assertThat(content.usedTokens()).isLessThanOrEqualTo(30);
        assertThat(content.truncated()).isTrue();
    }

    @Test
    void fetchReturnsEmptyContentWhenNoExperienceFits() {
        ExperienceSlotSource source = new ExperienceSlotSource(List.of(
                experience("long", "This experience reference is too large for a tiny slot.")
        ));

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.EXPERIENCE, 1),
                command()
        );

        assertThat(content.content()).isEmpty();
        assertThat(content.usedTokens()).isZero();
        assertThat(content.truncated()).isTrue();
    }

    private ExperienceSkillDTO experience(String code, String content) {
        return new ExperienceSkillDTO(1L, code, code, "GENERAL", content);
    }

    private BuildAgentContextCommand command() {
        return new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "hello",
                1_000,
                null,
                null
        );
    }
}
