package com.ls.agent.core.mcp.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.entity.McpToolEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultMcpToolQueryService implements McpToolQueryService, McpToolRegistry {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String SERVER_STATUS_ACTIVE = "ACTIVE";

    private final McpToolMapper mcpToolMapper;
    private final McpServerMapper mcpServerMapper;

    public DefaultMcpToolQueryService(McpToolMapper mcpToolMapper, McpServerMapper mcpServerMapper) {
        this.mcpToolMapper = mcpToolMapper;
        this.mcpServerMapper = mcpServerMapper;
    }

    @Override
    public boolean areMcpToolsBindable(Long tenantId, List<Long> mcpToolIds) {
        if (mcpToolIds == null || mcpToolIds.isEmpty()) {
            return true;
        }
        List<Long> serverIds = activeServerIds(tenantId);
        if (serverIds.isEmpty()) {
            return false;
        }
        Long count = mcpToolMapper.selectCount(new LambdaQueryWrapper<McpToolEntity>()
                .in(McpToolEntity::getMcpServerId, serverIds)
                .eq(McpToolEntity::getStatus, STATUS_AVAILABLE)
                .in(McpToolEntity::getId, mcpToolIds));
        return count == mcpToolIds.stream().distinct().count();
    }

    @Override
    public List<McpToolDTO> listTools(Long tenantId, String status) {
        List<Long> serverIds = activeServerIds(tenantId);
        if (serverIds.isEmpty()) {
            return List.of();
        }
        List<McpToolEntity> tools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolEntity>()
                .in(McpToolEntity::getMcpServerId, serverIds)
                .eq(status != null && !status.isBlank(), McpToolEntity::getStatus, status)
                .orderByAsc(McpToolEntity::getId));
        return toDTOs(tools);
    }

    @Override
    public List<McpToolDTO> listAvailableTools(Long tenantId, List<Long> mcpToolIds) {
        List<Long> serverIds = activeServerIds(tenantId);
        if (serverIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<McpToolEntity> wrapper = new LambdaQueryWrapper<McpToolEntity>()
                .in(McpToolEntity::getMcpServerId, serverIds)
                .eq(McpToolEntity::getStatus, STATUS_AVAILABLE)
                .orderByAsc(McpToolEntity::getId);
        if (mcpToolIds != null && !mcpToolIds.isEmpty()) {
            wrapper.in(McpToolEntity::getId, mcpToolIds);
        }
        return toDTOs(mcpToolMapper.selectList(wrapper));
    }

    private List<Long> activeServerIds(Long tenantId) {
        return mcpServerMapper.selectList(new LambdaQueryWrapper<McpServerEntity>()
                        .eq(McpServerEntity::getTenantId, tenantId)
                        .eq(McpServerEntity::getStatus, SERVER_STATUS_ACTIVE))
                .stream()
                .map(McpServerEntity::getId)
                .toList();
    }

    private List<McpToolDTO> toDTOs(List<McpToolEntity> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> new McpToolDTO(
                        tool.getId(),
                        tool.getMcpServerId(),
                        tool.getName(),
                        tool.getDescription(),
                        tool.getStatus(),
                        tool.getParameterSchema()
                ))
                .toList();
    }
}
