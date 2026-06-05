package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.experience.api.ExperienceSkillService;
import com.ls.agent.core.experience.command.CreateExperienceSkillCommand;
import com.ls.agent.core.experience.command.UpdateExperienceSkillCommand;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.web.dto.CreateExperienceSkillRequest;
import com.ls.agent.web.dto.UpdateExperienceSkillRequest;
import com.ls.agent.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExperienceSkillController {

    private final ExperienceSkillService experienceSkillService;

    public ExperienceSkillController(ExperienceSkillService experienceSkillService) {
        this.experienceSkillService = experienceSkillService;
    }

    @PostMapping("/api/experience-skills")
    public ApiResponse<ExperienceSkillDTO> create(
            CurrentUser currentUser,
            @Valid @RequestBody CreateExperienceSkillRequest request
    ) {
        return ApiResponse.success(experienceSkillService.create(new CreateExperienceSkillCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                request.applicationId(),
                request.profileId(),
                request.code(),
                request.name(),
                request.domain(),
                request.triggerKeywords(),
                request.content()
        )));
    }

    @GetMapping("/api/experience-skills")
    public ApiResponse<PageResult<ExperienceSkillDTO>> list(
            CurrentUser currentUser,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(experienceSkillService.page(
                currentUser.tenantId(),
                currentUser.userId(),
                applicationId,
                PageRequestNormalizer.pageNo(pageNo),
                PageRequestNormalizer.pageSize(pageSize)
        ));
    }

    @PutMapping("/api/experience-skills/{experienceSkillId}")
    public ApiResponse<ExperienceSkillDTO> update(
            CurrentUser currentUser,
            @PathVariable("experienceSkillId") Long experienceSkillId,
            @Valid @RequestBody UpdateExperienceSkillRequest request
    ) {
        return ApiResponse.success(experienceSkillService.update(new UpdateExperienceSkillCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                request.applicationId(),
                experienceSkillId,
                request.code(),
                request.name(),
                request.domain(),
                request.triggerKeywords(),
                request.content()
        )));
    }

    @PostMapping("/api/experience-skills/{experienceSkillId}/disable")
    public ApiResponse<ExperienceSkillDTO> disable(
            CurrentUser currentUser,
            @PathVariable("experienceSkillId") Long experienceSkillId,
            @RequestParam("applicationId") Long applicationId
    ) {
        return ApiResponse.success(experienceSkillService.disable(
                currentUser.tenantId(),
                currentUser.userId(),
                applicationId,
                experienceSkillId
        ));
    }
}
