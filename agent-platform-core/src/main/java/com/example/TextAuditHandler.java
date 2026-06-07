package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.skill.api.JarSkillHandler;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;

public class TextAuditHandler implements JarSkillHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] SENSITIVE = {"暴力", "赌博", "毒品", "诈骗"};

    @Override
    public SkillExecuteResult execute(SkillExecuteCommand command) {
        String text = command.arguments().path("text").asText("");
        if (text.isBlank()) {
            return new SkillExecuteResult(false, command.skillCode(),
                    MAPPER.createObjectNode().put("error", "text is required"), null);
        }

        boolean hasSensitive = false;
        for (String word : SENSITIVE) {
            if (text.contains(word)) {
                hasSensitive = true;
                break;
            }
        }

        ObjectNode output = MAPPER.createObjectNode();
        output.put("text", text);
        output.put("passed", !hasSensitive);
        output.put("result", hasSensitive ? "包含敏感词" : "审核通过");
        return new SkillExecuteResult(true, command.skillCode(), output, null);
    }
}
