package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.QueryTokenUsagePageCommand;
import com.ls.agent.core.quota.command.QueryTokenUsageSummaryCommand;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.quota.dto.TokenUsageSummaryDTO;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;

    public TokenUsageController(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    @GetMapping("/api/token-usages")
    public ApiResponse<PageResult<TokenUsageDTO>> list(
            CurrentUser currentUser,
            @RequestParam(name = "applicationId", required = false) Long applicationId,
            @RequestParam(name = "modelConfigId", required = false) Long modelConfigId,
            @RequestParam(name = "providerId", required = false) Long providerId,
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(tokenUsageService.pageTokenUsages(new QueryTokenUsagePageCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                applicationId,
                modelConfigId,
                providerId,
                PageRequestNormalizer.pageNo(pageNo),
                PageRequestNormalizer.pageSize(pageSize)
        )));
    }

    @GetMapping("/api/token-usages/summary")
    public ApiResponse<TokenUsageSummaryDTO> summary(
            CurrentUser currentUser,
            @RequestParam(name = "applicationId", required = false) Long applicationId,
            @RequestParam(name = "startedFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedFrom,
            @RequestParam(name = "startedTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedTo
    ) {
        return ApiResponse.success(tokenUsageService.summarizeTokenUsages(new QueryTokenUsageSummaryCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                applicationId,
                startedFrom,
                startedTo
        )));
    }
}
