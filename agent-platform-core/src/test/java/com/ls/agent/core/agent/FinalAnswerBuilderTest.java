package com.ls.agent.core.agent;

import com.ls.agent.core.agent.application.FinalAnswerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinalAnswerBuilderTest {

    private final FinalAnswerBuilder builder = new FinalAnswerBuilder();

    @Test
    void removesInternalExecutionLinesAndKeepsUserReadableAnswer() {
        String answer = builder.build("""
                tasks: [{"id":"t1","type":"TOOL_TASK"}]
                [mock search] raw result: [{"title":"internal"}]
                [{"debug":"raw"}]
                Final answer for the user.
                """);

        assertThat(answer).isEqualTo("Final answer for the user.");
    }

    @Test
    void returnsReadableFallbackWhenOnlyInternalContentRemains() {
        String answer = builder.build("""
                @mcp:read_file {"path":"/tmp/demo.txt"}
                {"error":"permission denied"}
                """);

        assertThat(answer)
                .contains("I could not produce a reliable final answer")
                .doesNotContain("@mcp:")
                .doesNotContain("permission denied");
    }
}
