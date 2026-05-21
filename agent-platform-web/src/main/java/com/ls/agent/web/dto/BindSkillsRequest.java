package com.ls.agent.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BindSkillsRequest(
        @NotNull List<Long> skillIds
) {
}
