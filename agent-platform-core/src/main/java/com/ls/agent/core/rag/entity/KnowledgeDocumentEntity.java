package com.ls.agent.core.rag.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

@TableName(value = "knowledge_documents", autoResultMap = true)
public class KnowledgeDocumentEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("application_id")
    private Long applicationId;

    @TableField("profile_id")
    private Long profileId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    private String title;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_uri")
    private String sourceUri;

    @TableField("doc_hash")
    private String docHash;

    private String status;

    @TableField(value = "metadata", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode metadata;

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

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getDocHash() {
        return docHash;
    }

    public void setDocHash(String docHash) {
        this.docHash = docHash;
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
}
