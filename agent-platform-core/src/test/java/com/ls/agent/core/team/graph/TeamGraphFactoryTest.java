package com.ls.agent.core.team.graph;

import com.ls.agent.core.agent.command.AgentRunCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamGraphFactoryTest {

    @Test
    void invokesMinimalGraphAndReturnsFinalState() {
        TeamGraphFactory factory = new TeamGraphFactory();
        AgentRunCommand command = new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "Plan a team activity",
                "trace-1",
                null,
                null,
                1000
        );
        TeamGraphState initialState = TeamGraphState.initial(command, 70001L);

        TeamGraphState finalState = factory.invoke(initialState, TeamGraphRuntimeContext.noop());

        assertThat(finalState.command()).isEqualTo(command);
        assertThat(finalState.conversationId()).isEqualTo(90001L);
        assertThat(finalState.runSpanId()).isEqualTo(70001L);
        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);
    }
}
