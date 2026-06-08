package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.HypotheticalDocumentService;
import com.ls.agent.core.rag.api.QueryExpansionService;
import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.api.RetrievalReranker;
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
    @ConditionalOnMissingBean(RetrievalReranker.class)
    public RetrievalReranker retrievalReranker() {
        return RetrievalReranker.noop();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.rag.query-expansion", name = "enabled", havingValue = "true")
    public QueryExpansionService mockQueryExpansionService() {
        return new MockQueryExpansionService();
    }

    @Bean
    @ConditionalOnMissingBean(QueryExpansionService.class)
    public QueryExpansionService queryExpansionService() {
        return QueryExpansionService.noop();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.rag.hyde", name = "enabled", havingValue = "true")
    public HypotheticalDocumentService mockHypotheticalDocumentService() {
        return new MockHypotheticalDocumentService();
    }

    @Bean
    @ConditionalOnMissingBean(HypotheticalDocumentService.class)
    public HypotheticalDocumentService hypotheticalDocumentService() {
        return HypotheticalDocumentService.noop();
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
            ObjectProvider<TraceService> traceServiceProvider
    ) {
        return new DefaultPostgresRagEngine(documentMapper, chunkMapper, textSplitter, embeddingService, vectorStore,
                traceServiceProvider.getIfAvailable(), retrievalReranker, queryExpansionService, hypotheticalDocumentService);
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
