package com.ls.agent.core.context;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.application.ContextSchemaAssembler;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSchema;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSchemaAssemblerTest {

    @Test
    void assembleFetchesSupportedSlotsInSchemaOrderAndSkipsEmptyContent() {
        ContextSchema schema = new ContextSchema("test", List.of(
                ContextSlot.required(ContextSlotKind.PROFILE, 600),
                ContextSlot.of(ContextSlotKind.TASK_MEMORY, 300),
                ContextSlot.of(ContextSlotKind.TOOLS, 800)
        ));
        ContextSchemaAssembler assembler = new ContextSchemaAssembler(List.of(
                source(ContextSlotKind.TOOLS, "Available skills:\n- calculator: Evaluate expressions.\n", 12),
                source(ContextSlotKind.PROFILE, "You are Nexus.\n\nProfile Prompt:\nBe concise.", 15),
                source(ContextSlotKind.TASK_MEMORY, "", 0)
        ));

        ContextSchemaAssembler.AssembledContext assembled = assembler.assemble(schema, command());

        assertThat(assembled.systemPrompt())
                .isEqualTo("You are Nexus.\n\nProfile Prompt:\nBe concise.\n\nAvailable skills:\n- calculator: Evaluate expressions.");
        assertThat(assembled.content(ContextSlotKind.PROFILE).usedTokens()).isEqualTo(15);
        assertThat(assembled.content(ContextSlotKind.TASK_MEMORY).usedTokens()).isZero();
        assertThat(assembled.content(ContextSlotKind.TOOLS).usedTokens()).isEqualTo(12);
        assertThat(assembled.usedTokens(ContextSlotKind.PROFILE)).isEqualTo(15);
        assertThat(assembled.usedTokens(ContextSlotKind.TOOLS)).isEqualTo(12);
    }

    private ContextSlotSource source(ContextSlotKind supportedKind, String content, int usedTokens) {
        return new ContextSlotSource() {
            @Override
            public boolean supports(ContextSlotKind kind) {
                return supportedKind == kind;
            }

            @Override
            public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
                return new ContextSlotContent(slot.kind(), content, usedTokens, false);
            }
        };
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
