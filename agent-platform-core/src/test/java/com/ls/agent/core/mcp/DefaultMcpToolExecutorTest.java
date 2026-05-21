package com.ls.agent.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.mcp.application.DefaultMcpToolExecutor;
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMcpToolExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultMcpToolExecutor executor = new DefaultMcpToolExecutor(objectMapper);

    @Test
    void readFileReturnsMockReadonlyContent() {
        McpToolExecuteResult result = executor.execute(new McpToolExecuteCommand(
                1L,
                10001L,
                "read_file",
                objectMapper.createObjectNode().put("path", "demo.txt")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("content").asText()).contains("demo.txt");
        assertThat(result.output().get("readonly").asBoolean()).isTrue();
    }
}
