package com.ls.agent.core.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

@TableName(value = "mcp_tools", autoResultMap = true)
public class McpToolEntity extends BaseEntity {

    @TableField("mcp_server_id")
    private Long mcpServerId;

    private String name;
    private String description;

    @TableField(value = "parameter_schema", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode parameterSchema;

    @TableField(value = "permission_declaration", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode permissionDeclaration;

    private String status;

    public Long getMcpServerId() {
        return mcpServerId;
    }

    public void setMcpServerId(Long mcpServerId) {
        this.mcpServerId = mcpServerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonNode getParameterSchema() {
        return parameterSchema;
    }

    public void setParameterSchema(JsonNode parameterSchema) {
        this.parameterSchema = parameterSchema;
    }

    public JsonNode getPermissionDeclaration() {
        return permissionDeclaration;
    }

    public void setPermissionDeclaration(JsonNode permissionDeclaration) {
        this.permissionDeclaration = permissionDeclaration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
