package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.mcp.api.McpServerService;
import com.ls.agent.core.mcp.command.CreateMcpServerCommand;
import com.ls.agent.core.mcp.dto.McpServerDTO;
import com.ls.agent.web.dto.CreateMcpServerRequest;
import com.ls.agent.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping("/api/mcp-servers")
    public ApiResponse<List<McpServerDTO>> list(
            CurrentUser currentUser,
            @RequestParam(name = "status", required = false) String status
    ) {
        return ApiResponse.success(mcpServerService.list(currentUser.tenantId(), status));
    }

    @PostMapping("/api/mcp-servers")
    public ApiResponse<McpServerDTO> create(
            CurrentUser currentUser,
            @Valid @RequestBody CreateMcpServerRequest request
    ) {
        return ApiResponse.success(mcpServerService.create(new CreateMcpServerCommand(
                currentUser.tenantId(),
                request.name(),
                request.serverType(),
                request.connectionConfig()
        )));
    }

    @PostMapping("/api/mcp-servers/{mcpServerId}/disable")
    public ApiResponse<McpServerDTO> disable(
            CurrentUser currentUser,
            @PathVariable("mcpServerId") Long mcpServerId
    ) {
        return ApiResponse.success(mcpServerService.disable(currentUser.tenantId(), mcpServerId));
    }

    @PostMapping("/api/mcp-servers/{mcpServerId}/refresh-tools")
    public ApiResponse<McpServerDTO> refreshTools(
            CurrentUser currentUser,
            @PathVariable("mcpServerId") Long mcpServerId
    ) {
        return ApiResponse.success(mcpServerService.refreshTools(currentUser.tenantId(), mcpServerId));
    }
}
