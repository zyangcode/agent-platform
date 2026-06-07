package com.ls.agent.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.context.application.DefaultAgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.context.dto.ContextBudgetSnapshotDTO;
import com.ls.agent.core.experience.api.ExperienceSkillResolver;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.dto.MemoryRecallFilter;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.profile.dto.ProfileMcpToolBindingDTO;
import com.ls.agent.core.profile.dto.ProfileSkillBindingDTO;
import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentContextBuilderTest {

    private final ProfileService profileService = mock(ProfileService.class);
    private final SkillRegistry skillRegistry = mock(SkillRegistry.class);
    private final McpToolRegistry mcpToolRegistry = mock(McpToolRegistry.class);
    private final MessageHistoryService messageHistoryService = mock(MessageHistoryService.class);
    private final MemoryRecallService memoryRecallService = mock(MemoryRecallService.class);
    private final ModelConfigService modelConfigService = mock(ModelConfigService.class);
    private final ExperienceSkillResolver experienceSkillResolver = mock(ExperienceSkillResolver.class);
    private final RagSearchService ragSearchService = mock(RagSearchService.class);
    private final TraceService traceService = mock(TraceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentContextBuilder builder = new DefaultAgentContextBuilder(
            profileService,
            skillRegistry,
            mcpToolRegistry,
            messageHistoryService,
            memoryRecallService,
            modelConfigService,
            experienceSkillResolver,
            ragSearchService,
            traceService
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
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("Please calculate 128 * 36 + 59."), any(MemoryRecallFilter.class)))
                .thenReturn(List.of(memory("Ada likes concise answers.")));
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("Please calculate 128 * 36 + 59."), anyInt()))
                .thenReturn(List.of());

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
        assertThat(result.apiMessages()).extracting("role")
                .containsExactly("system", "user", "assistant", "user");
        assertThat(result.apiMessages().get(0).content())
                .contains("You are Nexus")
                .contains("Act directly")
                .contains("@skill:name")
                .contains("Be concise.")
                .contains("Ada likes concise answers.")
                .contains("calculator")
                .contains("read_file");
        assertThat(result.conversationMessages()).extracting("role")
                .containsExactly("user", "assistant", "user");
        assertThat(result.conversationMessages()).extracting("content")
                .containsExactly("My name is Ada.", "Nice to meet you.", "Please calculate 128 * 36 + 59.");
        assertThat(result.conversationMessages()).extracting("content")
                .allSatisfy(content -> assertThat(String.valueOf(content))
                        .doesNotContain("Ada likes concise answers.")
                        .doesNotContain("calculator")
                        .doesNotContain("read_file")
                        .doesNotContain("You are Nexus"));
        assertThat(result.availableSkills()).extracting("code").containsExactly("calculator");
        assertThat(result.availableMcpTools()).extracting("name").containsExactly("read_file");
        assertThat(result.truncated()).isFalse();
        ContextBudgetSnapshotDTO budget = result.contextBudgetSnapshot();
        assertThat(budget.maxContextTokens()).isEqualTo(1_000);
        assertThat(budget.systemTokens()).isPositive();
        assertThat(budget.profileTokens()).isPositive();
        assertThat(budget.historyTokens()).isPositive();
        assertThat(budget.memoryTokens()).isPositive();
        assertThat(budget.toolsTokens()).isPositive();
        assertThat(budget.experienceTokens()).isZero();
        assertThat(budget.ragTokens()).isZero();
        assertThat(budget.currentInputTokens()).isPositive();
        assertThat(budget.apiMessagesTokens()).isEqualTo(result.estimatedTokens());
        assertThat(budget.remainingTokens()).isEqualTo(1_000 - result.estimatedTokens());
        assertThat(budget.truncated()).isFalse();

        ArgumentCaptor<MemoryRecallFilter> memoryFilterCaptor = ArgumentCaptor.forClass(MemoryRecallFilter.class);
        verify(memoryRecallService).recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("Please calculate 128 * 36 + 59."), memoryFilterCaptor.capture());
        assertThat(memoryFilterCaptor.getValue().categories()).containsExactly("summary", "preference", "fact");
        assertThat(memoryFilterCaptor.getValue().topK()).isEqualTo(5);
    }

    @Test
    void buildContextRecordsExperienceResolveAndApiMessagesComposeSpans() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("draft a refund reply"), any(MemoryRecallFilter.class), eq("trace-ctx"), any()))
                .thenReturn(List.of());
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("draft a refund reply"), anyInt()))
                .thenReturn(List.of());
        when(experienceSkillResolver.resolve(1L, 20001L, 10001L, 50001L, "GENERAL", "draft a refund reply", 3))
                .thenReturn(List.of(experienceSkill("support-refund-tone", "Use empathy first.")));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    return new TraceSpanDTO(100L + Math.abs(command.spanName().hashCode()), command.traceId(), command.parentSpanId(),
                            command.spanName(), command.spanType(), command.component(), "RUNNING", null, null,
                            null, null, null, command.attributes(), null);
                });

        builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "draft a refund reply",
                1_000,
                null,
                null,
                "trace-ctx",
                42L
        ));

        ArgumentCaptor<StartTraceSpanCommand> captor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(10)).startSpan(captor.capture());
        assertThat(captor.getAllValues()).extracting("spanName")
                .containsExactly(
                        "memory.recall",
                        "experience.resolve",
                        "api.messages.compose",
                        "rag.search",
                        "context.slot.compose",
                        "context.slot.compose",
                        "context.slot.compose",
                        "context.slot.compose",
                        "context.slot.compose",
                        "context.slot.compose"
                );
        assertThat(captor.getAllValues()).allSatisfy(command -> {
            assertThat(command.traceId()).isEqualTo("trace-ctx");
            assertThat(command.parentSpanId()).isEqualTo(42L);
            assertThat(command.spanType()).isEqualTo("CONTEXT");
        });
        StartTraceSpanCommand memorySpan = captor.getAllValues().get(0);
        assertThat(memorySpan.attributes().get("limit").asInt()).isEqualTo(5);
        assertThat(memorySpan.attributes().get("recalledCount").asInt()).isZero();
        StartTraceSpanCommand experienceSpan = captor.getAllValues().get(1);
        assertThat(experienceSpan.attributes().get("profileType").asText()).isEqualTo("GENERAL");
        assertThat(experienceSpan.attributes().get("limit").asInt()).isEqualTo(3);
        assertThat(experienceSpan.attributes().get("resolvedCount").asInt()).isEqualTo(1);
        StartTraceSpanCommand composeSpan = captor.getAllValues().get(2);
        assertThat(composeSpan.attributes().get("maxContextTokens").asInt()).isEqualTo(1_000);
        assertThat(composeSpan.attributes().get("apiMessages").asInt()).isEqualTo(2);
        assertThat(composeSpan.attributes().get("conversationMessages").asInt()).isEqualTo(1);
        assertThat(composeSpan.attributes().get("estimatedTokens").asInt()).isPositive();
        StartTraceSpanCommand ragSpan = captor.getAllValues().get(3);
        assertThat(ragSpan.attributes().get("topK").asInt()).isEqualTo(5);
        assertThat(ragSpan.attributes().get("searchService").asText()).isNotBlank();
        assertThat(ragSpan.attributes().has("vectorStoreAvailable")).isFalse();
        assertThat(ragSpan.attributes().get("returnedCount").asInt()).isZero();
        assertThat(captor.getAllValues().subList(4, 10))
                .extracting(command -> command.attributes().get("slotKind").asText())
                .containsExactly("PROFILE", "TASK_MEMORY", "EXPERIENCE", "RAG_RECALL", "TOOLS", "HISTORY");
        assertThat(captor.getAllValues().subList(4, 10))
                .allSatisfy(command -> {
                    assertThat(command.attributes().get("tokenBudget").asInt()).isGreaterThanOrEqualTo(0);
                    assertThat(command.attributes().get("usedTokens").asInt()).isGreaterThanOrEqualTo(0);
                    assertThat(command.attributes().get("truncated").asBoolean()).isFalse();
                });
    }

    @Test
    void buildContextInjectsRagReferencesIntoApiMessagesOnlyAndBudget() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("how to play basketball safely in hot weather"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of());
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("how to play basketball safely in hot weather"), anyInt()))
                .thenReturn(List.of(new RagSearchResultDTO(
                        10L,
                        100L,
                        "Outdoor Basketball Safety",
                        "Avoid noon exercise during high heat and prefer shaded courts.",
                        "kb://sports/safety",
                        0.88
                )));

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "how to play basketball safely in hot weather",
                1_000,
                null,
                null
        ));

        assertThat(result.apiMessages().get(0).content())
                .contains("RAG references:")
                .contains("Outdoor Basketball Safety")
                .contains("Avoid noon exercise during high heat");
        assertThat(result.conversationMessages()).extracting("content")
                .doesNotContain("Avoid noon exercise during high heat and prefer shaded courts.");
        assertThat(result.contextBudgetSnapshot().ragTokens()).isPositive();
        assertThat(result.contextBudgetSnapshot().memoryTokens()).isZero();
    }

    @Test
    void buildContextSkipsLongTermMemoryWhenProfileMemoryStrategyIsDisabled() {
        when(profileService.getProfile(1L, 10001L, 50001L))
                .thenReturn(profileWithMemoryStrategy("DISABLED"));
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .thenReturn(List.of());
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("remember nothing"), anyInt()))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "remember nothing",
                1_000,
                null,
                null
        ));

        verify(memoryRecallService, never()).recall(any(), any(), any(), any(), any(), any());
        assertThat(result.apiMessages().get(0).content()).doesNotContain("Long-term memories:");
        assertThat(result.contextBudgetSnapshot().memoryTokens()).isZero();
    }

    @Test
    void buildContextSkipsLongTermMemoryWhenProfileMemoryStrategyIsSessionOnly() {
        when(profileService.getProfile(1L, 10001L, 50001L))
                .thenReturn(profileWithMemoryStrategy("SESSION_ONLY"));
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .thenReturn(List.of());
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("session only"), anyInt()))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "session only",
                1_000,
                null,
                null
        ));

        verify(memoryRecallService, never()).recall(any(), any(), any(), any(), any(), any());
        assertThat(result.apiMessages().get(0).content()).doesNotContain("Long-term memories:");
        assertThat(result.contextBudgetSnapshot().memoryTokens()).isZero();
    }

    @Test
    void buildContextRecallsLongTermMemoryWhenProfileMemoryStrategyIsReadOnly() {
        when(profileService.getProfile(1L, 10001L, 50001L))
                .thenReturn(profileWithMemoryStrategy("READ_ONLY"));
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("read memory"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of(memory("Read-only memory can still be injected.")));
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("read memory"), anyInt()))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "read memory",
                1_000,
                null,
                null
        ));

        verify(memoryRecallService).recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("read memory"), any(MemoryRecallFilter.class));
        assertThat(result.apiMessages().get(0).content()).contains("Read-only memory can still be injected.");
        assertThat(result.contextBudgetSnapshot().memoryTokens()).isPositive();
    }

    @Test
    void buildContextRecallsLongTermMemoryWhenProfileMemoryStrategyIsReadWrite() {
        when(profileService.getProfile(1L, 10001L, 50001L))
                .thenReturn(profileWithMemoryStrategy("READ_WRITE"));
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("read and write memory"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of(memory("Read-write memory can be injected.")));
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("read and write memory"), anyInt()))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "read and write memory",
                1_000,
                null,
                null
        ));

        verify(memoryRecallService).recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("read and write memory"), any(MemoryRecallFilter.class));
        assertThat(result.apiMessages().get(0).content()).contains("Read-write memory can be injected.");
        assertThat(result.contextBudgetSnapshot().memoryTokens()).isPositive();
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
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("current question"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "current question",
                100,
                null,
                null
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.apiMessages()).extracting("content")
                .doesNotContain("old message should be trimmed because it is far from the current turn")
                .contains("recent answer should remain", "current question");
        assertThat(result.conversationMessages()).extracting("content")
                .doesNotContain("old message should be trimmed because it is far from the current turn")
                .contains("recent answer should remain", "current question");
    }

    @Test
    void buildContextAutoCompactsOldHistoryWhenBudgetIsTightButRecentTurnsFit() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of(
                message("user", "old investigation " + "a".repeat(240), 70),
                message("assistant", "old detailed answer " + "b".repeat(240), 70),
                message("user", "recent question", 4),
                message("assistant", "recent answer", 4)
        ));
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("current question"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "current question",
                120,
                null,
                null
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.apiMessages()).extracting("content")
                .anySatisfy(content -> assertThat(String.valueOf(content)).contains("[compact.auto]"))
                .contains("recent question", "recent answer", "current question");
        assertThat(result.conversationMessages()).extracting("content")
                .anySatisfy(content -> assertThat(String.valueOf(content)).contains("[compact.auto]"))
                .contains("recent question", "recent answer", "current question");
        assertThat(result.conversationMessages()).extracting("content")
                .doesNotContain("old investigation " + "a".repeat(240))
                .doesNotContain("old detailed answer " + "b".repeat(240));
        assertThat(result.contextBudgetSnapshot().historyTokens()).isGreaterThan(0);
        assertThat(result.contextBudgetSnapshot().apiMessagesTokens()).isLessThanOrEqualTo(120);
    }

    @Test
    void buildContextFallsBackToTrimAndInjectsDegradedNoticeWhenSummaryCannotFit() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of(
                message("user", "very old " + "x".repeat(300), 80),
                message("assistant", "less old " + "y".repeat(300), 80),
                message("user", "recent but too expensive", 40),
                message("assistant", "recent answer also too expensive", 40)
        ));
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("current"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "current",
                95,
                null,
                null
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.apiMessages()).extracting("content")
                .anySatisfy(content -> assertThat(String.valueOf(content)).contains("[compact.auto] older history summarized"))
                .contains("current")
                .doesNotContain("very old " + "x".repeat(300));
        assertThat(result.conversationMessages()).extracting("content")
                .anySatisfy(content -> assertThat(String.valueOf(content)).contains("[compact.auto] older history summarized"))
                .contains("current");
        assertThat(result.contextBudgetSnapshot().apiMessagesTokens()).isLessThanOrEqualTo(100);
    }

    @Test
    void buildContextUsesSelectedToolsWithinProfileBindings() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profileWithTwoToolBindings());
        when(skillRegistry.listAvailableSkills(1L, List.of(2L))).thenReturn(List.of(weatherSkill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(2L))).thenReturn(List.of(searchFileTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, null, 20)).thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("weather now"), any(MemoryRecallFilter.class))).thenReturn(List.of());

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
    void buildContextTreatsExplicitEmptySelectedToolsAsNoTools() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profileWithTwoToolBindings());
        when(skillRegistry.listAvailableSkills(1L, List.of())).thenReturn(List.of());
        when(mcpToolRegistry.listAvailableTools(1L, List.of())).thenReturn(List.of());
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, null, 20)).thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("no tools please"), any(MemoryRecallFilter.class))).thenReturn(List.of());
        when(ragSearchService.search(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("no tools please"), anyInt()))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                null,
                "no tools please",
                1_000,
                List.of(),
                List.of()
        ));

        assertThat(result.availableSkills()).isEmpty();
        assertThat(result.availableMcpTools()).isEmpty();
        assertThat(result.apiMessages().get(0).content())
                .doesNotContain("Available skills:")
                .doesNotContain("Available MCP tools:")
                .doesNotContain("calculator")
                .doesNotContain("weather")
                .doesNotContain("read_file")
                .doesNotContain("search_file");
        verify(skillRegistry).listAvailableSkills(1L, List.of());
        verify(mcpToolRegistry).listAvailableTools(1L, List.of());
    }

    @Test
    void buildContextInjectsExperienceRefsIntoApiMessagesOnlyAndBudget() {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("draft a refund reply"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of());
        when(experienceSkillResolver.resolve(1L, 20001L, 10001L, 50001L, "GENERAL", "draft a refund reply", 3))
                .thenReturn(List.of(new ExperienceSkillDTO(
                        70001L,
                        "support-refund-tone",
                        "Support refund tone",
                        "GENERAL",
                        List.of(),
                        "Use empathy first. Confirm policy second. Keep the final reply under 120 words."
                )));

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "draft a refund reply",
                1_000,
                null,
                null
        ));

        assertThat(result.apiMessages().get(0).content())
                .contains("Experience refs:")
                .contains("support-refund-tone")
                .contains("Use empathy first");
        assertThat(result.conversationMessages()).extracting("content")
                .doesNotContain("Use empathy first. Confirm policy second. Keep the final reply under 120 words.");
        assertThat(result.availableSkills()).extracting("code").containsExactly("calculator");
        assertThat(result.contextBudgetSnapshot().experienceTokens()).isPositive();
        assertThat(result.contextBudgetSnapshot().toolsTokens()).isPositive();
    }

    @Test
    void buildContextScalesMemoryAndExperienceBudgetsDownForSmallContextWindow() {
        String largeMemory = "small window memory " + "m".repeat(360);
        String largeExperience = "small window experience " + "e".repeat(360);
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("short question"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of(memory(largeMemory)));
        when(experienceSkillResolver.resolve(1L, 20001L, 10001L, 50001L, "GENERAL", "short question", 3))
                .thenReturn(List.of(new ExperienceSkillDTO(
                        70001L,
                        "small-window-exp",
                        "Small Window Experience",
                        "GENERAL",
                        List.of(),
                        largeExperience
                )));

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "short question",
                400,
                null,
                null
        ));

        assertThat(result.apiMessages().get(0).content())
                .doesNotContain(largeMemory)
                .doesNotContain(largeExperience);
    }

    @Test
    void buildContextScalesMemoryAndExperienceBudgetsUpForLargeContextWindow() {
        String memoryOne = "large window memory one " + "a".repeat(400);
        String memoryTwo = "large window memory two " + "b".repeat(400);
        String memoryThree = "large window memory three " + "c".repeat(400);
        String memoryFour = "large window memory four " + "d".repeat(400);
        String experienceOne = "large window experience one " + "e".repeat(840);
        String experienceTwo = "large window experience two " + "f".repeat(840);
        String experienceThree = "large window experience three " + "g".repeat(840);
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .thenReturn(List.of());
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("broad question"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of(
                        memory(memoryOne),
                        memory(memoryTwo),
                        memory(memoryThree),
                        memory(memoryFour)
                ));
        when(experienceSkillResolver.resolve(1L, 20001L, 10001L, 50001L, "GENERAL", "broad question", 3))
                .thenReturn(List.of(
                        experienceSkill("large-window-exp-1", experienceOne),
                        experienceSkill("large-window-exp-2", experienceTwo),
                        experienceSkill("large-window-exp-3", experienceThree)
                ));

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "broad question",
                16_000,
                null,
                null
        ));

        assertThat(result.apiMessages().get(0).content())
                .contains(memoryOne)
                .contains(memoryTwo)
                .contains(memoryThree)
                .contains(memoryFour)
                .contains(experienceOne)
                .contains(experienceTwo)
                .contains(experienceThree);
    }

    @Test
    void buildContextScalesAutoCompactSummarySampleUpForLargeContextWindow() {
        String oldMessage = "old context " + "x".repeat(150) + "DYNAMIC_SAMPLE_MARKER" + "y".repeat(200);
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of(
                message("user", oldMessage, 100),
                message("user", "recent question", 4),
                message("assistant", "recent answer", 4)
        ));
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("current question"), any(MemoryRecallFilter.class)))
                .thenReturn(List.of());

        AgentContextDTO result = builder.build(new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "current question",
                16_000,
                null,
                null
        ));

        assertThat(result.conversationMessages()).extracting("content")
                .anySatisfy(content -> assertThat(String.valueOf(content))
                        .contains("[compact.auto]")
                        .contains("DYNAMIC_SAMPLE_MARKER"));
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
        when(modelConfigService.getActiveModelConfig(30001L)).thenReturn(modelConfig(100));
        when(skillRegistry.listAvailableSkills(1L, List.of(1L))).thenReturn(List.of(skill()));
        when(mcpToolRegistry.listAvailableTools(1L, List.of(1L))).thenReturn(List.of(mcpTool()));
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20)).thenReturn(List.of(
                message("user", "old message should be trimmed because it is far from the current turn", 20),
                message("assistant", "recent answer should remain", 5)
        ));
        when(memoryRecallService.recall(eq(1L), eq(20001L), eq(10001L), eq(50001L), eq("current question"), any(MemoryRecallFilter.class)))
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
        assertThat(result.apiMessages()).extracting("content")
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

    private ProfileDTO profileWithMemoryStrategy(String mode) {
        ProfileDTO profile = profile();
        return new ProfileDTO(
                profile.profileId(),
                profile.applicationId(),
                profile.name(),
                profile.profileType(),
                profile.description(),
                profile.modelConfigId(),
                profile.promptExtra(),
                objectMapper.createObjectNode().put("mode", mode),
                profile.maxSteps(),
                profile.executionMode(),
                profile.visibility(),
                profile.status(),
                profile.skillBindings(),
                profile.mcpToolBindings()
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

    private ExperienceSkillDTO experienceSkill(String code, String content) {
        return new ExperienceSkillDTO(
                70001L,
                code,
                "Large Window Experience",
                "GENERAL",
                List.of(),
                content
        );
    }
}
