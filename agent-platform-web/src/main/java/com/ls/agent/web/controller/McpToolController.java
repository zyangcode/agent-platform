package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class McpToolController {

    private final McpToolQueryService mcpToolQueryService;

    public McpToolController(McpToolQueryService mcpToolQueryService) {
        this.mcpToolQueryService = mcpToolQueryService;
    }

    @GetMapping("/api/mcp-tools")
    public ApiResponse<List<McpToolDTO>> listTools(
            CurrentUser currentUser,
            @RequestParam(name = "status", required = false) String status
    ) {
        return ApiResponse.success(mcpToolQueryService.listTools(currentUser.tenantId(), status));
    }
}
