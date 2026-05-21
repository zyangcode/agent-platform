package com.ls.agent.core.skill.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.CreatedEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

@TableName(value = "skill_versions", autoResultMap = true)
public class SkillVersionEntity extends CreatedEntity {

    @TableField("skill_id")
    private Long skillId;

    private String version;

    @TableField(value = "parameter_schema", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode parameterSchema;

    @TableField(value = "return_schema", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode returnSchema;

    @TableField(value = "runtime_config", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode runtimeConfig;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode dependencies;
    private String checksum;

    @TableField(value = "validation_result", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode validationResult;

    private String status;

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public JsonNode getParameterSchema() {
        return parameterSchema;
    }

    public void setParameterSchema(JsonNode parameterSchema) {
        this.parameterSchema = parameterSchema;
    }

    public JsonNode getReturnSchema() {
        return returnSchema;
    }

    public void setReturnSchema(JsonNode returnSchema) {
        this.returnSchema = returnSchema;
    }

    public JsonNode getRuntimeConfig() {
        return runtimeConfig;
    }

    public void setRuntimeConfig(JsonNode runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public JsonNode getDependencies() {
        return dependencies;
    }

    public void setDependencies(JsonNode dependencies) {
        this.dependencies = dependencies;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public JsonNode getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(JsonNode validationResult) {
        this.validationResult = validationResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
