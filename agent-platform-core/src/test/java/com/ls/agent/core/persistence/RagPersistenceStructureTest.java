package com.ls.agent.core.persistence;

import com.ls.agent.core.support.persistence.BaseEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagPersistenceStructureTest {

    private static final Map<String, Class<?>> RAG_ENTITY_SUPER_TYPES = Map.of(
            "com.ls.agent.core.rag.entity.KnowledgeDocumentEntity", BaseEntity.class,
            "com.ls.agent.core.rag.entity.KnowledgeChunkEntity", BaseEntity.class
    );

    private static final String[] RAG_MAPPER_NAMES = {
            "com.ls.agent.core.rag.mapper.KnowledgeDocumentMapper",
            "com.ls.agent.core.rag.mapper.KnowledgeChunkMapper"
    };

    @Test
    void ragMigrationCreatesKnowledgeDocumentAndChunkTables() throws IOException {
        String sql = readMigration("db/migration/V016__init_knowledge_rag.sql");

        assertThat(sql).contains(
                "create table knowledge_documents",
                "create table knowledge_chunks",
                "tenant_id bigint not null references tenants (id)",
                "application_id bigint not null references applications (id)",
                "owner_user_id bigint not null references users (id)",
                "profile_id bigint references agent_profiles (id)",
                "doc_hash varchar(128) not null",
                "metadata jsonb not null default '{}'::jsonb",
                "vector_id varchar(128)",
                "uk_knowledge_documents_scope_hash",
                "idx_knowledge_documents_scope_status",
                "idx_knowledge_chunks_document_status",
                "idx_knowledge_chunks_vector"
        );
    }

    @Test
    void keywordSearchMigrationAddsPostgresTsvectorIndexesForMemoryAndRag() throws IOException {
        String sql = readMigration("db/migration/V017__add_memory_rag_tsvector_search.sql");

        assertThat(sql).contains(
                "alter table memories",
                "search_vector tsvector",
                "alter table knowledge_chunks",
                "idx_memories_search_vector",
                "idx_knowledge_chunks_search_vector",
                "using gin (search_vector)",
                "create or replace function update_memories_search_vector",
                "create or replace function update_knowledge_chunks_search_vector",
                "trigger trg_memories_search_vector",
                "trigger trg_knowledge_chunks_search_vector",
                "to_tsvector('simple'"
        );
    }

    @Test
    void memoryScopeMigrationAddsConversationBoundaryColumnAndIndex() throws IOException {
        String sql = readMigration("db/migration/V022__add_memory_scope.sql");

        assertThat(sql).contains(
                "alter table memories",
                "memory_scope varchar(32) not null default 'PROFILE_LONG_TERM'",
                "idx_memories_scope_conversation",
                "tenant_id, user_id, memory_scope, source_conversation_id"
        );
    }

    @Test
    void ragEntitiesExistAndUseExpectedBaseClasses() throws ClassNotFoundException {
        for (Map.Entry<String, Class<?>> entry : RAG_ENTITY_SUPER_TYPES.entrySet()) {
            Class<?> entityClass = Class.forName(entry.getKey());

            assertThat(entityClass.getSuperclass())
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void ragMappersExist() throws ClassNotFoundException {
        for (String mapperName : RAG_MAPPER_NAMES) {
            assertThat(Class.forName(mapperName))
                    .as(mapperName)
                    .isInterface();
        }
    }

    private String readMigration(String resourcePath) throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream)
                    .as(resourcePath + " should exist")
                    .isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
