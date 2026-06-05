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
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultMcpServerService implements McpServerService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final McpServerMapper mapper;
    private final ObjectMapper objectMapper;

    public DefaultMcpServerService(McpServerMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
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
        return toDTO(entity);
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
