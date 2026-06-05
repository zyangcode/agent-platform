package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.QueryTracePageCommand;
import com.ls.agent.core.trace.dto.TraceDetailDTO;
import com.ls.agent.core.trace.dto.TraceSummaryDTO;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/api/traces/{traceId}")
    public ApiResponse<TraceDetailDTO> get(CurrentUser currentUser, @PathVariable("traceId") String traceId) {
        return ApiResponse.success(traceService.getTrace(
                currentUser.tenantId(),
                currentUser.userId(),
                traceId
        ));
    }

    @GetMapping("/api/traces")
    public ApiResponse<PageResult<TraceSummaryDTO>> list(
            CurrentUser currentUser,
            @RequestParam(name = "applicationId", required = false) Long applicationId,
            @RequestParam(name = "profileId", required = false) Long profileId,
            @RequestParam(name = "modelConfigId", required = false) Long modelConfigId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "entrypoint", required = false) String entrypoint,
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(traceService.pageTraces(new QueryTracePageCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                applicationId,
                profileId,
                modelConfigId,
                status,
                entrypoint,
                PageRequestNormalizer.pageNo(pageNo),
                PageRequestNormalizer.pageSize(pageSize)
        )));
    }
}
