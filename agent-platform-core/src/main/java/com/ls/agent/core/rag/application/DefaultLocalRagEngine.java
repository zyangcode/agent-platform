package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.rag.dto.RagTextChunkDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultLocalRagEngine implements RagEngine {

    private final TextSplitter textSplitter;
    private final AtomicLong documentIdSequence = new AtomicLong(1);
    private final AtomicLong chunkIdSequence = new AtomicLong(1);
    private final ConcurrentMap<Long, StoredDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> documentIdsByScopeAndHash = new ConcurrentHashMap<>();

    public DefaultLocalRagEngine(TextSplitter textSplitter) {
        this.textSplitter = textSplitter == null ? new TextSplitter() : textSplitter;
    }

    @Override
    public RagIngestResultDTO ingest(IngestKnowledgeDocumentCommand command) {
        validate(command);
        String docHash = TextSplitter.sha256(command.content());
        String scopeHash = scopeHash(command, docHash);
        Long documentId = documentIdsByScopeAndHash.computeIfAbsent(scopeHash, ignored -> documentIdSequence.getAndIncrement());
        List<RagTextChunkDTO> chunks = textSplitter.split(
                command.title(),
                command.sourceUri(),
                command.content(),
                command.chunkTokenBudget(),
                command.overlapTokens()
        );
        List<StoredChunk> storedChunks = new ArrayList<>();
        for (RagTextChunkDTO chunk : chunks) {
            storedChunks.add(new StoredChunk(
                    chunkIdSequence.getAndIncrement(),
                    documentId,
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.tokenCount(),
                    chunk.contentHash(),
                    chunk.headingPath()
            ));
        }
        StoredDocument document = new StoredDocument(
                documentId,
                command.tenantId(),
                command.applicationId(),
                command.ownerUserId(),
                command.profileId(),
                command.title(),
                command.sourceType(),
                command.sourceUri(),
                docHash,
                storedChunks
        );
        documents.put(documentId, document);
        return new RagIngestResultDTO(documentId, command.title(), docHash, storedChunks.size(), "INDEXED");
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
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        Set<String> queryTerms = keywords(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        return documents.values().stream()
                .filter(document -> matchesScope(document, tenantId, applicationId, userId, profileId))
                .flatMap(document -> document.chunks().stream()
                        .map(chunk -> score(document, chunk, queryTerms))
                        .filter(scored -> scored.score() > 0))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(scored -> scored.chunk().chunkIndex()))
                .limit(topK)
                .map(scored -> new RagSearchResultDTO(
                        scored.document().documentId(),
                        scored.chunk().chunkId(),
                        scored.document().title(),
                        scored.chunk().content(),
                        scored.document().sourceUri(),
                        scored.score()
                ))
                .toList();
    }

    @Override
    public int delete(Long tenantId, Long applicationId, Long userId, Long profileId, Long documentId) {
        StoredDocument document = documents.get(documentId);
        if (document == null || !matchesScope(document, tenantId, applicationId, userId, profileId)) {
            return 0;
        }
        documents.remove(documentId);
        documentIdsByScopeAndHash.entrySet().removeIf(entry -> Objects.equals(entry.getValue(), documentId));
        return 1;
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

    private String scopeHash(IngestKnowledgeDocumentCommand command, String docHash) {
        return command.tenantId() + ":"
                + command.applicationId() + ":"
                + command.ownerUserId() + ":"
                + command.profileId() + ":"
                + docHash;
    }

    private boolean matchesScope(
            StoredDocument document,
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId
    ) {
        return Objects.equals(document.tenantId(), tenantId)
                && Objects.equals(document.applicationId(), applicationId)
                && Objects.equals(document.ownerUserId(), userId)
                && (document.profileId() == null || Objects.equals(document.profileId(), profileId));
    }

    private ScoredChunk score(StoredDocument document, StoredChunk chunk, Set<String> queryTerms) {
        Set<String> contentTerms = keywords(document.title() + " " + chunk.headingPath() + " " + chunk.content());
        int hits = 0;
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                hits++;
            }
        }
        double score = hits == 0 ? 0 : (double) hits / queryTerms.size();
        return new ScoredChunk(document, chunk, score);
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
        Set<String> terms = new HashSet<>();
        for (String part : normalized.split("\\s+")) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        return terms;
    }

    private record StoredDocument(
            Long documentId,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            String title,
            String sourceType,
            String sourceUri,
            String docHash,
            List<StoredChunk> chunks
    ) {

        private StoredDocument {
            title = title == null ? "" : title;
            sourceType = sourceType == null ? "" : sourceType;
            sourceUri = sourceUri == null ? "" : sourceUri;
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }

    private record StoredChunk(
            Long chunkId,
            Long documentId,
            int chunkIndex,
            String content,
            int tokenCount,
            String contentHash,
            String headingPath
    ) {
    }

    private record ScoredChunk(
            StoredDocument document,
            StoredChunk chunk,
            double score
    ) {
    }
}
