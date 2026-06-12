package com.ls.agent.core.context.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.context.dto.ContextBudgetSnapshotDTO;
import com.ls.agent.core.context.dto.ContextSchema;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.experience.api.ExperienceSkillResolver;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.dto.MemoryRecallFilter;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class DefaultAgentContextBuilder implements AgentContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentContextBuilder.class);

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 4_000;
    private static final int HISTORY_LIMIT = 20;
    private static final int MEMORY_LIMIT = 5;
    private static final int RAG_LIMIT = 5;
    private static final int EXPERIENCE_LIMIT = 3;
    private static final int RETRIEVAL_TIMEOUT_MILLIS = 1_500;
    private static final int MIN_MEMORY_TOKEN_BUDGET = 80;
    private static final int MAX_MEMORY_TOKEN_BUDGET = 1_200;
    private static final int MIN_EXPERIENCE_TOKEN_BUDGET = 80;
    private static final int MAX_EXPERIENCE_TOKEN_BUDGET = 1_600;
    private static final int MIN_RAG_TOKEN_BUDGET = 120;
    private static final int MAX_RAG_TOKEN_BUDGET = 2_000;
    private static final int MIN_AUTO_COMPACT_CHARS = 180;
    private static final int MAX_AUTO_COMPACT_CHARS = 1_200;
    private static final int MIN_AUTO_COMPACT_SAMPLE_CHARS = 40;
    private static final int MAX_AUTO_COMPACT_SAMPLE_CHARS = 240;
    private static final int RECENT_HISTORY_RAW_MESSAGES = 2;
    private static final String PROFILE_STATUS_DRAFT = "DRAFT";
    private static final String PROFILE_STATUS_PUBLISHED = "PUBLISHED";

    private final ProfileService profileService;
    private final SkillRegistry skillRegistry;
    private final McpToolRegistry mcpToolRegistry;
    private final MessageHistoryService messageHistoryService;
    private final MemoryRecallService memoryRecallService;
    private final ModelConfigService modelConfigService;
    private final ExperienceSkillResolver experienceSkillResolver;
    private final RagSearchService ragSearchService;
    private final TraceService traceService;
    private final EmbeddingService embeddingService;
    private final ExecutorService retrievalExecutor;

    public DefaultAgentContextBuilder(
            ProfileService profileService,
            SkillRegistry skillRegistry,
            McpToolRegistry mcpToolRegistry,
            MessageHistoryService messageHistoryService,
            MemoryRecallService memoryRecallService,
            ModelConfigService modelConfigService,
            ExperienceSkillResolver experienceSkillResolver,
            RagSearchService ragSearchService,
            TraceService traceService
    ) {
        this(profileService, skillRegistry, mcpToolRegistry, messageHistoryService, memoryRecallService,
                modelConfigService, experienceSkillResolver, ragSearchService, traceService, null);
    }

    @Autowired
    public DefaultAgentContextBuilder(
            ProfileService profileService,
            SkillRegistry skillRegistry,
            McpToolRegistry mcpToolRegistry,
            MessageHistoryService messageHistoryService,
            MemoryRecallService memoryRecallService,
            ModelConfigService modelConfigService,
            ExperienceSkillResolver experienceSkillResolver,
            RagSearchService ragSearchService,
            TraceService traceService,
            EmbeddingService embeddingService
    ) {
        this.profileService = profileService;
        this.skillRegistry = skillRegistry;
        this.mcpToolRegistry = mcpToolRegistry;
        this.messageHistoryService = messageHistoryService;
        this.memoryRecallService = memoryRecallService;
        this.modelConfigService = modelConfigService;
        this.experienceSkillResolver = experienceSkillResolver;
        this.ragSearchService = ragSearchService;
        this.traceService = traceService;
        this.embeddingService = embeddingService;
        this.retrievalExecutor = Executors.newFixedThreadPool(4, daemonThreadFactory());
    }

    @PreDestroy
    public void shutdown() {
        retrievalExecutor.shutdownNow();
    }

    @Override
    public AgentContextDTO build(BuildAgentContextCommand command) {
        validate(command);
        ProfileDTO profile = profileService.getProfile(command.tenantId(), command.userId(), command.profileId());
        if (!command.applicationId().equals(profile.applicationId())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Profile does not belong to application");
        }
        if (!isRunnableProfile(profile)) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Profile is unavailable");
        }
        int maxTokens = resolveMaxContextTokens(command, profile);
        ContextBudgetPolicy budgetPolicy = ContextBudgetPolicy.from(maxTokens);

        List<Long> skillIds = resolveSkillIds(profile, command.selectedSkillIds());
        List<Long> mcpToolIds = resolveMcpToolIds(profile, command.selectedMcpToolIds());
        List<SkillDTO> skills = skillRegistry.listAvailableSkills(command.tenantId(), skillIds);
        List<McpToolDTO> mcpTools = mcpToolRegistry.listAvailableTools(command.tenantId(), mcpToolIds);
        RetrievalResult retrievalResult = retrieveMemoryAndRag(command, profile, budgetPolicy.ragTokenBudget());
        List<MemoryDTO> memories = retrievalResult.memories();
        List<RagSearchResultDTO> ragResults = retrievalResult.ragResults();
        log.info("[CONTEXT] retrievalDone profileId={} memories={} ragResults={} maxTokens={}",
                command.profileId(), memories.size(), ragResults.size(), maxTokens);
        List<ExperienceSkillDTO> experienceSkills = resolveExperienceSkills(command, profile);
        List<ConversationMessageDTO> history = messageHistoryService.listRecentMessages(
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                command.conversationId(),
                HISTORY_LIMIT
        );

        TraceSpanDTO composeSpan = safeStartSpan(command.traceId(), command.parentSpanId(), "api.messages.compose", "CONTEXT",
                attributes().put("maxContextTokens", maxTokens));
        ContextBlocks contextBlocks;
        HistoryWindow historyWindow;
        List<ModelMessage> conversationMessages;
        List<ModelMessage> apiMessages;
        ContextBudgetSnapshotDTO snapshot;
        try {
            contextBlocks = buildContextBlocks(
                    profile,
                    skills,
                    mcpTools,
                    memories,
                    experienceSkills,
                    budgetPolicy.memoryTokenBudget(),
                    budgetPolicy.experienceTokenBudget(),
                    budgetPolicy.ragTokenBudget(),
                    ragResults,
                    command
            );
            String systemPrompt = contextBlocks.systemPrompt();
            int reservedTokens = estimateTokens(systemPrompt) + estimateTokens(command.userInput());
            historyWindow = compactHistory(history, maxTokens - reservedTokens, budgetPolicy);
            List<ModelMessage> keptHistory = historyWindow.messages();
            boolean truncated = historyWindow.truncated();

            conversationMessages = new ArrayList<>(keptHistory);
            conversationMessages.add(new ModelMessage("user", command.userInput()));

            apiMessages = new ArrayList<>();
            apiMessages.add(new ModelMessage("system", systemPrompt));
            apiMessages.addAll(conversationMessages);
            int estimatedTokens = estimateMessagesTokens(apiMessages);
            snapshot = new ContextBudgetSnapshotDTO(
                    maxTokens,
                    contextBlocks.platformSystemTokens(),
                    contextBlocks.profileTokens(),
                    historyWindow.tokens(),
                    contextBlocks.memoryTokens(),
                    contextBlocks.toolsTokens(),
                    contextBlocks.experienceTokens(),
                    contextBlocks.ragTokens(),
                    estimateTokens(command.userInput()),
                    estimatedTokens,
                    Math.max(0, maxTokens - estimatedTokens),
                    truncated
            );
            log.info("[CONTEXT] assembled maxTokens={} system={} profile={} history={} memory={} tools={} experience={} rag={} total={} remaining={} truncated={}",
                    snapshot.maxContextTokens(), snapshot.systemTokens(), snapshot.profileTokens(),
                    snapshot.historyTokens(), snapshot.memoryTokens(), snapshot.toolsTokens(),
                    snapshot.experienceTokens(), snapshot.ragTokens(),
                    snapshot.apiMessagesTokens(), snapshot.remainingTokens(), snapshot.truncated());
            if (composeSpan != null && composeSpan.attributes() instanceof ObjectNode attributes) {
                attributes.put("apiMessages", apiMessages.size());
                attributes.put("conversationMessages", conversationMessages.size());
                attributes.put("estimatedTokens", estimatedTokens);
                attributes.put("truncated", truncated);
            }
            recordSlotSpan(command, ContextSlotKind.PROFILE, estimateTokens(ProfileSlotSource.PLATFORM_SYSTEM_PROMPT) + budgetPolicy.memoryTokenBudget(), contextBlocks.platformSystemTokens() + contextBlocks.profileTokens(), false);
            recordSlotSpan(command, ContextSlotKind.TASK_MEMORY, budgetPolicy.memoryTokenBudget(), contextBlocks.memoryTokens(), contextBlocks.memoryTokens() > budgetPolicy.memoryTokenBudget());
            recordSlotSpan(command, ContextSlotKind.EXPERIENCE, budgetPolicy.experienceTokenBudget(), contextBlocks.experienceTokens(), contextBlocks.experienceTokens() > budgetPolicy.experienceTokenBudget());
            recordSlotSpan(command, ContextSlotKind.RAG_RECALL, budgetPolicy.ragTokenBudget(), contextBlocks.ragTokens(), contextBlocks.ragTruncated());
            recordSlotSpan(command, ContextSlotKind.TOOLS, maxTokens, contextBlocks.toolsTokens(), false);
            recordSlotSpan(command, ContextSlotKind.HISTORY, Math.max(0, maxTokens - reservedTokens), historyWindow.tokens(), historyWindow.truncated());
            safeFinishSpan(composeSpan, "SUCCESS", null, null);
        } catch (Exception ex) {
            safeFinishSpan(composeSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
        return new AgentContextDTO(
                profile.modelConfigId(),
                profile,
                conversationMessages,
                apiMessages,
                skills,
                mcpTools,
                snapshot.apiMessagesTokens(),
                snapshot.truncated(),
                snapshot,
                contextBlocks.ragResults()
        );
    }

    private RetrievalResult retrieveMemoryAndRag(
            BuildAgentContextCommand command,
            ProfileDTO profile,
            int ragTokenBudget
    ) {
        boolean recallMemory = shouldRecallMemory(profile);
        boolean recallRag = ragSearchService != null;
        CompletableFuture<EmbeddingVectorDTO> queryVectorFuture = (recallMemory || recallRag)
                ? supplyEmbedding(command)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<List<MemoryDTO>> memoryFuture = recallMemory
                ? supplyRetrieval(() -> recallMemories(command, awaitEmbedding(queryVectorFuture)))
                : CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<RagSearchResultDTO>> ragFuture = recallRag
                ? supplyRetrieval(() -> searchRag(command, awaitEmbedding(queryVectorFuture), ragTokenBudget))
                : CompletableFuture.completedFuture(List.of());
        return new RetrievalResult(
                awaitRetrieval(memoryFuture),
                awaitRetrieval(ragFuture)
        );
    }

    private CompletableFuture<EmbeddingVectorDTO> supplyEmbedding(BuildAgentContextCommand command) {
        return CompletableFuture.supplyAsync(() -> precomputeQueryVector(command), retrievalExecutor);
    }

    private EmbeddingVectorDTO precomputeQueryVector(BuildAgentContextCommand command) {
        if (embeddingService == null || command.userInput() == null || command.userInput().isBlank()) {
            return null;
        }
        EmbeddingVectorDTO vector = embeddingService.embed(command.userInput());
        return vector == null || vector.dimension() == 0 ? null : vector;
    }

    private EmbeddingVectorDTO awaitEmbedding(CompletableFuture<EmbeddingVectorDTO> future) {
        try {
            return future.get(RETRIEVAL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private <T> CompletableFuture<List<T>> supplyRetrieval(Supplier<List<T>> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<T> result = supplier.get();
                return result == null ? List.of() : result;
            } catch (RuntimeException ex) {
                return List.of();
            }
        }, retrievalExecutor);
    }

    private <T> List<T> awaitRetrieval(CompletableFuture<List<T>> future) {
        try {
            return future.get(RETRIEVAL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ExperienceSkillDTO> resolveExperienceSkills(BuildAgentContextCommand command, ProfileDTO profile) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), command.parentSpanId(), "experience.resolve", "CONTEXT",
                attributes()
                        .put("profileType", nullToEmpty(profile.profileType()))
                        .put("limit", EXPERIENCE_LIMIT));
        try {
            List<ExperienceSkillDTO> skills = experienceSkillResolver.resolve(
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                profile.profileType(),
                command.userInput(),
                EXPERIENCE_LIMIT
            );
            if (span != null && span.attributes() instanceof ObjectNode attributes) {
                attributes.put("resolvedCount", skills == null ? 0 : skills.size());
            }
            safeFinishSpan(span, "SUCCESS", null, null);
            return skills == null ? List.of() : skills;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private List<MemoryDTO> recallMemories(BuildAgentContextCommand command, EmbeddingVectorDTO queryVector) {
        MemoryRecallFilter profileFilter = MemoryRecallFilter.builder()
                .categories(List.of("summary", "preference", "fact"))
                .memoryScopes(List.of("PROFILE_LONG_TERM"))
                .topK(MEMORY_LIMIT)
                .build();
        MemoryRecallFilter conversationFilter = MemoryRecallFilter.builder()
                .categories(List.of("summary"))
                .memoryScopes(List.of("CONVERSATION_TEMP"))
                .sourceConversationId(command.conversationId())
                .topK(3)
                .build();
        ObjectNode attributes = attributes()
                .put("limit", MEMORY_LIMIT);
        attributes.set("categories", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()
                .add("summary")
                .add("preference")
                .add("fact"));
        TraceSpanDTO span = safeStartSpan(command.traceId(), command.parentSpanId(), "memory.recall", "CONTEXT", attributes);
        try {
            List<MemoryDTO> result = new ArrayList<>();
            result.addAll(recallMemoryWithFilter(command, profileFilter, queryVector, span));
            if (command.conversationId() != null) {
                result.addAll(recallMemoryWithFilter(command, conversationFilter, queryVector, span));
            }
            attributes.put("recalledCount", result.size());
            safeFinishSpan(span, "SUCCESS", null, null);
            return result;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private List<MemoryDTO> recallMemoryWithFilter(
            BuildAgentContextCommand command,
            MemoryRecallFilter filter,
            EmbeddingVectorDTO queryVector,
            TraceSpanDTO span
    ) {
        Long parentSpanId = span == null ? command.parentSpanId() : span.id();
        List<MemoryDTO> memories;
        if (command.traceId() == null || command.traceId().isBlank()) {
            memories = queryVector == null
                    ? memoryRecallService.recall(
                            command.tenantId(),
                            command.applicationId(),
                            command.userId(),
                            command.profileId(),
                            command.userInput(),
                            filter
                    )
                    : memoryRecallService.recall(
                            command.tenantId(),
                            command.applicationId(),
                            command.userId(),
                            command.profileId(),
                            command.userInput(),
                            filter,
                            queryVector,
                            null,
                            null
                    );
        } else {
            memories = queryVector == null
                    ? memoryRecallService.recall(
                            command.tenantId(),
                            command.applicationId(),
                            command.userId(),
                            command.profileId(),
                            command.userInput(),
                            filter,
                            command.traceId(),
                            parentSpanId
                    )
                    : memoryRecallService.recall(
                            command.tenantId(),
                            command.applicationId(),
                            command.userId(),
                            command.profileId(),
                            command.userInput(),
                            filter,
                            queryVector,
                            command.traceId(),
                            parentSpanId
                    );
        }
        return memories == null ? List.of() : memories;
    }

    private List<RagSearchResultDTO> searchRag(
            BuildAgentContextCommand command,
            EmbeddingVectorDTO queryVector,
            int ragTokenBudget
    ) {
        if (ragSearchService == null) {
            return List.of();
        }
        TraceSpanDTO span = safeStartSpan(command.traceId(), command.parentSpanId(), "rag.search", "CONTEXT",
                attributes()
                        .put("topK", RAG_LIMIT)
                        .put("tokenBudget", Math.max(0, ragTokenBudget))
                        .put("searchService", ragSearchService.getClass().getSimpleName())
                        .put("traceAware", command.traceId() != null && !command.traceId().isBlank()));
        try {
            List<RagSearchResultDTO> results;
            if (command.traceId() == null || command.traceId().isBlank()) {
                results = queryVector == null
                        ? ragSearchService.search(
                                command.tenantId(),
                                command.applicationId(),
                                command.userId(),
                                command.profileId(),
                                command.userInput(),
                                RAG_LIMIT
                        )
                        : ragSearchService.search(
                                command.tenantId(),
                                command.applicationId(),
                                command.userId(),
                                command.profileId(),
                                command.userInput(),
                                RAG_LIMIT,
                                queryVector,
                                null,
                                null
                        );
            } else {
                results = queryVector == null
                        ? ragSearchService.search(
                                command.tenantId(),
                                command.applicationId(),
                                command.userId(),
                                command.profileId(),
                                command.userInput(),
                                RAG_LIMIT,
                                command.traceId(),
                                span == null ? command.parentSpanId() : span.id()
                        )
                        : ragSearchService.search(
                                command.tenantId(),
                                command.applicationId(),
                                command.userId(),
                                command.profileId(),
                                command.userInput(),
                                RAG_LIMIT,
                                queryVector,
                                command.traceId(),
                                span == null ? command.parentSpanId() : span.id()
                        );
            }
            List<RagSearchResultDTO> safeResults = results == null ? List.of() : results;
            if (span != null && span.attributes() instanceof ObjectNode attributes) {
                attributes.put("returnedCount", safeResults.size());
            }
            safeFinishSpan(span, "SUCCESS", null, null);
            return safeResults;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", "RAG_SEARCH_FAILED", errorMessage(ex));
            throw ex;
        }
    }

    private boolean shouldRecallMemory(ProfileDTO profile) {
        String mode = memoryStrategyMode(profile);
        return !"DISABLED".equals(mode) && !"SESSION_ONLY".equals(mode);
    }

    private String memoryStrategyMode(ProfileDTO profile) {
        if (profile == null || profile.memoryStrategy() == null || !profile.memoryStrategy().hasNonNull("mode")) {
            return "READ_WRITE";
        }
        String mode = profile.memoryStrategy().get("mode").asText("READ_WRITE");
        return mode == null || mode.isBlank() ? "READ_WRITE" : mode.strip().toUpperCase(Locale.ROOT);
    }

    private void recordSlotSpan(
            BuildAgentContextCommand command,
            ContextSlotKind slotKind,
            int tokenBudget,
            int usedTokens,
            boolean truncated
    ) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), command.parentSpanId(), "context.slot.compose", "CONTEXT",
                attributes()
                        .put("slotKind", slotKind.name())
                        .put("tokenBudget", Math.max(0, tokenBudget))
                        .put("usedTokens", Math.max(0, usedTokens))
                        .put("truncated", truncated));
        safeFinishSpan(span, "SUCCESS", null, null);
    }

    private boolean isRunnableProfile(ProfileDTO profile) {
        return PROFILE_STATUS_DRAFT.equals(profile.status()) || PROFILE_STATUS_PUBLISHED.equals(profile.status());
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

    private int resolveMaxContextTokens(BuildAgentContextCommand command, ProfileDTO profile) {
        if (command.maxContextTokens() != null && command.maxContextTokens() > 0) {
            return command.maxContextTokens();
        }
        ModelConfigDTO modelConfig = modelConfigService.getActiveModelConfig(profile.modelConfigId());
        Integer maxContextTokens = modelConfig.maxContextTokens();
        return maxContextTokens == null || maxContextTokens <= 0 ? DEFAULT_MAX_CONTEXT_TOKENS : maxContextTokens;
    }

    private List<Long> resolveSkillIds(ProfileDTO profile, List<Long> selectedSkillIds) {
        List<Long> boundSkillIds = profile.skillBindings().stream()
                .map(binding -> binding.skillId())
                .toList();
        if (selectedSkillIds != null) {
            return selectedSkillIds.stream()
                    .filter(boundSkillIds::contains)
                    .distinct()
                    .toList();
        }
        return profile.skillBindings().stream()
                .filter(binding -> Boolean.TRUE.equals(binding.enabledByDefault()))
                .map(binding -> binding.skillId())
                .distinct()
                .toList();
    }

    private List<Long> resolveMcpToolIds(ProfileDTO profile, List<Long> selectedMcpToolIds) {
        List<Long> boundToolIds = profile.mcpToolBindings().stream()
                .map(binding -> binding.mcpToolId())
                .toList();
        if (selectedMcpToolIds != null) {
            return selectedMcpToolIds.stream()
                    .filter(boundToolIds::contains)
                    .distinct()
                    .toList();
        }
        return profile.mcpToolBindings().stream()
                .filter(binding -> Boolean.TRUE.equals(binding.enabledByDefault()))
                .map(binding -> binding.mcpToolId())
                .distinct()
                .toList();
    }

    private ContextBlocks buildContextBlocks(
            ProfileDTO profile,
            List<SkillDTO> skills,
            List<McpToolDTO> mcpTools,
            List<MemoryDTO> memories,
            List<ExperienceSkillDTO> experienceSkills,
            int memoryTokenBudget,
            int experienceTokenBudget,
            int ragTokenBudget,
            List<RagSearchResultDTO> ragResults,
            BuildAgentContextCommand command
    ) {
        ProfileSlotSource profileSlotSource = new ProfileSlotSource(profile);
        RagSlotSource ragSlotSource = new RagSlotSource(ragResults);
        ContextSchema schema = new ContextSchema("single-agent-react", List.of(
                ContextSlot.required(ContextSlotKind.PROFILE, Integer.MAX_VALUE),
                ContextSlot.of(ContextSlotKind.TASK_MEMORY, memoryTokenBudget),
                ContextSlot.of(ContextSlotKind.EXPERIENCE, experienceTokenBudget),
                ContextSlot.of(ContextSlotKind.RAG_RECALL, ragTokenBudget),
                ContextSlot.of(ContextSlotKind.TOOLS, Integer.MAX_VALUE)
        ));
        ContextSchemaAssembler.AssembledContext assembled = new ContextSchemaAssembler(List.of(
                profileSlotSource,
                new MemorySlotSource(memories),
                new ExperienceSlotSource(experienceSkills),
                ragSlotSource,
                new ToolsSlotSource(skills, mcpTools)
        )).assemble(schema, command);
        String platformSystem = profileSlotSource.buildPlatformSystemPrompt();
        String profileBlock = profileSlotSource.buildProfileBlock();
        return ContextBlocks.of(
                assembled.systemPrompt(),
                estimateTokens(platformSystem),
                estimateTokens(profileBlock),
                assembled.usedTokens(ContextSlotKind.TASK_MEMORY),
                assembled.usedTokens(ContextSlotKind.TOOLS),
                assembled.usedTokens(ContextSlotKind.EXPERIENCE),
                assembled.usedTokens(ContextSlotKind.RAG_RECALL),
                assembled.truncated(ContextSlotKind.RAG_RECALL),
                ragSlotSource.getLastResults()
        );
    }

    private HistoryWindow compactHistory(
            List<ConversationMessageDTO> history,
            int remainingTokens,
            ContextBudgetPolicy budgetPolicy
    ) {
        if (history == null || history.isEmpty() || remainingTokens <= 0) {
            return new HistoryWindow(List.of(), 0, history != null && !history.isEmpty());
        }
        List<ModelMessage> recent = selectRecentRawMessages(history, remainingTokens);
        int recentTokens = estimateMessagesTokens(recent);
        int olderCount = history.size() - recent.size();
        if (olderCount <= 0) {
            return new HistoryWindow(recent, recentTokens, false);
        }

        String summary = summarizeOldHistory(history.subList(0, olderCount), budgetPolicy);
        int summaryTokens = estimateTokens(summary);
        if (summaryTokens + recentTokens <= remainingTokens) {
            List<ModelMessage> compacted = new ArrayList<>();
            compacted.add(new ModelMessage("system", summary));
            compacted.addAll(recent);
            return new HistoryWindow(compacted, summaryTokens + recentTokens, true);
        }

        String degradedNotice = "[compact.auto.degraded]";
        int degradedTokens = estimateTokens(degradedNotice);
        if (degradedTokens + recentTokens <= remainingTokens) {
            List<ModelMessage> compacted = new ArrayList<>();
            compacted.add(new ModelMessage("system", degradedNotice));
            compacted.addAll(recent);
            return new HistoryWindow(compacted, degradedTokens + recentTokens, true);
        }

        List<ModelMessage> fallback = trimHistory(history, remainingTokens);
        int fallbackTokens = estimateMessagesTokens(fallback);
        if (!fallback.isEmpty() && degradedTokens + fallbackTokens <= remainingTokens) {
            List<ModelMessage> compacted = new ArrayList<>();
            compacted.add(new ModelMessage("system", degradedNotice));
            compacted.addAll(fallback);
            return new HistoryWindow(compacted, degradedTokens + fallbackTokens, true);
        }
        if (!fallback.isEmpty()) {
            return new HistoryWindow(fallback, fallbackTokens, true);
        }
        if (degradedTokens <= remainingTokens) {
            return new HistoryWindow(List.of(new ModelMessage("system", degradedNotice)), degradedTokens, true);
        }
        return new HistoryWindow(List.of(new ModelMessage("system", degradedNotice)), degradedTokens, true);
    }

    private List<ModelMessage> selectRecentRawMessages(List<ConversationMessageDTO> history, int remainingTokens) {
        List<ModelMessage> selected = new ArrayList<>();
        int used = 0;
        int minIndex = Math.max(0, history.size() - RECENT_HISTORY_RAW_MESSAGES);
        for (int i = history.size() - 1; i >= minIndex; i--) {
            ConversationMessageDTO message = history.get(i);
            int tokens = messageTokens(message);
            if (used + tokens > remainingTokens) {
                break;
            }
            selected.add(0, new ModelMessage(message.role(), message.content()));
            used += tokens;
        }
        if (!selected.isEmpty()) {
            return selected;
        }
        return trimHistory(history, remainingTokens);
    }

    private List<ModelMessage> trimHistory(List<ConversationMessageDTO> history, int remainingTokens) {
        if (history == null || history.isEmpty() || remainingTokens <= 0) {
            return List.of();
        }
        List<ModelMessage> selected = new ArrayList<>();
        int used = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMessageDTO message = history.get(i);
            int tokens = messageTokens(message);
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

    private String summarizeOldHistory(List<ConversationMessageDTO> oldHistory, ContextBudgetPolicy budgetPolicy) {
        StringBuilder builder = new StringBuilder();
        builder.append("[compact.auto] older history summarized; ");
        int totalMessages = oldHistory == null ? 0 : oldHistory.size();
        builder.append("messages=").append(totalMessages);
        if (oldHistory == null || oldHistory.isEmpty()) {
            return builder.toString().strip();
        }
        if (totalMessages > 1) {
            return builder.toString().strip();
        }
        builder.append('\n');
        int maxChars = budgetPolicy.autoCompactMaxChars();
        int remainingChars = maxChars - builder.length();
        for (ConversationMessageDTO message : oldHistory) {
            if (remainingChars <= 0) {
                break;
            }
            String sample = sample(message.content(), Math.min(budgetPolicy.autoCompactSampleChars(), remainingChars));
            String line = "- " + nullToEmpty(message.role()) + ": " + sample + '\n';
            if (line.length() > remainingChars) {
                line = line.substring(0, remainingChars);
            }
            builder.append(line);
            remainingChars = maxChars - builder.length();
        }
        return builder.toString().strip();
    }

    private String sample(String content, int maxChars) {
        if (content == null || content.isBlank() || maxChars <= 0) {
            return "";
        }
        String normalized = content.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 3) {
            return normalized.substring(0, maxChars);
        }
        return normalized.substring(0, maxChars - 3) + "...";
    }

    private int messageTokens(ConversationMessageDTO message) {
        return message.tokenCount() == null ? estimateTokens(message.content()) : message.tokenCount();
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

    private ObjectNode attributes() {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }

    private TraceSpanDTO safeStartSpan(String traceId, Long parentSpanId, String spanName, String spanType, ObjectNode attributes) {
        if (traceService == null || traceId == null || traceId.isBlank()) {
            return null;
        }
        try {
            return traceService.startSpan(new StartTraceSpanCommand(
                    traceId,
                    parentSpanId,
                    spanName,
                    spanType,
                    "core",
                    attributes
            ));
        } catch (Exception ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (traceService == null || span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, span.attributes()));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break context assembly.
        }
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "Context assembly failed" : ex.getMessage();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ThreadFactory daemonThreadFactory() {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "context-retrieval-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record RetrievalResult(
            List<MemoryDTO> memories,
            List<RagSearchResultDTO> ragResults
    ) {
        private RetrievalResult {
            memories = memories == null ? List.of() : List.copyOf(memories);
            ragResults = ragResults == null ? List.of() : List.copyOf(ragResults);
        }
    }

    private record ContextBudgetPolicy(
            int memoryTokenBudget,
            int experienceTokenBudget,
            int ragTokenBudget,
            int autoCompactMaxChars,
            int autoCompactSampleChars
    ) {

        private static ContextBudgetPolicy from(int maxContextTokens) {
            int normalizedMaxTokens = Math.max(1, maxContextTokens);
            int autoCompactMaxChars = clamp(
                    normalizedMaxTokens / 8,
                    MIN_AUTO_COMPACT_CHARS,
                    MAX_AUTO_COMPACT_CHARS
            );
            return new ContextBudgetPolicy(
                    clamp((int) Math.ceil(normalizedMaxTokens * 0.08), MIN_MEMORY_TOKEN_BUDGET, MAX_MEMORY_TOKEN_BUDGET),
                    clamp((int) Math.ceil(normalizedMaxTokens * 0.10), MIN_EXPERIENCE_TOKEN_BUDGET, MAX_EXPERIENCE_TOKEN_BUDGET),
                    clamp((int) Math.ceil(normalizedMaxTokens * 0.12), MIN_RAG_TOKEN_BUDGET, MAX_RAG_TOKEN_BUDGET),
                    autoCompactMaxChars,
                    clamp(autoCompactMaxChars / 4, MIN_AUTO_COMPACT_SAMPLE_CHARS, MAX_AUTO_COMPACT_SAMPLE_CHARS)
            );
        }
    }

    private record ContextBlocks(
            String systemPrompt,
            int platformSystemTokens,
            int profileTokens,
            int memoryTokens,
            int toolsTokens,
            int experienceTokens,
            int ragTokens,
            boolean ragTruncated,
            List<com.ls.agent.core.rag.dto.RagSearchResultDTO> ragResults
    ) {
        private static ContextBlocks of(
                String systemPrompt,
                int platformSystemTokens,
                int profileTokens,
                int memoryTokens,
                int toolsTokens,
                int experienceTokens,
                int ragTokens,
                boolean ragTruncated,
                List<com.ls.agent.core.rag.dto.RagSearchResultDTO> ragResults
        ) {
            return new ContextBlocks(
                    systemPrompt, platformSystemTokens, profileTokens,
                    memoryTokens, toolsTokens, experienceTokens, ragTokens,
                    ragTruncated,
                    ragResults == null ? List.of() : ragResults
            );
        }
    }

    private record HistoryWindow(
            List<ModelMessage> messages,
            int tokens,
            boolean truncated
    ) {
        private HistoryWindow {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }
}
