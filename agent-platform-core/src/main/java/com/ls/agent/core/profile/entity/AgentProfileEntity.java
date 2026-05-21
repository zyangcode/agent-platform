package com.ls.agent.core.profile.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;
import com.ls.agent.core.support.persistence.VersionedEntity;

@TableName(value = "agent_profiles", autoResultMap = true)
public class AgentProfileEntity extends VersionedEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("application_id")
    private Long applicationId;

    private String name;

    @TableField("profile_type")
    private String profileType;

    private String description;

    @TableField("model_config_id")
    private Long modelConfigId;

    @TableField("prompt_extra")
    private String promptExtra;

    @TableField(value = "memory_strategy", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode memoryStrategy;

    @TableField("max_steps")
    private Integer maxSteps;

    @TableField("security_policy_id")
    private Long securityPolicyId;

    @TableField("execution_mode")
    private String executionMode;

    private String visibility;
    private String status;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfileType() {
        return profileType;
    }

    public void setProfileType(String profileType) {
        this.profileType = profileType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getModelConfigId() {
        return modelConfigId;
    }

    public void setModelConfigId(Long modelConfigId) {
        this.modelConfigId = modelConfigId;
    }

    public String getPromptExtra() {
        return promptExtra;
    }

    public void setPromptExtra(String promptExtra) {
        this.promptExtra = promptExtra;
    }

    public JsonNode getMemoryStrategy() {
        return memoryStrategy;
    }

    public void setMemoryStrategy(JsonNode memoryStrategy) {
        this.memoryStrategy = memoryStrategy;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps) {
        this.maxSteps = maxSteps;
    }

    public Long getSecurityPolicyId() {
        return securityPolicyId;
    }

    public void setSecurityPolicyId(Long securityPolicyId) {
        this.securityPolicyId = securityPolicyId;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
