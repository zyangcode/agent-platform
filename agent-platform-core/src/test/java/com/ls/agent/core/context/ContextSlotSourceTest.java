package com.ls.agent.core.context;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSlotSourceTest {

    @Test
    void sourceFetchesContentForSupportedSlot() {
        ContextSlotSource source = new StaticProfileSlotSource();
        ContextSlot slot = ContextSlot.required(ContextSlotKind.PROFILE, 600);

        ContextSlotContent content = source.fetch(slot, command());

        assertThat(source.supports(ContextSlotKind.PROFILE)).isTrue();
        assertThat(source.supports(ContextSlotKind.RAG_RECALL)).isFalse();
        assertThat(content.kind()).isEqualTo(ContextSlotKind.PROFILE);
        assertThat(content.content()).isEqualTo("Profile Prompt:\nBe concise.");
        assertThat(content.usedTokens()).isPositive();
        assertThat(content.truncated()).isFalse();
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

    private static final class StaticProfileSlotSource implements ContextSlotSource {

        @Override
        public boolean supports(ContextSlotKind kind) {
            return kind == ContextSlotKind.PROFILE;
        }

        @Override
        public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
            return new ContextSlotContent(slot.kind(), "Profile Prompt:\nBe concise.", 8, false);
        }
    }
}
