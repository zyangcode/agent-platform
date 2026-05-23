package com.ls.agent.core.trace.application;

import com.ls.agent.core.trace.api.TokenUsageService;
import com.ls.agent.core.trace.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.entity.TokenUsageLogEntity;
import com.ls.agent.core.trace.mapper.TokenUsageLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultTokenUsageService implements TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenUsageService.class);

    private final TokenUsageLogMapper tokenUsageMapper;

    public DefaultTokenUsageService(TokenUsageLogMapper tokenUsageMapper) {
        this.tokenUsageMapper = tokenUsageMapper;
    }

    @Override
    public void record(RecordTokenUsageCommand command) {
        TokenUsageLogEntity entity = new TokenUsageLogEntity();
        entity.setTraceId(TraceValidation.requireText(command.traceId(), "traceId"));
        entity.setSpanId(command.spanId());
        entity.setTenantId(TraceValidation.requireNonNull(command.tenantId(), "tenantId"));
        entity.setApplicationId(command.applicationId());
        entity.setUserId(command.userId());
        entity.setProfileId(command.profileId());
        entity.setModelConfigId(TraceValidation.requireNonNull(command.modelConfigId(), "modelConfigId"));
        entity.setProviderId(TraceValidation.requireNonNull(command.providerId(), "providerId"));
        entity.setModelName(TraceValidation.requireText(command.modelName(), "modelName"));
        entity.setProviderType(TraceValidation.requireText(command.providerType(), "providerType"));
        entity.setPromptTokens(defaultInt(command.promptTokens()));
        entity.setCompletionTokens(defaultInt(command.completionTokens()));
        entity.setTotalTokens(defaultInt(command.totalTokens()));
        entity.setEstimated(command.estimated() != null && command.estimated());
        try {
            tokenUsageMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Token usage write failed, traceId={}, modelName={}", command.traceId(), command.modelName(), ex);
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
