package com.ls.agent.core.context;

import com.ls.agent.core.context.dto.ContextSchema;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSchemaTest {

    @Test
    void reactSchemaDefinesOrderedSlotsWithBudgets() {
        ContextSchema schema = ContextSchema.reactSchema();

        assertThat(schema.name()).isEqualTo("react");
        assertThat(schema.slots()).extracting(ContextSlot::kind)
                .containsExactly(
                        ContextSlotKind.PROFILE,
                        ContextSlotKind.HISTORY,
                        ContextSlotKind.PREFERENCE,
                        ContextSlotKind.TASK_MEMORY,
                        ContextSlotKind.TOOLS,
                        ContextSlotKind.EXPERIENCE
                );
        assertThat(schema.slots()).allSatisfy(slot -> assertThat(slot.tokenBudget()).isPositive());
        assertThat(schema.slot(ContextSlotKind.PROFILE)).isPresent();
        assertThat(schema.slot(ContextSlotKind.RAG_RECALL)).isEmpty();
    }

    @Test
    void ragSchemaKeepsRagSeparateFromMemory() {
        ContextSchema schema = ContextSchema.ragSchema();

        assertThat(schema.name()).isEqualTo("rag");
        assertThat(schema.slots()).extracting(ContextSlot::kind)
                .containsExactly(
                        ContextSlotKind.PROFILE,
                        ContextSlotKind.HISTORY,
                        ContextSlotKind.RAG_RECALL,
                        ContextSlotKind.CONSTRAINTS
                );
        assertThat(schema.slot(ContextSlotKind.RAG_RECALL)).isPresent();
        assertThat(schema.slot(ContextSlotKind.TASK_MEMORY)).isEmpty();
    }
}
