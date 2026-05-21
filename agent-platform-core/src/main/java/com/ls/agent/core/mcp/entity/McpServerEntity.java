package com.ls.agent.core.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

import java.time.LocalDateTime;

@TableName(value = "mcp_servers", autoResultMap = true)
public class McpServerEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    private String name;

    @TableField("server_type")
    private String serverType;

    @TableField(value = "connection_config", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode connectionConfig;

    private String status;

    @TableField("last_discovered_at")
    private LocalDateTime lastDiscoveredAt;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public JsonNode getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(JsonNode connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastDiscoveredAt() {
        return lastDiscoveredAt;
    }

    public void setLastDiscoveredAt(LocalDateTime lastDiscoveredAt) {
        this.lastDiscoveredAt = lastDiscoveredAt;
    }
}
