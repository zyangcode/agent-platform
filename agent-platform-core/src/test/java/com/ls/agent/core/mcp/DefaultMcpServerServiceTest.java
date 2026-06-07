package com.ls.agent.core.mcp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.mcp.application.DefaultMcpServerService;
import com.ls.agent.core.mcp.application.HttpMcpClient;
import com.ls.agent.core.mcp.application.StdioMcpClient;
import com.ls.agent.core.mcp.command.CreateMcpServerCommand;
import com.ls.agent.core.mcp.dto.McpServerDTO;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMcpServerServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpServerMapper mapper = mock(McpServerMapper.class);
    private final McpToolMapper toolMapper = mock(McpToolMapper.class);
    private final StdioMcpClient stdioClient = mock(StdioMcpClient.class);
    private final HttpMcpClient httpClient = mock(HttpMcpClient.class);
    private final DefaultMcpServerService service = new DefaultMcpServerService(
            mapper, toolMapper, objectMapper, stdioClient, httpClient);

    @Test
    void createPersistsActiveServerInTenantScope() {
        when(stdioClient.listTools(any())).thenReturn(objectMapper.createObjectNode());
        service.create(new CreateMcpServerCommand(
                1L,
                "Readonly Filesystem MCP",
                "stdio",
                objectMapper.createObjectNode().put("command", "builtin-demo-filesystem-mcp")
        ));

        ArgumentCaptor<McpServerEntity> captor = ArgumentCaptor.forClass(McpServerEntity.class);
        verify(mapper).insert(captor.capture());
        McpServerEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(1L);
        assertThat(entity.getName()).isEqualTo("Readonly Filesystem MCP");
        assertThat(entity.getServerType()).isEqualTo("STDIO");
        assertThat(entity.getConnectionConfig().get("command").asText()).isEqualTo("builtin-demo-filesystem-mcp");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listReturnsOnlyRequestedTenantServers() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeServer()));

        List<McpServerDTO> result = service.list(1L, "ACTIVE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mcpServerId()).isEqualTo(10L);
        assertThat(result.get(0).name()).isEqualTo("Readonly Filesystem MCP");
        assertThat(result.get(0).serverType()).isEqualTo("STDIO");
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void disableRejectsOtherTenantServer() {
        McpServerEntity server = activeServer();
        server.setTenantId(2L);
        when(mapper.selectById(10L)).thenReturn(server);

        assertThatThrownBy(() -> service.disable(1L, 10L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void disableMarksOwnedServerDisabled() {
        when(mapper.selectById(10L)).thenReturn(activeServer());

        McpServerDTO result = service.disable(1L, 10L);

        ArgumentCaptor<McpServerEntity> captor = ArgumentCaptor.forClass(McpServerEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
        assertThat(result.mcpServerId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo("DISABLED");
    }

    private McpServerEntity activeServer() {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(10L);
        entity.setTenantId(1L);
        entity.setName("Readonly Filesystem MCP");
        entity.setServerType("STDIO");
        entity.setConnectionConfig(objectMapper.createObjectNode().put("command", "builtin-demo-filesystem-mcp"));
        entity.setStatus("ACTIVE");
        return entity;
    }
}
