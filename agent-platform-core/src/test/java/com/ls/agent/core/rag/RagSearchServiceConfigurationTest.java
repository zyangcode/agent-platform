package com.ls.agent.core.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.HypotheticalDocumentService;
import com.ls.agent.core.rag.api.QueryExpansionService;
import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.api.RetrievalReranker;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.application.DefaultLocalRagEngine;
import com.ls.agent.core.rag.application.DefaultPostgresRagEngine;
import com.ls.agent.core.rag.application.InMemorySemanticCacheService;
import com.ls.agent.core.rag.application.MockEmbeddingService;
import com.ls.agent.core.rag.application.MockHypotheticalDocumentService;
import com.ls.agent.core.rag.application.MockQueryExpansionService;
import com.ls.agent.core.rag.application.MockRetrievalReranker;
import com.ls.agent.core.rag.application.MockVectorStore;
import com.ls.agent.core.rag.application.OpenAiCompatibleEmbeddingService;
import com.ls.agent.core.rag.application.OpenAiCompatibleHypotheticalDocumentService;
import com.ls.agent.core.rag.application.OpenAiCompatibleQueryExpansionService;
import com.ls.agent.core.rag.application.OpenAiCompatibleRetrievalReranker;
import com.ls.agent.core.rag.application.QdrantVectorStore;
import com.ls.agent.core.rag.application.RagSearchServiceConfiguration;
import com.ls.agent.core.rag.application.TextSplitter;
import com.ls.agent.core.rag.api.SemanticCacheService;
import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import com.ls.agent.core.rag.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagSearchServiceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(RagSearchServiceConfiguration.class);

    @Test
    void registersMockVectorDependenciesByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TextSplitter.class);
            assertThat(context).hasSingleBean(EmbeddingService.class);
            assertThat(context.getBean(EmbeddingService.class)).isInstanceOf(MockEmbeddingService.class);
            assertThat(context).hasSingleBean(VectorStore.class);
            assertThat(context.getBean(VectorStore.class)).isInstanceOf(MockVectorStore.class);
            assertThat(context).hasSingleBean(RetrievalReranker.class);
            assertThat(context.getBean(RetrievalReranker.class)).isNotInstanceOf(MockRetrievalReranker.class);
            assertThat(context).hasSingleBean(QueryExpansionService.class);
            assertThat(context.getBean(QueryExpansionService.class)).isNotInstanceOf(MockQueryExpansionService.class);
            assertThat(context).hasSingleBean(HypotheticalDocumentService.class);
            assertThat(context.getBean(HypotheticalDocumentService.class)).isNotInstanceOf(MockHypotheticalDocumentService.class);
            assertThat(context).hasSingleBean(SemanticCacheService.class);
            assertThat(context.getBean(SemanticCacheService.class).enabled()).isFalse();
        });
    }

    @Test
    void usesPostgresRagEngineWhenRagMappersExist() {
        contextRunner
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:15432/agent_platform")
                .withBean(KnowledgeDocumentMapper.class, () -> mock(KnowledgeDocumentMapper.class))
                .withBean(KnowledgeChunkMapper.class, () -> mock(KnowledgeChunkMapper.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RagEngine.class);
                    assertThat(context.getBean(RagEngine.class)).isInstanceOf(DefaultPostgresRagEngine.class);
                    assertThat(context).hasSingleBean(RagSearchService.class);
                });
    }

    @Test
    void fallsBackToLocalRagEngineWhenDatasourceIsMissingEvenIfMapperMocksExist() {
        contextRunner
                .withBean(KnowledgeDocumentMapper.class, () -> mock(KnowledgeDocumentMapper.class))
                .withBean(KnowledgeChunkMapper.class, () -> mock(KnowledgeChunkMapper.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RagEngine.class);
                    assertThat(context.getBean(RagEngine.class)).isInstanceOf(DefaultLocalRagEngine.class);
                    assertThat(context).hasSingleBean(RagSearchService.class);
                });
    }

    @Test
    void fallsBackToLocalRagEngineWhenRagMappersAreMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RagEngine.class);
            assertThat(context.getBean(RagEngine.class)).isInstanceOf(DefaultLocalRagEngine.class);
            assertThat(context).hasSingleBean(RagSearchService.class);
        });
    }

    @Test
    void registersQdrantVectorStoreWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.vector-store=qdrant",
                        "agent.rag.qdrant.enabled=true",
                        "agent.rag.qdrant.base-url=http://localhost:6333",
                        "agent.rag.qdrant.collection-name=rag_chunks_test",
                        "agent.rag.qdrant.dimension=768"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorStore.class);
                    assertThat(context.getBean(VectorStore.class)).isInstanceOf(QdrantVectorStore.class);
                });
    }

    @Test
    void registersOpenAiCompatibleEmbeddingServiceWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.embedding-provider=openai-compatible",
                        "agent.rag.embedding.enabled=true",
                        "agent.rag.embedding.base-url=https://api.openai.com/v1",
                        "agent.rag.embedding.api-key=sk-test",
                        "agent.rag.embedding.model=text-embedding-3-small",
                        "agent.rag.embedding.dimension=1536"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingService.class);
                    assertThat(context.getBean(EmbeddingService.class)).isInstanceOf(OpenAiCompatibleEmbeddingService.class);
                });
    }

    @Test
    void registersMockRetrievalEnhancersOnlyWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.reranker.enabled=true",
                        "agent.rag.reranker.provider=mock",
                        "agent.rag.query-expansion.enabled=true",
                        "agent.rag.query-expansion.provider=mock",
                        "agent.rag.hyde.enabled=true",
                        "agent.rag.hyde.provider=mock"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RetrievalReranker.class);
                    assertThat(context.getBean(RetrievalReranker.class)).isInstanceOf(MockRetrievalReranker.class);
                    assertThat(context).hasSingleBean(QueryExpansionService.class);
                    assertThat(context.getBean(QueryExpansionService.class)).isInstanceOf(MockQueryExpansionService.class);
                    assertThat(context).hasSingleBean(HypotheticalDocumentService.class);
                    assertThat(context.getBean(HypotheticalDocumentService.class)).isInstanceOf(MockHypotheticalDocumentService.class);
                });
    }

    @Test
    void registersOpenAiCompatibleRerankerWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.reranker.enabled=true",
                        "agent.rag.reranker.provider=openai-compatible",
                        "agent.rag.reranker.base-url=https://rerank.example.com/v1",
                        "agent.rag.reranker.api-key=sk-test",
                        "agent.rag.reranker.model=rerank-v1",
                        "agent.rag.reranker.path=/rerank"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RetrievalReranker.class);
                    assertThat(context.getBean(RetrievalReranker.class)).isInstanceOf(OpenAiCompatibleRetrievalReranker.class);
                });
    }

    @Test
    void registersOpenAiCompatibleQueryExpansionWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.query-expansion.enabled=true",
                        "agent.rag.query-expansion.provider=openai-compatible",
                        "agent.rag.query-expansion.base-url=https://llm.example.com/v1",
                        "agent.rag.query-expansion.api-key=sk-test",
                        "agent.rag.query-expansion.model=gpt-4o-mini",
                        "agent.rag.query-expansion.path=/chat/completions"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryExpansionService.class);
                    assertThat(context.getBean(QueryExpansionService.class)).isInstanceOf(OpenAiCompatibleQueryExpansionService.class);
                });
    }

    @Test
    void registersInMemorySemanticCacheWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.semantic-cache.enabled=true",
                        "agent.rag.semantic-cache.provider=memory",
                        "agent.rag.semantic-cache.similarity-threshold=0.88",
                        "agent.rag.semantic-cache.ttl-ms=60000",
                        "agent.rag.semantic-cache.max-entries=256"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SemanticCacheService.class);
                    assertThat(context.getBean(SemanticCacheService.class)).isInstanceOf(InMemorySemanticCacheService.class);
                    assertThat(context.getBean(SemanticCacheService.class).enabled()).isTrue();
                });
    }

    @Test
    void registersOpenAiCompatibleHydeWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.hyde.enabled=true",
                        "agent.rag.hyde.provider=openai-compatible",
                        "agent.rag.hyde.base-url=https://llm.example.com/v1",
                        "agent.rag.hyde.api-key=sk-test",
                        "agent.rag.hyde.model=gpt-4o-mini",
                        "agent.rag.hyde.path=/chat/completions"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(HypotheticalDocumentService.class);
                    assertThat(context.getBean(HypotheticalDocumentService.class)).isInstanceOf(OpenAiCompatibleHypotheticalDocumentService.class);
                });
    }
}
