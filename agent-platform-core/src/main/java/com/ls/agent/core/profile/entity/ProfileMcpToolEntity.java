package com.ls.agent.core.profile.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;
import com.ls.agent.core.support.persistence.CreatedEntity;

@TableName(value = "profile_mcp_tools", autoResultMap = true)
public class ProfileMcpToolEntity extends CreatedEntity {

    @TableField("profile_id")
    private Long profileId;

    @TableField("mcp_tool_id")
    private Long mcpToolId;

    @TableField("enabled_by_default")
    private Boolean enabledByDefault;

    @TableField(value = "config_override", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode configOverride;

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getMcpToolId() {
        return mcpToolId;
    }

    public void setMcpToolId(Long mcpToolId) {
        this.mcpToolId = mcpToolId;
    }

    public Boolean getEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(Boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    public JsonNode getConfigOverride() {
        return configOverride;
    }

    public void setConfigOverride(JsonNode configOverride) {
        this.configOverride = configOverride;
    }
}
