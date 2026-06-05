package com.ls.agent.core.context.application;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSchema;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ContextSchemaAssembler {

    private final List<ContextSlotSource> sources;

    public ContextSchemaAssembler(List<ContextSlotSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public AssembledContext assemble(ContextSchema schema, BuildAgentContextCommand command) {
        Map<ContextSlotKind, ContextSlotContent> contents = new EnumMap<>(ContextSlotKind.class);
        StringBuilder prompt = new StringBuilder();
        for (ContextSlot slot : schema.slots()) {
            ContextSlotContent content = fetch(slot, command);
            contents.put(slot.kind(), content);
            if (!content.content().isBlank()) {
                if (!prompt.isEmpty()) {
                    prompt.append("\n\n");
                }
                prompt.append(content.content().strip());
            }
        }
        return new AssembledContext(prompt.toString().strip(), contents);
    }

    private ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
        return sources.stream()
                .filter(source -> source.supports(slot.kind()))
                .findFirst()
                .map(source -> source.fetch(slot, command))
                .orElseGet(() -> ContextSlotContent.empty(slot.kind()));
    }

    public record AssembledContext(
            String systemPrompt,
            Map<ContextSlotKind, ContextSlotContent> contents
    ) {

        public AssembledContext {
            systemPrompt = systemPrompt == null ? "" : systemPrompt;
            contents = contents == null ? Map.of() : Map.copyOf(contents);
        }

        public ContextSlotContent content(ContextSlotKind kind) {
            ContextSlotContent content = contents.get(kind);
            return content == null ? ContextSlotContent.empty(kind) : content;
        }

        public int usedTokens(ContextSlotKind kind) {
            return content(kind).usedTokens();
        }

        public boolean truncated(ContextSlotKind kind) {
            return content(kind).truncated();
        }
    }
}
