package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SkillController {

    private final SkillQueryService skillQueryService;

    public SkillController(SkillQueryService skillQueryService) {
        this.skillQueryService = skillQueryService;
    }

    @GetMapping("/api/skills")
    public ApiResponse<List<SkillDTO>> listSkills(
            CurrentUser currentUser,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "status", required = false) String status
    ) {
        return ApiResponse.success(skillQueryService.listSkills(currentUser.tenantId(), scope, status));
    }
}
