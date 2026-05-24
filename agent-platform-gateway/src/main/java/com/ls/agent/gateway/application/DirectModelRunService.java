package com.ls.agent.gateway.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectModelRunService {

    private final ModelInvokeService modelInvokeService;
    private final TraceService traceService;
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;

    public DirectModelRunService(
            ModelInvokeService modelInvokeService,
            TraceService traceService,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper
    ) {
        this.modelInvokeService = modelInvokeService;
        this.traceService = traceService;
        this.tokenUsageService = tokenUsageService;
        this.objectMapper = objectMapper;
    }

    public ModelInvokeResult run(
            String traceId,
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long modelConfigId,
            List<ModelMessage> messages
    ) {
        TraceSpanDTO span = safeStartSpan(traceId, modelConfigId);
        try {
            ModelInvokeResult result = modelInvokeService.invoke(new ModelInvokeCommand(
                    modelConfigId,
                    messages,
                    null,
                    false
            ));
            safeRecordTokenUsage(traceId, span == null ? null : span.id(), tenantId, applicationId, userId, profileId, result);
            safeFinishSpan(span, "SUCCESS", null, null);
            return result;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private TraceSpanDTO safeStartSpan(String traceId, Long modelConfigId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        try {
            return traceService.startSpan(new StartTraceSpanCommand(
                    traceId,
                    null,
                    "model.invoke",
                    "MODEL",
                    "core",
                    objectMapper.createObjectNode().put("modelConfigId", modelConfigId == null ? 0L : modelConfigId)
            ));
        } catch (Exception ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break the direct model run.
        }
    }

    private void safeRecordTokenUsage(
            String traceId,
            Long spanId,
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            ModelInvokeResult result
    ) {
        if (traceId == null || traceId.isBlank() || result == null || result.usage() == null) {
            return;
        }
        try {
            tokenUsageService.record(new RecordTokenUsageCommand(
                    traceId,
                    spanId,
                    tenantId,
                    applicationId,
                    userId,
                    profileId,
                    result.modelConfigId(),
                    result.providerId(),
                    result.modelName(),
                    result.providerType(),
                    result.usage().promptTokens(),
                    result.usage().completionTokens(),
                    result.usage().totalTokens(),
                    result.usage().estimated()
            ));
        } catch (Exception ex) {
            // Token usage is accounting data in stage 2; do not break chat.
        }
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "Direct model run failed" : ex.getMessage();
    }
}
