package com.ls.agent.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.context.application.DefaultAgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.profile.dto.ProfileMcpToolBindingDTO;
import com.ls.agent.core.profile.dto.ProfileSkillBindingDTO;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAgentContextBuilderTest {

    private final ProfileService profileService = mock(ProfileService.class);
    private final SkillRegistry skillRegistry = mock(SkillRegistry.class);
    private final McpToolRegistry mcpToolRegistry = mock(McpToolRegistry.class);
    private final MessageHistoryService messageHistoryService = mock(MessageHistoryService.class);
    private final MemoryRecallService memoryRecallService = mock(MemoryRecallService.class);
    private final ModelConfigService modelConfigService = mock(ModelConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentContextBuilder builder = new DefaultAgentContextBuilder(
            profileService,
            skillRegistry,
            mcpToolRegistry,
            messageHistoryService,
            memoryRecallService,
            modelConfigService
    );

    @Test
    void buildContextCombinesProfilePromptHistoryMemoryAndTools() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq(90001L), anyInt())).thenReturn(List.of(
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
                1_000,
                null,
                null
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
                90,
                null,
                null
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.messages()).extracting("content")
                .doesNotContain("old message should be trimmed because it is far from the current turn")
                .contains("recent answer should remain", "current question");
    }

    @Test
    void buildContextUsesSelectedToolsWithinProfileBindings() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profileWithTwoToolBindings());
        when(skillRegistry.listAvailableSkills(1L, List.of(2L))).thenReturn(List.of(weatherSkill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(2L))).thenReturn(List.of(searchFileTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, null, 20)).thenReturn(List.of());
        when(memoryRecallService.recall(1L, 20001L, 10001L, 50001L, "weather now", 5)).thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                null,
                "weather now",
                1_000,
                List.of(2L),
                List.of(2L)
        ));

        assertThat(result.availableSkills()).extracting("skillId").containsExactly(2L);
        assertThat(result.availableMcpTools()).extracting("mcpToolId").containsExactly(2L);
    }

    @Test
    void buildContextRejectsDisabledProfile() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(disabledProfile());

        assertThatThrownBy(() -> builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                null,
                "hello",
                1_000,
                null,
                null
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void buildContextUsesModelConfigTokenLimitWhenCommandLimitIsMissing() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(modelConfigService.getActiveModelConfig(30001L)).thenReturn(modelConfig(90));
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
                null,
                null,
                null
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.messages()).extracting("content")
                .doesNotContain("old message should be trimmed because it is far from the current turn")
                .contains("recent answer should remain");
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
                "BASIC",
                "PRIVATE",
                "DRAFT",
                List.of(new ProfileSkillBindingDTO(1L, true, false)),
                List.of(new ProfileMcpToolBindingDTO(1L, true))
        );
    }

    private ProfileDTO profileWithTwoToolBindings() {
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
                "BASIC",
                "PRIVATE",
                "DRAFT",
                List.of(
                        new ProfileSkillBindingDTO(1L, true, false),
                        new ProfileSkillBindingDTO(2L, false, false)
                ),
                List.of(
                        new ProfileMcpToolBindingDTO(1L, true),
                        new ProfileMcpToolBindingDTO(2L, false)
                )
        );
    }

    private ProfileDTO disabledProfile() {
        ProfileDTO profile = profile();
        return new ProfileDTO(
                profile.profileId(),
                profile.applicationId(),
                profile.name(),
                profile.profileType(),
                profile.description(),
                profile.modelConfigId(),
                profile.promptExtra(),
                profile.memoryStrategy(),
                profile.maxSteps(),
                profile.executionMode(),
                profile.visibility(),
                "DISABLED",
                profile.skillBindings(),
                profile.mcpToolBindings()
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

    private SkillDTO weatherSkill() {
        return new SkillDTO(
                2L,
                "weather",
                "Weather",
                "Return current weather.",
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

    private McpToolDTO searchFileTool() {
        return new McpToolDTO(
                2L,
                1L,
                "search_file",
                "Search a demo file.",
                "AVAILABLE",
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private ModelConfigDTO modelConfig(Integer maxContextTokens) {
        return new ModelConfigDTO(
                30001L,
                1L,
                "mock-chat",
                "Mock Chat",
                objectMapper.createObjectNode(),
                null,
                maxContextTokens,
                "ACTIVE"
        );
    }

    private ConversationMessageDTO message(String role, String content, int tokens) {
        return new ConversationMessageDTO(null, role, content, tokens, null);
    }

    private MemoryDTO memory(String content) {
        return new MemoryDTO("LONG_TERM", content);
    }
}
