package com.ls.agent.core.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

import java.math.BigDecimal;

@TableName(value = "model_configs", autoResultMap = true)
public class ModelConfigEntity extends BaseEntity {

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_name")
    private String modelName;

    @TableField("display_name")
    private String displayName;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode capabilities;

    @TableField("default_temperature")
    private BigDecimal defaultTemperature;

    @TableField("max_context_tokens")
    private Integer maxContextTokens;

    private String status;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public JsonNode getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(JsonNode capabilities) {
        this.capabilities = capabilities;
    }

    public BigDecimal getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(BigDecimal defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public Integer getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(Integer maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
