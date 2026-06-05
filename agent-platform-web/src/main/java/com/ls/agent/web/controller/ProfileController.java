package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.command.BindMcpToolsCommand;
import com.ls.agent.core.profile.command.BindSkillsCommand;
import com.ls.agent.core.profile.command.CreateProfileCommand;
import com.ls.agent.core.profile.command.UpdateProfileCommand;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.web.dto.BindMcpToolsRequest;
import com.ls.agent.web.dto.BindSkillsRequest;
import com.ls.agent.web.dto.CreateProfileRequest;
import com.ls.agent.web.dto.UpdateProfileRequest;
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
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/api/profiles")
    public ApiResponse<ProfileDTO> create(CurrentUser currentUser, @Valid @RequestBody CreateProfileRequest request) {
        return ApiResponse.success(profileService.createProfile(new CreateProfileCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                request.applicationId(),
                request.name(),
                request.profileType(),
                request.description(),
                request.modelConfigId(),
                request.promptExtra(),
                request.memoryStrategy(),
                request.maxSteps(),
                request.executionMode(),
                request.visibility()
        )));
    }

    @GetMapping("/api/profiles")
    public ApiResponse<PageResult<ProfileDTO>> list(
            CurrentUser currentUser,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(profileService.pageProfiles(
                currentUser.tenantId(),
                currentUser.userId(),
                applicationId,
                PageRequestNormalizer.pageNo(pageNo),
                PageRequestNormalizer.pageSize(pageSize)
        ));
    }

    @GetMapping("/api/profiles/{profileId}")
    public ApiResponse<ProfileDTO> get(CurrentUser currentUser, @PathVariable("profileId") Long profileId) {
        return ApiResponse.success(profileService.getProfile(
                currentUser.tenantId(),
                currentUser.userId(),
                profileId
        ));
    }

    @PutMapping("/api/profiles/{profileId}")
    public ApiResponse<ProfileDTO> update(
            CurrentUser currentUser,
            @PathVariable("profileId") Long profileId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ApiResponse.success(profileService.updateProfile(new UpdateProfileCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                profileId,
                request.name(),
                request.description(),
                request.modelConfigId(),
                request.promptExtra(),
                request.memoryStrategy(),
                request.maxSteps(),
                request.executionMode(),
                request.visibility()
        )));
    }

    @PostMapping("/api/profiles/{profileId}/disable")
    public ApiResponse<ProfileDTO> disable(CurrentUser currentUser, @PathVariable("profileId") Long profileId) {
        return ApiResponse.success(profileService.disableProfile(
                currentUser.tenantId(),
                currentUser.userId(),
                profileId
        ));
    }

    @PutMapping("/api/profiles/{profileId}/skills")
    public ApiResponse<Boolean> bindSkills(
            CurrentUser currentUser,
            @PathVariable("profileId") Long profileId,
            @Valid @RequestBody BindSkillsRequest request
    ) {
        profileService.bindSkills(new BindSkillsCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                profileId,
                request.skillIds()
        ));
        return ApiResponse.success(true);
    }

    @PutMapping("/api/profiles/{profileId}/mcp-tools")
    public ApiResponse<Boolean> bindMcpTools(
            CurrentUser currentUser,
            @PathVariable("profileId") Long profileId,
            @Valid @RequestBody BindMcpToolsRequest request
    ) {
        profileService.bindMcpTools(new BindMcpToolsCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                profileId,
                request.mcpToolIds()
        ));
        return ApiResponse.success(true);
    }
}
