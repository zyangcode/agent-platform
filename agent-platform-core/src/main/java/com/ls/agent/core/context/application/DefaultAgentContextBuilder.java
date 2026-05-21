package com.ls.agent.core.context.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultAgentContextBuilder implements AgentContextBuilder {

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 4_000;
    private static final int HISTORY_LIMIT = 20;
    private static final int MEMORY_LIMIT = 5;
    private static final String PLATFORM_SYSTEM_PROMPT = """
            You are AgentX, a helpful AI agent platform assistant.
            Follow the user's profile prompt and only use listed tools when needed.
            """;

    private final ProfileService profileService;
    private final SkillRegistry skillRegistry;
    private final McpToolRegistry mcpToolRegistry;
    private final MessageHistoryService messageHistoryService;
    private final MemoryRecallService memoryRecallService;

    public DefaultAgentContextBuilder(
            ProfileService profileService,
            SkillRegistry skillRegistry,
            McpToolRegistry mcpToolRegistry,
            MessageHistoryService messageHistoryService,
            MemoryRecallService memoryRecallService
    ) {
        this.profileService = profileService;
        this.skillRegistry = skillRegistry;
        this.mcpToolRegistry = mcpToolRegistry;
        this.messageHistoryService = messageHistoryService;
        this.memoryRecallService = memoryRecallService;
    }

    @Override
    public AgentContextDTO build(BuildAgentContextCommand command) {
        validate(command);
        int maxTokens = command.maxContextTokens() == null || command.maxContextTokens() <= 0
                ? DEFAULT_MAX_CONTEXT_TOKENS
                : command.maxContextTokens();

        ProfileDTO profile = profileService.getProfile(command.tenantId(), command.userId(), command.profileId());
        if (!command.applicationId().equals(profile.applicationId())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Profile does not belong to application");
        }

        List<Long> skillIds = profile.skillBindings().stream()
                .filter(binding -> Boolean.TRUE.equals(binding.enabledByDefault()))
                .map(binding -> binding.skillId())
                .toList();
        List<Long> mcpToolIds = profile.mcpToolBindings().stream()
                .filter(binding -> Boolean.TRUE.equals(binding.enabledByDefault()))
                .map(binding -> binding.mcpToolId())
                .toList();
        List<SkillDTO> skills = skillRegistry.listAvailableSkills(command.tenantId(), skillIds);
        List<McpToolDTO> mcpTools = mcpToolRegistry.listAvailableTools(command.tenantId(), mcpToolIds);
        List<MemoryDTO> memories = memoryRecallService.recall(
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                command.userInput(),
                MEMORY_LIMIT
        );
        List<ConversationMessageDTO> history = messageHistoryService.listRecentMessages(
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                command.conversationId(),
                HISTORY_LIMIT
        );

        String systemPrompt = buildSystemPrompt(profile, skills, mcpTools, memories);
        int reservedTokens = estimateTokens(systemPrompt) + estimateTokens(command.userInput());
        List<ModelMessage> keptHistory = trimHistory(history, maxTokens - reservedTokens);
        boolean truncated = keptHistory.size() < history.size();

        List<ModelMessage> messages = new ArrayList<>();
        messages.add(new ModelMessage("system", systemPrompt));
        messages.addAll(keptHistory);
        messages.add(new ModelMessage("user", command.userInput()));
        return new AgentContextDTO(
                profile.modelConfigId(),
                profile,
                messages,
                skills,
                mcpTools,
                estimateMessagesTokens(messages),
                truncated
        );
    }

    private void validate(BuildAgentContextCommand command) {
        if (command == null
                || command.tenantId() == null
                || command.userId() == null
                || command.applicationId() == null
                || command.profileId() == null
                || command.userInput() == null
                || command.userInput().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Context command is invalid");
        }
    }

    private String buildSystemPrompt(
            ProfileDTO profile,
            List<SkillDTO> skills,
            List<McpToolDTO> mcpTools,
            List<MemoryDTO> memories
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(PLATFORM_SYSTEM_PROMPT.strip()).append("\n\n");
        if (profile.promptExtra() != null && !profile.promptExtra().isBlank()) {
            builder.append("Profile Prompt:\n").append(profile.promptExtra().strip()).append("\n\n");
        }
        if (!memories.isEmpty()) {
            builder.append("Long-term memories:\n");
            memories.forEach(memory -> builder.append("- ").append(memory.content()).append('\n'));
            builder.append('\n');
        }
        if (!skills.isEmpty()) {
            builder.append("Available skills:\n");
            skills.forEach(skill -> builder
                    .append("- ")
                    .append(skill.code())
                    .append(": ")
                    .append(nullToEmpty(skill.description()))
                    .append('\n'));
            builder.append('\n');
        }
        if (!mcpTools.isEmpty()) {
            builder.append("Available MCP tools:\n");
            mcpTools.forEach(tool -> builder
                    .append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(nullToEmpty(tool.description()))
                    .append('\n'));
        }
        return builder.toString().strip();
    }

    private List<ModelMessage> trimHistory(List<ConversationMessageDTO> history, int remainingTokens) {
        if (history == null || history.isEmpty() || remainingTokens <= 0) {
            return List.of();
        }
        List<ModelMessage> selected = new ArrayList<>();
        int used = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMessageDTO message = history.get(i);
            int tokens = message.tokenCount() == null ? estimateTokens(message.content()) : message.tokenCount();
            if (used + tokens > remainingTokens && !selected.isEmpty()) {
                break;
            }
            if (used + tokens <= remainingTokens) {
                selected.add(0, new ModelMessage(message.role(), message.content()));
                used += tokens;
            }
        }
        return selected;
    }

    private int estimateMessagesTokens(List<ModelMessage> messages) {
        return messages.stream()
                .mapToInt(message -> estimateTokens(message.content()))
                .sum();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
