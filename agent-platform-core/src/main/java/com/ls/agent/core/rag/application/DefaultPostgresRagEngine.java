package com.ls.agent.core.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.rag.dto.RagTextChunkDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import com.ls.agent.core.rag.entity.KnowledgeChunkEntity;
import com.ls.agent.core.rag.entity.KnowledgeDocumentEntity;
import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import com.ls.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultPostgresRagEngine implements RagEngine {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double RRF_K = 60.0;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final TraceService traceService;

    public DefaultPostgresRagEngine(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            TextSplitter textSplitter
    ) {
        this(documentMapper, chunkMapper, textSplitter, null, null);
    }

    public DefaultPostgresRagEngine(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            TextSplitter textSplitter,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this(documentMapper, chunkMapper, textSplitter, embeddingService, vectorStore, null);
    }

    public DefaultPostgresRagEngine(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            TextSplitter textSplitter,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            TraceService traceService
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.textSplitter = textSplitter == null ? new TextSplitter() : textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.traceService = traceService;
    }

    @Override
    public RagIngestResultDTO ingest(IngestKnowledgeDocumentCommand command) {
        return ingest(command, null, null);
    }

    @Override
    public RagIngestResultDTO ingest(
            IngestKnowledgeDocumentCommand command,
            String traceId,
            Long parentSpanId
    ) {
        validate(command);
        ObjectNode attributes = OBJECT_MAPPER.createObjectNode()
                .put("titleChars", command.title().length())
                .put("sourceType", command.sourceType().isBlank() ? "MANUAL" : command.sourceType())
                .put("sourceUriPresent", !command.sourceUri().isBlank())
                .put("contentChars", command.content().length())
                .put("chunkTokenBudget", command.chunkTokenBudget())
                .put("overlapTokens", command.overlapTokens())
                .put("profileScoped", command.profileId() != null)
                .put("embeddingService", embeddingServiceName())
                .put("vectorStore", vectorStoreName());
        TraceSpanDTO ingestSpan = safeStartSpan(traceId, parentSpanId, "rag.ingest", "RAG", attributes);
        try {
            RagIngestResultDTO result = doIngest(command, attributes);
            safeFinishSpan(ingestSpan, "SUCCESS", null, null);
            return result;
        } catch (RuntimeException ex) {
            safeFinishSpan(ingestSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private RagIngestResultDTO doIngest(IngestKnowledgeDocumentCommand command, ObjectNode attributes) {
        String docHash = sha256(command.content());
        List<RagTextChunkDTO> chunks = textSplitter.split(
                command.title(),
                command.sourceUri(),
                command.content(),
                command.chunkTokenBudget(),
                command.overlapTokens()
        );

        KnowledgeDocumentEntity document = documentMapper.selectActiveByScopeAndHash(
                command.tenantId(),
                command.applicationId(),
                command.ownerUserId(),
                command.profileId(),
                docHash
        );
        if (document == null) {
            document = newDocument(command, docHash);
            documentMapper.insert(document);
        } else {
            applyDocumentFields(document, command, docHash);
            documentMapper.updateById(document);
            chunkMapper.disableByDocumentId(command.tenantId(), command.applicationId(), document.getId());
            deleteVectorDocument(command.tenantId(), command.applicationId(), command.ownerUserId(), command.profileId(), document.getId());
        }

        int vectorIndexedCount = 0;
        for (RagTextChunkDTO chunk : chunks) {
            KnowledgeChunkEntity entity = newChunk(command, document.getId(), chunk);
            chunkMapper.insert(entity);
            if (upsertVector(command, document.getId(), entity, chunk)) {
                vectorIndexedCount++;
            }
        }

        attributes.put("documentId", document.getId() == null ? 0L : document.getId());
        attributes.put("chunkCount", chunks.size());
        attributes.put("vectorIndexedCount", vectorIndexedCount);
        attributes.put("status", "INDEXED");
        return new RagIngestResultDTO(document.getId(), command.title(), docHash, chunks.size(), "INDEXED");
    }

    @Override
    public List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK
    ) {
        return search(tenantId, applicationId, userId, profileId, query, topK, null, null);
    }

    @Override
    public List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK,
            String traceId,
            Long parentSpanId
    ) {
        return search(tenantId, applicationId, userId, profileId, query, topK, null, traceId, parentSpanId);
    }

    @Override
    public List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK,
            EmbeddingVectorDTO queryVector,
            String traceId,
            Long parentSpanId
    ) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<RagSearchResultDTO> vectorResults = searchByVector(tenantId, applicationId, userId, profileId, query, topK,
                queryVector,
                traceId, parentSpanId);
        List<RagSearchResultDTO> keywordResults = searchByKeyword(tenantId, applicationId, userId, profileId, query, topK);
        return mergeSearchResults(vectorResults, keywordResults, topK);
    }

    private List<RagSearchResultDTO> searchByKeyword(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK
    ) {
        List<String> terms = keywords(query).stream().toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        try {
            return chunkMapper.searchActiveChunks(tenantId, applicationId, userId, profileId, terms, String.join(" ", terms), topK)
                    .stream()
                    .map(chunk -> new RagSearchResultDTO(
                            chunk.getDocumentId(),
                            chunk.getId(),
                            chunk.getTitle(),
                            chunk.getContent(),
                            chunk.getSourceUri(),
                            chunk.getKeywordScore() == null ? 0.0 : chunk.getKeywordScore()
                    ))
                    .toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<RagSearchResultDTO> mergeSearchResults(
            List<RagSearchResultDTO> vectorResults,
            List<RagSearchResultDTO> keywordResults,
            int topK
    ) {
        List<RagSearchResultDTO> safeVectorResults = vectorResults == null ? List.of() : vectorResults;
        List<RagSearchResultDTO> safeKeywordResults = keywordResults == null ? List.of() : keywordResults;
        if (safeVectorResults.isEmpty()) {
            return safeKeywordResults;
        }
        if (safeKeywordResults.isEmpty()) {
            return safeVectorResults;
        }
        Map<String, FusedRagResult> fused = new java.util.LinkedHashMap<>();
        addRankedRagResults(fused, safeVectorResults);
        addRankedRagResults(fused, safeKeywordResults);
        return fused.values().stream()
                .sorted(java.util.Comparator
                        .<FusedRagResult>comparingDouble(FusedRagResult::score)
                        .reversed()
                        .thenComparing(result -> result.value().score(), java.util.Comparator.reverseOrder()))
                .limit(Math.max(1, topK))
                .map(result -> withScore(result.value(), result.score()))
                .toList();
    }

    private void addRankedRagResults(Map<String, FusedRagResult> fused, List<RagSearchResultDTO> results) {
        for (int i = 0; i < results.size(); i++) {
            RagSearchResultDTO result = results.get(i);
            if (result == null) {
                continue;
            }
            String key = ragKey(result);
            double score = 1.0 / (RRF_K + i + 1);
            fused.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new FusedRagResult(result, score);
                }
                return new FusedRagResult(existing.value(), existing.score() + score);
            });
        }
    }

    private String ragKey(RagSearchResultDTO result) {
        if (result.chunkId() != null) {
            return "chunk:" + result.chunkId();
        }
        if (result.documentId() != null) {
            return "doc:" + result.documentId() + ":" + result.content();
        }
        return "content:" + result.content();
    }

    private RagSearchResultDTO withScore(RagSearchResultDTO result, double score) {
        return new RagSearchResultDTO(
                result.documentId(),
                result.chunkId(),
                result.title(),
                result.content(),
                result.sourceUri(),
                score
        );
    }

    @Override
    public int delete(Long tenantId, Long applicationId, Long userId, Long profileId, Long documentId) {
        KnowledgeDocumentEntity document = documentMapper.selectActiveByIdAndScope(
                tenantId,
                applicationId,
                userId,
                profileId,
                documentId
        );
        if (document == null) {
            return 0;
        }
        documentMapper.disableById(documentId);
        chunkMapper.disableByDocumentId(tenantId, applicationId, documentId);
        deleteVectorDocument(tenantId, applicationId, userId, profileId, documentId);
        return 1;
    }

    private boolean upsertVector(
            IngestKnowledgeDocumentCommand command,
            Long documentId,
            KnowledgeChunkEntity entity,
            RagTextChunkDTO chunk
    ) {
        if (embeddingService == null || vectorStore == null || entity.getId() == null) {
            return false;
        }
        try {
            EmbeddingVectorDTO vector = embeddingService.embed(chunk.content());
            if (vector == null || vector.dimension() == 0) {
                return false;
            }
            String vectorId = "rag-chunk-" + entity.getId();
            entity.setVectorId(vectorId);
            vectorStore.upsert(new VectorStoreDocumentDTO(
                    vectorId,
                    command.tenantId(),
                    command.applicationId(),
                    command.ownerUserId(),
                    command.profileId(),
                    documentId,
                    entity.getId(),
                    vector
            ));
            chunkMapper.updateById(entity);
            return true;
        } catch (RuntimeException ignored) {
            // Vector indexing is a recall enhancement; PG keyword search remains the fallback path.
            return false;
        }
    }

    private List<RagSearchResultDTO> searchByVector(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK,
            EmbeddingVectorDTO precomputedQueryVector,
            String traceId,
            Long parentSpanId
    ) {
        if (vectorStore == null) {
            return List.of();
        }
        try {
            EmbeddingVectorDTO queryVector = precomputedQueryVector == null
                    ? embedQuery(query, traceId, parentSpanId)
                    : precomputedQueryVector;
            if (queryVector == null || queryVector.dimension() == 0) {
                return List.of();
            }
            ObjectNode vectorAttributes = OBJECT_MAPPER.createObjectNode()
                    .put("topK", topK)
                    .put("dimension", queryVector.dimension())
                    .put("profileScoped", profileId != null)
                    .put("vectorStore", vectorStoreName());
            TraceSpanDTO vectorSpan = safeStartSpan(traceId, parentSpanId, "rag.vector.search", "RAG", vectorAttributes);
            List<VectorSearchResultDTO> vectorResults;
            try {
                vectorResults = vectorStore.search(new VectorSearchQueryDTO(
                        tenantId,
                        applicationId,
                        userId,
                        profileId,
                        queryVector,
                        topK
                ));
                if (vectorSpan != null && vectorSpan.attributes() instanceof ObjectNode attributes) {
                    attributes.put("resultCount", vectorResults == null ? 0 : vectorResults.size());
                }
                safeFinishSpan(vectorSpan, "SUCCESS", null, null);
            } catch (RuntimeException ex) {
                safeFinishSpan(vectorSpan, "FAILED", errorCode(ex), errorMessage(ex));
                throw ex;
            }
            if (vectorResults == null || vectorResults.isEmpty()) {
                return List.of();
            }
            List<Long> chunkIds = vectorResults.stream()
                    .map(VectorSearchResultDTO::chunkId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (chunkIds.isEmpty()) {
                return List.of();
            }
            Map<Long, KnowledgeChunkEntity> chunksById = chunkMapper.selectActiveChunksByIds(
                            tenantId,
                            applicationId,
                            userId,
                            profileId,
                            chunkIds
                    )
                    .stream()
                    .collect(Collectors.toMap(KnowledgeChunkEntity::getId, Function.identity(), (left, right) -> left));
            return vectorResults.stream()
                    .map(result -> toVectorSearchResult(result, chunksById.get(result.chunkId())))
                    .filter(Objects::nonNull)
                    .limit(topK)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private EmbeddingVectorDTO embedQuery(String query, String traceId, Long parentSpanId) {
        if (embeddingService == null) {
            return null;
        }
        ObjectNode embeddingAttributes = OBJECT_MAPPER.createObjectNode()
                .put("queryChars", query == null ? 0 : query.length());
        TraceSpanDTO embeddingSpan = safeStartSpan(traceId, parentSpanId, "rag.embedding", "RAG", embeddingAttributes);
        try {
            EmbeddingVectorDTO queryVector = embeddingService.embed(query);
            if (embeddingSpan != null && embeddingSpan.attributes() instanceof ObjectNode attributes) {
                attributes.put("model", queryVector == null ? "" : queryVector.model());
                attributes.put("dimension", queryVector == null ? 0 : queryVector.dimension());
            }
            safeFinishSpan(embeddingSpan, "SUCCESS", null, null);
            return queryVector;
        } catch (RuntimeException ex) {
            safeFinishSpan(embeddingSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private TraceSpanDTO safeStartSpan(
            String traceId,
            Long parentSpanId,
            String spanName,
            String spanType,
            JsonNode attributes
    ) {
        if (traceService == null || traceId == null || traceId.isBlank()) {
            return null;
        }
        try {
            return traceService.startSpan(new StartTraceSpanCommand(
                    traceId,
                    parentSpanId,
                    spanName,
                    spanType,
                    "core.rag",
                    attributes
            ));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (traceService == null || span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, span.attributes()));
        } catch (RuntimeException ex) {
            // Trace is diagnostic data; it must not break RAG recall.
        }
    }

    private String errorCode(RuntimeException ex) {
        return ex == null ? null : ex.getClass().getSimpleName();
    }

    private String errorMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return null;
        }
        return ex.getMessage().length() > 500 ? ex.getMessage().substring(0, 500) : ex.getMessage();
    }

    private String vectorStoreName() {
        return vectorStore == null ? "" : vectorStore.getClass().getSimpleName();
    }

    private String embeddingServiceName() {
        return embeddingService == null ? "" : embeddingService.getClass().getSimpleName();
    }

    private RagSearchResultDTO toVectorSearchResult(
            VectorSearchResultDTO vectorResult,
            KnowledgeChunkEntity chunk
    ) {
        if (chunk == null) {
            return null;
        }
        return new RagSearchResultDTO(
                chunk.getDocumentId(),
                chunk.getId(),
                chunk.getTitle(),
                chunk.getContent(),
                chunk.getSourceUri(),
                vectorResult.score()
        );
    }

    private void deleteVectorDocument(Long tenantId, Long applicationId, Long userId, Long profileId, Long documentId) {
        if (vectorStore == null) {
            return;
        }
        try {
            vectorStore.deleteByDocument(tenantId, applicationId, userId, profileId, documentId);
        } catch (RuntimeException ignored) {
            // Deleting PG rows is authoritative; vector cleanup can be retried by a later Qdrant implementation.
        }
    }

    private void validate(IngestKnowledgeDocumentCommand command) {
        if (command == null
                || command.tenantId() == null
                || command.applicationId() == null
                || command.ownerUserId() == null
                || command.content() == null
                || command.content().isBlank()) {
            throw new IllegalArgumentException("RAG ingest command is invalid");
        }
    }

    private KnowledgeDocumentEntity newDocument(IngestKnowledgeDocumentCommand command, String docHash) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        applyDocumentFields(document, command, docHash);
        return document;
    }

    private void applyDocumentFields(
            KnowledgeDocumentEntity document,
            IngestKnowledgeDocumentCommand command,
            String docHash
    ) {
        document.setTenantId(command.tenantId());
        document.setApplicationId(command.applicationId());
        document.setOwnerUserId(command.ownerUserId());
        document.setProfileId(command.profileId());
        document.setTitle(command.title());
        document.setSourceType(command.sourceType().isBlank() ? "MANUAL" : command.sourceType());
        document.setSourceUri(command.sourceUri());
        document.setDocHash(docHash);
        document.setStatus("INDEXED");
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("chunkTokenBudget", command.chunkTokenBudget());
        metadata.put("overlapTokens", command.overlapTokens());
        document.setMetadata(metadata);
    }

    private KnowledgeChunkEntity newChunk(
            IngestKnowledgeDocumentCommand command,
            Long documentId,
            RagTextChunkDTO chunk
    ) {
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setTenantId(command.tenantId());
        entity.setApplicationId(command.applicationId());
        entity.setDocumentId(documentId);
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setContent(chunk.content());
        entity.setContentHash(chunk.contentHash());
        entity.setTokenCount(chunk.tokenCount());
        entity.setStatus("ACTIVE");
        entity.setMetadata(chunkMetadata(chunk));
        return entity;
    }

    private JsonNode chunkMetadata(RagTextChunkDTO chunk) {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("documentTitle", chunk.documentTitle());
        metadata.put("sourceUri", chunk.sourceUri());
        metadata.put("headingPath", chunk.headingPath());
        return metadata;
    }

    private Set<String> keywords(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .strip();
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String part : normalized.split("\\s+")) {
            if (part.length() >= 2) {
                if (isChinese(part)) {
                    for (int i = 0; i < part.length() - 1; i++) {
                        terms.add(part.substring(i, i + 2));
                    }
                    if (part.length() == 2) {
                        terms.add(part);
                    }
                } else {
                    terms.add(part);
                }
            }
        }
        return terms;
    }

    private boolean isChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record FusedRagResult(
            RagSearchResultDTO value,
            double score
    ) {
    }
}
