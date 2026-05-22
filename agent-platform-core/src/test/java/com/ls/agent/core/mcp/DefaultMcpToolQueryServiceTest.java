package com.ls.agent.core.mcp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.mcp.application.DefaultMcpToolQueryService;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.entity.McpToolEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultMcpToolQueryServiceTest {

    private final McpToolMapper toolMapper = mock(McpToolMapper.class);
    private final McpServerMapper serverMapper = mock(McpServerMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultMcpToolQueryService service = new DefaultMcpToolQueryService(toolMapper, serverMapper);

    @Test
    void listToolsReturnsAvailableToolsFromTenantServers() {
        McpServerEntity server = server();
        McpToolEntity tool = tool();
        when(serverMapper.selectList(any(Wrapper.class))).thenReturn(List.of(server));
        when(toolMapper.selectList(any(Wrapper.class))).thenReturn(List.of(tool));

        List<McpToolDTO> result = service.listTools(1L, "AVAILABLE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mcpToolId()).isEqualTo(1L);
        assertThat(result.get(0).mcpServerId()).isEqualTo(10L);
        assertThat(result.get(0).name()).isEqualTo("read_file");
        assertThat(result.get(0).parameterSchema().get("properties").has("path")).isTrue();
    }

    @Test
    void areMcpToolsBindableRequiresToolsFromTenantActiveServers() {
        when(serverMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(toolMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        boolean result = service.areMcpToolsBindable(1L, List.of(99L));

        assertThat(result).isFalse();
    }

    private McpServerEntity server() {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(10L);
        entity.setTenantId(1L);
        entity.setName("Readonly Filesystem MCP");
        entity.setServerType("STDIO");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private McpToolEntity tool() {
        McpToolEntity entity = new McpToolEntity();
        entity.setId(1L);
        entity.setMcpServerId(10L);
        entity.setName("read_file");
        entity.setDescription("Read an allowed local text file in readonly mode");
        entity.setParameterSchema(objectMapper.createObjectNode()
                .put("type", "object")
                .set("properties", objectMapper.createObjectNode()
                        .set("path", objectMapper.createObjectNode().put("type", "string"))));
        entity.setStatus("AVAILABLE");
        return entity;
    }
}
