package com.ls.agent.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.context.application.DefaultAgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.profile.dto.ProfileMcpToolBindingDTO;
import com.ls.agent.core.profile.dto.ProfileSkillBindingDTO;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAgentContextBuilderTest {

    private final ProfileService profileService = mock(ProfileService.class);
    private final SkillRegistry skillRegistry = mock(SkillRegistry.class);
    private final McpToolRegistry mcpToolRegistry = mock(McpToolRegistry.class);
    private final MessageHistoryService messageHistoryService = mock(MessageHistoryService.class);
    private final MemoryRecallService memoryRecallService = mock(MemoryRecallService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentContextBuilder builder = new DefaultAgentContextBuilder(
            profileService,
            skillRegistry,
            mcpToolRegistry,
            messageHistoryService,
            memoryRecallService
    );

    @Test
    void buildContextCombinesProfilePromptHistoryMemoryAndTools() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of(
                message("user", "My name is Ada.", 4),
                message("assistant", "Nice to meet you.", 5)
        ));
        when(memoryRecallService.recall(1L, 20001L, 10001L, 50001L, "Please calculate 128 * 36 + 59.", 5))
                .thenReturn(List.of(memory("Ada likes concise answers.")));

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "Please calculate 128 * 36 + 59.",
                1_000
        ));

        assertThat(result.profile().profileId()).isEqualTo(50001L);
        assertThat(result.modelConfigId()).isEqualTo(30001L);
        assertThat(result.messages()).extracting("role")
                .containsExactly("system", "user", "assistant", "user");
        assertThat(result.messages().get(0).content())
                .contains("You are AgentX")
                .contains("Be concise.")
                .contains("Ada likes concise answers.")
                .contains("calculator")
                .contains("read_file");
        assertThat(result.availableSkills()).extracting("code").containsExactly("calculator");
        assertThat(result.availableMcpTools()).extracting("name").containsExactly("read_file");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void buildContextTrimsOldHistoryWhenTokenBudgetIsSmall() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of(
                message("user", "old message should be trimmed because it is far from the current turn", 20),
                message("assistant", "recent answer should remain", 5)
        ));
        when(memoryRecallService.recall(1L, 20001L, 10001L, 50001L, "current question", 5))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "current question",
                80
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.messages()).extracting("content")
                .doesNotContain("old message should be trimmed because it is far from the current turn")
                .contains("recent answer should remain", "current question");
    }

    private ProfileDTO profile() {
        return new ProfileDTO(
                50001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage 1 profile",
                30001L,
                "Be concise.",
                objectMapper.createObjectNode(),
                6,
                "PRIVATE",
                "DRAFT",
                List.of(new ProfileSkillBindingDTO(1L, true, false)),
                List.of(new ProfileMcpToolBindingDTO(1L, true))
        );
    }

    private SkillDTO skill() {
        return new SkillDTO(
                1L,
                "calculator",
                "Calculator",
                "Evaluate arithmetic expressions.",
                "BUILTIN",
                "GLOBAL",
                "ENABLED",
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private McpToolDTO mcpTool() {
        return new McpToolDTO(
                1L,
                1L,
                "read_file",
                "Read a demo file.",
                "AVAILABLE",
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private ConversationMessageDTO message(String role, String content, int tokens) {
        return new ConversationMessageDTO(role, content, tokens);
    }

    private MemoryDTO memory(String content) {
        return new MemoryDTO("LONG_TERM", content);
    }
}
