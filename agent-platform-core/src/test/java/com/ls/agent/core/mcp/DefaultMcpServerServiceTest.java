package com.ls.agent.core.mcp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.mcp.application.DefaultMcpServerService;
import com.ls.agent.core.mcp.application.HttpMcpClient;
import com.ls.agent.core.mcp.application.SpringAiMcpClientAdapter;
import com.ls.agent.core.mcp.application.StdioMcpClient;
import com.ls.agent.core.mcp.command.CreateMcpServerCommand;
import com.ls.agent.core.mcp.dto.McpServerDTO;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.entity.McpToolEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMcpServerServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpServerMapper mapper = mock(McpServerMapper.class);
    private final McpToolMapper toolMapper = mock(McpToolMapper.class);
    private final StdioMcpClient stdioClient = mock(StdioMcpClient.class);
    private final HttpMcpClient httpClient = mock(HttpMcpClient.class);
    private final SpringAiMcpClientAdapter springAiMcpClient = mock(SpringAiMcpClientAdapter.class);
    private final DefaultMcpServerService service = new DefaultMcpServerService(
            mapper, toolMapper, objectMapper, stdioClient, httpClient, springAiMcpClient);

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

    @Test
    void refreshToolsUpdatesExistingAddsNewAndDisablesMissingTools() {
        when(mapper.selectById(10L)).thenReturn(activeServer());
        McpToolEntity existing = tool(1L, "search", "Old search", "AVAILABLE");
        McpToolEntity removed = tool(2L, "weather.current", "Weather", "AVAILABLE");
        when(toolMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing, removed));
        when(stdioClient.listTools(any())).thenReturn(objectMapper.createObjectNode().set("tools", tools(
                toolNode("search", "New search"),
                toolNode("calculator", "Calculator")
        )));

        McpServerDTO result = service.refreshTools(1L, 10L);

        assertThat(result.mcpServerId()).isEqualTo(10L);
        ArgumentCaptor<McpToolEntity> updateCaptor = ArgumentCaptor.forClass(McpToolEntity.class);
        verify(toolMapper, times(2)).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getAllValues())
                .extracting(McpToolEntity::getName, McpToolEntity::getDescription, McpToolEntity::getStatus)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("search", "New search", "AVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("weather.current", "Weather", "DISABLED")
                );
        ArgumentCaptor<McpToolEntity> insertCaptor = ArgumentCaptor.forClass(McpToolEntity.class);
        verify(toolMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getName()).isEqualTo("calculator");
        assertThat(insertCaptor.getValue().getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void createStreamableHttpDiscoversToolsThroughSpringAiMcpClientAdapter() {
        when(toolMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(springAiMcpClient.listTools(any())).thenReturn(objectMapper.createObjectNode().set("tools", tools(
                toolNode("web.search", "Search web")
        )));

        service.create(new CreateMcpServerCommand(
                1L,
                "Remote MCP",
                "streamable_http",
                objectMapper.createObjectNode().put("baseUrl", "https://mcp.example.test").put("endpoint", "/mcp")
        ));

        ArgumentCaptor<McpServerEntity> serverCaptor = ArgumentCaptor.forClass(McpServerEntity.class);
        verify(mapper).insert(serverCaptor.capture());
        assertThat(serverCaptor.getValue().getServerType()).isEqualTo("STREAMABLE_HTTP");
        verify(springAiMcpClient).listTools(serverCaptor.getValue());
        ArgumentCaptor<McpToolEntity> toolCaptor = ArgumentCaptor.forClass(McpToolEntity.class);
        verify(toolMapper).insert(toolCaptor.capture());
        assertThat(toolCaptor.getValue().getName()).isEqualTo("web.search");
        assertThat(toolCaptor.getValue().getDescription()).isEqualTo("Search web");
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

    private McpToolEntity tool(Long id, String name, String description, String status) {
        McpToolEntity tool = new McpToolEntity();
        tool.setId(id);
        tool.setMcpServerId(10L);
        tool.setName(name);
        tool.setDescription(description);
        tool.setStatus(status);
        return tool;
    }

    private ArrayNode tools(com.fasterxml.jackson.databind.JsonNode... nodes) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (com.fasterxml.jackson.databind.JsonNode node : nodes) {
            tools.add(node);
        }
        return tools;
    }

    private com.fasterxml.jackson.databind.JsonNode toolNode(String name, String description) {
        return objectMapper.createObjectNode()
                .put("name", name)
                .put("description", description)
                .set("inputSchema", objectMapper.createObjectNode()
                        .put("type", "object"));
    }
}
