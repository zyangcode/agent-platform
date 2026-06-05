package com.ls.agent.core.memory.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.StringArrayTypeHandler;

import java.time.LocalDateTime;

@TableName(value = "memories", autoResultMap = true)
public class MemoryEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    @TableField("application_id")
    private Long applicationId;

    @TableField("profile_id")
    private Long profileId;

    @TableField("memory_type")
    private String memoryType;

    @TableField("memory_category")
    private String memoryCategory;

    private String content;
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] keywords;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;
    private Double importance;
    private Double confidence;

    @TableField("last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @TableField("access_count")
    private Integer accessCount;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("slot_hint")
    private String slotHint;

    @TableField("source_conversation_id")
    private Long sourceConversationId;

    private String status;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getMemoryCategory() {
        return memoryCategory;
    }

    public void setMemoryCategory(String memoryCategory) {
        this.memoryCategory = memoryCategory;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public Double getImportance() {
        return importance;
    }

    public void setImportance(Double importance) {
        this.importance = importance;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSlotHint() {
        return slotHint;
    }

    public void setSlotHint(String slotHint) {
        this.slotHint = slotHint;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
