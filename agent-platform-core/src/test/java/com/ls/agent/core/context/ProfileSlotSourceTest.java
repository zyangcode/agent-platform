package com.ls.agent.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.context.application.ProfileSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.profile.dto.ProfileDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileSlotSourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchBuildsPlatformAndProfilePrompt() {
        ProfileSlotSource source = new ProfileSlotSource(profile());

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.PROFILE, 600),
                command()
        );

        assertThat(source.supports(ContextSlotKind.PROFILE)).isTrue();
        assertThat(source.supports(ContextSlotKind.HISTORY)).isFalse();
        assertThat(content.kind()).isEqualTo(ContextSlotKind.PROFILE);
        assertThat(content.content())
                .contains("You are AgentX")
                .contains("Tools only when needed")
                .contains(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .contains("Profile Prompt:")
                .contains("Be concise.");
        assertThat(content.usedTokens()).isPositive();
        assertThat(content.truncated()).isFalse();
    }

    private ProfileDTO profile() {
        return new ProfileDTO(
                50001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage profile",
                30001L,
                "Be concise.",
                objectMapper.createObjectNode(),
                6,
                "BASIC",
                "PRIVATE",
                "DRAFT",
                List.of(),
                List.of()
        );
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
