package com.ls.agent.core.agent.tool;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultToolExecutionPlanner implements ToolExecutionPlanner {

    @Override
    public ToolExecutionPlan plan(List<ToolExecutionPlan.Item> items, int maxParallelTools) {
        List<ToolExecutionPlan.Item> safeItems = items == null ? List.of() : items;
        int batchSize = Math.max(1, maxParallelTools);
        List<ToolExecutionPlan.Group> groups = new ArrayList<>();
        List<ToolExecutionPlan.Item> currentParallel = new ArrayList<>();
        Set<String> currentResourceKeys = new HashSet<>();

        for (ToolExecutionPlan.Item item : safeItems) {
            if (!canRunInParallel(item)) {
                flushParallel(groups, currentParallel, currentResourceKeys);
                groups.add(new ToolExecutionPlan.Group(false, List.of(item)));
                continue;
            }
            if (currentParallel.size() >= batchSize || conflicts(currentResourceKeys, resourceKeys(item))) {
                flushParallel(groups, currentParallel, currentResourceKeys);
            }
            currentParallel.add(item);
            currentResourceKeys.addAll(resourceKeys(item));
        }
        flushParallel(groups, currentParallel, currentResourceKeys);
        return new ToolExecutionPlan(groups);
    }

    private boolean canRunInParallel(ToolExecutionPlan.Item item) {
        AgentToolDTO tool = item == null ? null : item.tool();
        return tool != null
                && tool.readOnly()
                && tool.riskLevel() == AgentToolRiskLevel.LOW;
    }

    private boolean conflicts(Set<String> plannedKeys, List<String> itemKeys) {
        for (String key : itemKeys) {
            if (plannedKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private List<String> resourceKeys(ToolExecutionPlan.Item item) {
        if (item == null || item.tool() == null || item.tool().resourceKeys() == null) {
            return List.of();
        }
        return item.tool().resourceKeys();
    }

    private void flushParallel(
            List<ToolExecutionPlan.Group> groups,
            List<ToolExecutionPlan.Item> currentParallel,
            Set<String> currentResourceKeys
    ) {
        if (currentParallel.isEmpty()) {
            return;
        }
        groups.add(new ToolExecutionPlan.Group(currentParallel.size() > 1, List.copyOf(currentParallel)));
        currentParallel.clear();
        currentResourceKeys.clear();
    }
}
