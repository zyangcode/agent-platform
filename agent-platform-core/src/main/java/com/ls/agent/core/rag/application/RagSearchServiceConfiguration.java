package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.HypotheticalDocumentService;
import com.ls.agent.core.rag.api.QueryExpansionService;
import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.api.RetrievalReranker;
import com.ls.agent.core.rag.api.SemanticCacheService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import com.ls.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.trace.api.TraceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagSearchServiceConfiguration {

    @Bean
    @ConditionalOnMissingBean(TextSplitter.class)
    public TextSplitter textSplitter() {
        return new TextSplitter();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.embedding-provider:mock}' == 'openai-compatible' && '${agent.rag.embedding.enabled:false}' == 'true'")
    public EmbeddingService openAiCompatibleEmbeddingService(
            ObjectMapper objectMapper,
            @Value("${agent.rag.embedding.enabled:false}") boolean enabled,
            @Value("${agent.rag.embedding.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agent.rag.embedding.api-key:}") String apiKey,
            @Value("${agent.rag.embedding.model:text-embedding-3-small}") String model,
            @Value("${agent.rag.embedding.dimension:0}") int dimension,
            @Value("${agent.rag.embedding.timeout-ms:3000}") int timeoutMs
    ) {
        return new OpenAiCompatibleEmbeddingService(
                new OpenAiCompatibleEmbeddingServiceProperties(enabled, baseUrl, apiKey, model, dimension, timeoutMs),
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingService.class)
    public EmbeddingService embeddingService() {
        return new MockEmbeddingService();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.vector-store:mock}' == 'qdrant' && '${agent.rag.qdrant.enabled:false}' == 'true'")
    public VectorStore qdrantVectorStore(
            ObjectMapper objectMapper,
            @Value("${agent.rag.qdrant.enabled:false}") boolean enabled,
            @Value("${agent.rag.qdrant.base-url:http://localhost:6333}") String baseUrl,
            @Value("${agent.rag.qdrant.collection-name:rag_chunks}") String collectionName,
            @Value("${agent.rag.qdrant.dimension:768}") int dimension,
            @Value("${agent.rag.qdrant.distance:Cosine}") String distance,
            @Value("${agent.rag.qdrant.timeout-ms:3000}") int timeoutMs
    ) {
        return new QdrantVectorStore(
                new QdrantVectorStoreProperties(enabled, baseUrl, collectionName, dimension, distance, timeoutMs),
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore() {
        return new MockVectorStore();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.reranker.provider:noop}' == 'mock' && '${agent.rag.reranker.enabled:false}' == 'true'")
    public RetrievalReranker mockRetrievalReranker() {
        return new MockRetrievalReranker();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.reranker.provider:noop}' == 'openai-compatible' && '${agent.rag.reranker.enabled:false}' == 'true'")
    public RetrievalReranker openAiCompatibleRetrievalReranker(
            ObjectMapper objectMapper,
            @Value("${agent.rag.reranker.enabled:false}") boolean enabled,
            @Value("${agent.rag.reranker.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agent.rag.reranker.api-key:}") String apiKey,
            @Value("${agent.rag.reranker.model:rerank-v1}") String model,
            @Value("${agent.rag.reranker.path:/rerank}") String path,
            @Value("${agent.rag.reranker.timeout-ms:3000}") int timeoutMs
    ) {
        return new OpenAiCompatibleRetrievalReranker(
                new OpenAiCompatibleRetrievalRerankerProperties(enabled, baseUrl, apiKey, model, path, timeoutMs),
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalReranker.class)
    public RetrievalReranker retrievalReranker() {
        return RetrievalReranker.noop();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.query-expansion.provider:noop}' == 'mock' && '${agent.rag.query-expansion.enabled:false}' == 'true'")
    public QueryExpansionService mockQueryExpansionService() {
        return new MockQueryExpansionService();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.query-expansion.provider:noop}' == 'openai-compatible' && '${agent.rag.query-expansion.enabled:false}' == 'true'")
    public QueryExpansionService openAiCompatibleQueryExpansionService(
            ObjectMapper objectMapper,
            @Value("${agent.rag.query-expansion.enabled:false}") boolean enabled,
            @Value("${agent.rag.query-expansion.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agent.rag.query-expansion.api-key:}") String apiKey,
            @Value("${agent.rag.query-expansion.model:gpt-4o-mini}") String model,
            @Value("${agent.rag.query-expansion.temperature:0.1}") double temperature,
            @Value("${agent.rag.query-expansion.path:/chat/completions}") String path,
            @Value("${agent.rag.query-expansion.timeout-ms:3000}") int timeoutMs
    ) {
        return new OpenAiCompatibleQueryExpansionService(
                new OpenAiCompatibleQueryExpansionServiceProperties(enabled, baseUrl, apiKey, model, temperature, path, timeoutMs),
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(QueryExpansionService.class)
    public QueryExpansionService queryExpansionService() {
        return QueryExpansionService.noop();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.hyde.provider:noop}' == 'mock' && '${agent.rag.hyde.enabled:false}' == 'true'")
    public HypotheticalDocumentService mockHypotheticalDocumentService() {
        return new MockHypotheticalDocumentService();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.hyde.provider:noop}' == 'openai-compatible' && '${agent.rag.hyde.enabled:false}' == 'true'")
    public HypotheticalDocumentService openAiCompatibleHypotheticalDocumentService(
            ObjectMapper objectMapper,
            @Value("${agent.rag.hyde.enabled:false}") boolean enabled,
            @Value("${agent.rag.hyde.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agent.rag.hyde.api-key:}") String apiKey,
            @Value("${agent.rag.hyde.model:gpt-4o-mini}") String model,
            @Value("${agent.rag.hyde.temperature:0.2}") double temperature,
            @Value("${agent.rag.hyde.path:/chat/completions}") String path,
            @Value("${agent.rag.hyde.timeout-ms:3000}") int timeoutMs
    ) {
        return new OpenAiCompatibleHypotheticalDocumentService(
                new OpenAiCompatibleHypotheticalDocumentServiceProperties(enabled, baseUrl, apiKey, model, temperature, path, timeoutMs),
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(HypotheticalDocumentService.class)
    public HypotheticalDocumentService hypotheticalDocumentService() {
        return HypotheticalDocumentService.noop();
    }

    @Bean
    @ConditionalOnExpression("'${agent.rag.semantic-cache.provider:noop}' == 'memory' && '${agent.rag.semantic-cache.enabled:false}' == 'true'")
    public SemanticCacheService inMemorySemanticCacheService(
            @Value("${agent.rag.semantic-cache.enabled:false}") boolean enabled,
            @Value("${agent.rag.semantic-cache.provider:memory}") String provider,
            @Value("${agent.rag.semantic-cache.similarity-threshold:0.9}") double similarityThreshold,
            @Value("${agent.rag.semantic-cache.ttl-ms:60000}") long ttlMs,
            @Value("${agent.rag.semantic-cache.max-entries:256}") int maxEntries
    ) {
        return new InMemorySemanticCacheService(
                new SemanticCacheProperties(enabled, provider, similarityThreshold, ttlMs, maxEntries)
        );
    }

    @Bean
    @ConditionalOnMissingBean(SemanticCacheService.class)
    public SemanticCacheService semanticCacheService() {
        return SemanticCacheService.noop();
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.datasource", name = "url")
    public RagEngine postgresRagEngine(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            TextSplitter textSplitter,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            RetrievalReranker retrievalReranker,
            QueryExpansionService queryExpansionService,
            HypotheticalDocumentService hypotheticalDocumentService,
            SemanticCacheService semanticCacheService,
            ObjectProvider<TraceService> traceServiceProvider
    ) {
        return new DefaultPostgresRagEngine(documentMapper, chunkMapper, textSplitter, embeddingService, vectorStore,
                traceServiceProvider.getIfAvailable(), retrievalReranker, queryExpansionService,
                hypotheticalDocumentService, semanticCacheService);
    }

    @Bean
    @ConditionalOnMissingBean(RagEngine.class)
    public RagEngine localRagEngine(TextSplitter textSplitter) {
        return new DefaultLocalRagEngine(textSplitter);
    }

    @Bean
    @ConditionalOnMissingBean(RagSearchService.class)
    public RagSearchService localRagSearchService(RagEngine ragEngine) {
        return ragEngine;
    }
}
