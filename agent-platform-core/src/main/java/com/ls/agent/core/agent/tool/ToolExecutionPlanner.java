package com.ls.agent.core.agent.tool;

import java.util.List;

public interface ToolExecutionPlanner {

    ToolExecutionPlan plan(List<ToolExecutionPlan.Item> items, int maxParallelTools);
}
