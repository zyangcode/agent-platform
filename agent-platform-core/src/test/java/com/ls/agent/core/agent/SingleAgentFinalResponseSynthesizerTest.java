package com.ls.agent.core.agent;

import com.ls.agent.core.agent.application.SingleAgentFinalResponseSynthesizer;
import com.ls.agent.core.model.dto.ModelMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SingleAgentFinalResponseSynthesizerTest {

    private final SingleAgentFinalResponseSynthesizer synthesizer = new SingleAgentFinalResponseSynthesizer();

    @Test
    void cleanUserVisibleAnswerRemovesInternalToolSyntaxAndJson() {
        String answer = synthesizer.cleanUserVisibleAnswer("""
                @skill:calculator {"expression":"1+2"}
                {"result":"3"}
                The answer is 3.
                [tool failed] hidden
                """);

        assertThat(answer).isEqualTo("The answer is 3.");
    }

    @Test
    void cleanUserVisibleAnswerRemovesTraceSpanAndModelDiagnosticLines() {
        String answer = synthesizer.cleanUserVisibleAnswer("""
                trace_id=tr_001 span_id=42 status=SUCCESS
                spanId=43 model.invoke latencyMs=120
                message_delta #0 null
                I am AgentX, your AI assistant.
                """);

        assertThat(answer).isEqualTo("I am AgentX, your AI assistant.");
    }

    @Test
    void fallbackMessagesRemoveToolListingsAndAppendUserVisibleAnswerInstruction() {
        List<ModelMessage> messages = synthesizer.fallbackMessages(List.of(
                new ModelMessage("system", """
                        You are AgentX
                        Available skills:
                        - calculator: evaluate math
                        Available MCP tools:
                        - read_file: read file
                        """),
                new ModelMessage("assistant", "@skill:calculator {\"expression\":\"1+2\"}"),
                new ModelMessage("tool", "{\"result\":\"3\"}")
        ));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).content())
                .contains("You are AgentX")
                .doesNotContain("Available skills", "Available MCP tools", "calculator", "read_file");
        assertThat(messages.get(3).role()).isEqualTo("user");
        assertThat(messages.get(3).content())
                .contains("provide the best possible answer")
                .contains("Do NOT output tool-call format")
                .contains("Do NOT mention traces, spans, JSON, or internal execution details");
    }
}
