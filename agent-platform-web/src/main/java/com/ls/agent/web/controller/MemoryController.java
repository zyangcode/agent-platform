package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.memory.api.MemoryManagementService;
import com.ls.agent.core.memory.command.UpdateMemoryCommand;
import com.ls.agent.core.memory.dto.MemoryRecordDTO;
import com.ls.agent.web.dto.UpdateMemoryRequest;
import com.ls.agent.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MemoryController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final MemoryManagementService memoryManagementService;

    public MemoryController(MemoryManagementService memoryManagementService) {
        this.memoryManagementService = memoryManagementService;
    }

    @GetMapping("/api/memories")
    public ApiResponse<List<MemoryRecordDTO>> list(
            CurrentUser currentUser,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(name = "profileId", required = false) Long profileId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit
    ) {
        return ApiResponse.success(memoryManagementService.list(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                category,
                query,
                clampLimit(limit)
        ));
    }

    @PatchMapping("/api/memories/{memoryId}")
    public ApiResponse<MemoryRecordDTO> update(
            CurrentUser currentUser,
            @PathVariable("memoryId") Long memoryId,
            @Valid @RequestBody UpdateMemoryRequest request
    ) {
        return ApiResponse.success(memoryManagementService.update(new UpdateMemoryCommand(
                currentUser.tenantId(),
                request.applicationId(),
                currentUser.userId(),
                request.profileId(),
                memoryId,
                request.content(),
                request.memoryCategory(),
                request.tags(),
                request.importance(),
                request.slotHint()
        )));
    }

    @DeleteMapping("/api/memories/{memoryId}")
    public ApiResponse<Integer> disable(
            CurrentUser currentUser,
            @PathVariable("memoryId") Long memoryId,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(name = "profileId", required = false) Long profileId
    ) {
        return ApiResponse.success(memoryManagementService.disable(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                memoryId
        ));
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
