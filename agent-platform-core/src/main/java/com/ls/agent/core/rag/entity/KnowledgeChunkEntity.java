package com.ls.agent.core.rag.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

@TableName(value = "knowledge_chunks", autoResultMap = true)
public class KnowledgeChunkEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("application_id")
    private Long applicationId;

    @TableField("document_id")
    private Long documentId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    private String content;

    @TableField("content_hash")
    private String contentHash;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("vector_id")
    private String vectorId;

    private String status;

    @TableField(value = "metadata", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode metadata;

    @TableField(exist = false)
    private String title;

    @TableField(exist = false)
    private String sourceUri;

    @TableField(exist = false)
    private Double keywordScore;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public Double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(Double keywordScore) {
        this.keywordScore = keywordScore;
    }
}
