package com.ls.agent.core.context;

import com.ls.agent.core.context.application.MemorySlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.memory.dto.MemoryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySlotSourceTest {

    @Test
    void fetchBuildsLongTermMemoryBlockWithinBudget() {
        MemorySlotSource source = new MemorySlotSource(List.of(
                new MemoryDTO("LONG_TERM", "Ada likes concise answers."),
                new MemoryDTO("LONG_TERM", "Ada prefers Java examples."),
                new MemoryDTO("LONG_TERM", "This very long memory should not fit into the tiny memory slot budget.")
        ));

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.TASK_MEMORY, 14),
                command()
        );

        assertThat(source.supports(ContextSlotKind.TASK_MEMORY)).isTrue();
        assertThat(source.supports(ContextSlotKind.PROFILE)).isFalse();
        assertThat(content.kind()).isEqualTo(ContextSlotKind.TASK_MEMORY);
        assertThat(content.content())
                .contains("Long-term memories:")
                .contains("- Ada likes concise answers.")
                .contains("- Ada prefers Java examples.")
                .doesNotContain("This very long memory");
        assertThat(content.usedTokens()).isLessThanOrEqualTo(14);
        assertThat(content.truncated()).isTrue();
    }

    @Test
    void fetchReturnsEmptyContentWhenNoMemoryFits() {
        MemorySlotSource source = new MemorySlotSource(List.of(
                new MemoryDTO("LONG_TERM", "This memory is too large for a tiny slot.")
        ));

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.TASK_MEMORY, 1),
                command()
        );

        assertThat(content.content()).isEmpty();
        assertThat(content.usedTokens()).isZero();
        assertThat(content.truncated()).isTrue();
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
