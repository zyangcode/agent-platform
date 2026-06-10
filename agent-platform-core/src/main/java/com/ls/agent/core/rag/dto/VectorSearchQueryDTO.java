package com.ls.agent.core.rag.dto;

public record VectorSearchQueryDTO(
        String sourceType,
        Long tenantId,
        Long applicationId,
        Long ownerUserId,
        Long profileId,
        EmbeddingVectorDTO queryVector,
        int topK,
        java.util.List<String> memoryScopes,
        Long sourceConversationId
) {

    public VectorSearchQueryDTO {
        sourceType = sourceType == null || sourceType.isBlank() ? "rag" : sourceType.strip().toLowerCase();
        topK = Math.max(0, topK);
        memoryScopes = normalizeScopes(memoryScopes);
    }

    public VectorSearchQueryDTO(
            String sourceType,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            EmbeddingVectorDTO queryVector,
            int topK
    ) {
        this(sourceType, tenantId, applicationId, ownerUserId, profileId, queryVector, topK, java.util.List.of(), null);
    }

    public VectorSearchQueryDTO(
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            EmbeddingVectorDTO queryVector,
            int topK
    ) {
        this("rag", tenantId, applicationId, ownerUserId, profileId, queryVector, topK, java.util.List.of(), null);
    }

    private static java.util.List<String> normalizeScopes(java.util.List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return java.util.List.of();
        }
        return scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(scope -> scope.strip().toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }
}
