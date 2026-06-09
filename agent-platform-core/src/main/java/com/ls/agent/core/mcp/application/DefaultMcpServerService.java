package com.ls.agent.core.mcp.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.api.McpServerService;
import com.ls.agent.core.mcp.command.CreateMcpServerCommand;
import com.ls.agent.core.mcp.dto.McpServerDTO;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.entity.McpToolEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DefaultMcpServerService implements McpServerService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpServerService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String TOOL_STATUS_AVAILABLE = "AVAILABLE";

    private final McpServerMapper mapper;
    private final McpToolMapper toolMapper;
    private final ObjectMapper objectMapper;
    private final StdioMcpClient stdioMcpClient;
    private final HttpMcpClient httpMcpClient;

    public DefaultMcpServerService(McpServerMapper mapper, McpToolMapper toolMapper, ObjectMapper objectMapper,
                                   StdioMcpClient stdioMcpClient, HttpMcpClient httpMcpClient) {
        this.mapper = mapper;
        this.toolMapper = toolMapper;
        this.objectMapper = objectMapper;
        this.stdioMcpClient = stdioMcpClient;
        this.httpMcpClient = httpMcpClient;
    }

    @Override
    @Transactional
    public McpServerDTO create(CreateMcpServerCommand command) {
        Long tenantId = requireNonNull(command.tenantId(), "tenantId");
        McpServerEntity entity = new McpServerEntity();
        entity.setTenantId(tenantId);
        entity.setName(requiredText(command.name(), "name"));
        entity.setServerType(normalizeServerType(command.serverType()));
        entity.setConnectionConfig(normalizeConnectionConfig(command.connectionConfig()));
        entity.setStatus(STATUS_ACTIVE);
        mapper.insert(entity);
        discoverTools(entity);
        return toDTO(entity);
    }

    @Override
    @Transactional
    public McpServerDTO refreshTools(Long tenantId, Long mcpServerId) {
        McpServerEntity server = getOwned(tenantId, mcpServerId);
        discoverTools(server);
        return toDTO(server);
    }

    private void discoverTools(McpServerEntity server) {
        try {
            JsonNode result = listRemoteTools(server);
            JsonNode tools = result.path("tools");
            if (!tools.isArray()) return;
            Map<String, McpToolEntity> existingTools = toolMapper.selectList(new LambdaQueryWrapper<McpToolEntity>()
                            .eq(McpToolEntity::getMcpServerId, server.getId()))
                    .stream()
                    .collect(Collectors.toMap(McpToolEntity::getName, Function.identity(), (first, second) -> first));
            for (JsonNode tool : tools) {
                String name = tool.path("name").asText("");
                if (name.isBlank()) continue;
                McpToolEntity existing = existingTools.remove(name);
                if (existing == null) {
                    toolMapper.insert(newTool(server, tool, name));
                } else {
                    existing.setDescription(tool.path("description").asText(""));
                    existing.setParameterSchema(tool.path("inputSchema"));
                    existing.setStatus(TOOL_STATUS_AVAILABLE);
                    toolMapper.updateById(existing);
                }
            }
            disableMissingTools(existingTools);
        } catch (Exception ex) {
            log.warn("MCP tool discovery failed for server {}: {}", server.getName(), ex.getMessage());
        }
    }

    private JsonNode listRemoteTools(McpServerEntity server) {
        if ("STDIO".equalsIgnoreCase(server.getServerType())) {
            return stdioMcpClient.listTools(server);
        }
        if ("HTTP".equalsIgnoreCase(server.getServerType())) {
            return httpMcpClient.listTools(server);
        }
        return objectMapper.createObjectNode();
    }

    private McpToolEntity newTool(McpServerEntity server, JsonNode tool, String name) {
        McpToolEntity entity = new McpToolEntity();
        entity.setMcpServerId(server.getId());
        entity.setName(name);
        entity.setDescription(tool.path("description").asText(""));
        entity.setParameterSchema(tool.path("inputSchema"));
        entity.setStatus(TOOL_STATUS_AVAILABLE);
        return entity;
    }

    private void disableMissingTools(Map<String, McpToolEntity> missingTools) {
        for (McpToolEntity missingTool : missingTools.values()) {
            if (TOOL_STATUS_AVAILABLE.equals(missingTool.getStatus())) {
                missingTool.setStatus(STATUS_DISABLED);
                toolMapper.updateById(missingTool);
            }
        }
    }

    @Override
    public List<McpServerDTO> list(Long tenantId, String status) {
        requireNonNull(tenantId, "tenantId");
        return mapper.selectList(new LambdaQueryWrapper<McpServerEntity>()
                        .eq(McpServerEntity::getTenantId, tenantId)
                        .eq(status != null && !status.isBlank(), McpServerEntity::getStatus, status)
                        .orderByAsc(McpServerEntity::getId))
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public McpServerDTO disable(Long tenantId, Long mcpServerId) {
        McpServerEntity entity = getOwned(tenantId, mcpServerId);
        entity.setStatus(STATUS_DISABLED);
        mapper.updateById(entity);
        return toDTO(entity);
    }

    private McpServerEntity getOwned(Long tenantId, Long mcpServerId) {
        requireNonNull(tenantId, "tenantId");
        requireNonNull(mcpServerId, "mcpServerId");
        McpServerEntity entity = mapper.selectById(mcpServerId);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "MCP server is not accessible");
        }
        return entity;
    }

    private JsonNode normalizeConnectionConfig(JsonNode connectionConfig) {
        return connectionConfig == null || connectionConfig.isNull()
                ? objectMapper.createObjectNode()
                : connectionConfig;
    }

    private String normalizeServerType(String serverType) {
        String value = requiredText(serverType, "serverType").toUpperCase();
        if (!"STDIO".equals(value) && !"HTTP".equals(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "serverType must be STDIO or HTTP");
        }
        return value;
    }

    private McpServerDTO toDTO(McpServerEntity entity) {
        return new McpServerDTO(
                entity.getId(),
                entity.getName(),
                entity.getServerType(),
                entity.getConnectionConfig(),
                entity.getStatus()
        );
    }

    private Long requireNonNull(Long value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is required");
        }
        return value;
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is required");
        }
        return value.strip();
    }
}
