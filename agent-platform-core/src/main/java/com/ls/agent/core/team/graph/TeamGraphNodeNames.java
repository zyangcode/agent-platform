package com.ls.agent.core.team.graph;

import org.bsc.langgraph4j.GraphDefinition;

public final class TeamGraphNodeNames {

    public static final String START = GraphDefinition.START;
    public static final String BUILD_CONTEXT = "build_context";
    public static final String PLAN = "plan";
    public static final String VALIDATE_PLAN = "validate_plan";
    public static final String SCHEDULE = "schedule";
    public static final String EXECUTE_BATCH = "execute_batch";
    public static final String FINAL_ANSWER = "final_answer";
    public static final String END = GraphDefinition.END;

    private TeamGraphNodeNames() {
    }
}
