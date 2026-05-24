package com.ls.agent.core.trace.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.dto.TokenUsageAggregateDTO;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.QueryTracePageCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceDetailDTO;
import com.ls.agent.core.trace.dto.TraceRootDTO;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import com.ls.agent.core.trace.dto.TraceSummaryDTO;
import com.ls.agent.core.trace.entity.TraceRootEntity;
import com.ls.agent.core.trace.entity.TraceSpanEntity;
import com.ls.agent.core.trace.mapper.TraceRootMapper;
import com.ls.agent.core.trace.mapper.TraceSpanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DefaultTraceService implements TraceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTraceService.class);
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TraceRootMapper rootMapper;
    private final TraceSpanMapper spanMapper;
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DefaultTraceService(
            TraceRootMapper rootMapper,
            TraceSpanMapper spanMapper,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper
    ) {
        this(rootMapper, spanMapper, tokenUsageService, objectMapper, Clock.systemDefaultZone());
    }

    public DefaultTraceService(
            TraceRootMapper rootMapper,
            TraceSpanMapper spanMapper,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.rootMapper = rootMapper;
        this.spanMapper = spanMapper;
        this.tokenUsageService = tokenUsageService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public TraceRootDTO startRoot(StartTraceRootCommand command) {
        TraceRootEntity entity = new TraceRootEntity();
        entity.setTraceId(TraceValidation.requireText(command.traceId(), "traceId"));
        entity.setTenantId(TraceValidation.requireNonNull(command.tenantId(), "tenantId"));
        entity.setApplicationId(command.applicationId());
        entity.setUserId(command.userId());
        entity.setProfileId(command.profileId());
        entity.setConversationId(command.conversationId());
        entity.setClientRequestId(command.clientRequestId());
        entity.setEntrypoint(TraceValidation.requireText(command.entrypoint(), "entrypoint"));
        entity.setAgentMode(command.agentMode());
        entity.setStatus(TraceConstants.STATUS_RUNNING);
        entity.setStartedAt(now());
        entity.setMetadata(TraceValidation.objectOrEmpty(command.metadata(), objectMapper));
        try {
            rootMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Trace root write failed, traceId={}", command.traceId(), ex);
        }
        return toRootDTO(entity);
    }

    @Override
    public void finishRoot(FinishTraceRootCommand command) {
        TraceValidation.requireText(command.traceId(), "traceId");
        LocalDateTime endedAt = now();
        LocalDateTime startedAt = findRootStartedAt(command.traceId());
        TraceRootEntity entity = new TraceRootEntity();
        entity.setConversationId(command.conversationId());
        entity.setStatus(TraceValidation.requireText(command.status(), "status"));
        entity.setErrorCode(command.errorCode());
        entity.setErrorMessage(command.errorMessage());
        entity.setEndedAt(endedAt);
        entity.setLatencyMs(latencyMillis(startedAt, endedAt));
        try {
            rootMapper.update(entity, new LambdaUpdateWrapper<TraceRootEntity>()
                    .eq(TraceRootEntity::getTraceId, command.traceId()));
        } catch (Exception ex) {
            log.warn("Trace root finish failed, traceId={}", command.traceId(), ex);
        }
    }

    @Override
    public TraceSpanDTO startSpan(StartTraceSpanCommand command) {
        TraceSpanEntity entity = new TraceSpanEntity();
        entity.setTraceId(TraceValidation.requireText(command.traceId(), "traceId"));
        entity.setParentSpanId(command.parentSpanId());
        entity.setSpanName(TraceValidation.requireText(command.spanName(), "spanName"));
        entity.setSpanType(TraceValidation.requireText(command.spanType(), "spanType"));
        entity.setComponent(TraceValidation.requireText(command.component(), "component"));
        entity.setStatus(TraceConstants.STATUS_RUNNING);
        entity.setStartedAt(now());
        entity.setAttributes(TraceValidation.objectOrEmpty(command.attributes(), objectMapper));
        try {
            spanMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Trace span write failed, traceId={}, spanName={}", command.traceId(), command.spanName(), ex);
        }
        return toSpanDTO(entity);
    }

    @Override
    public void finishSpan(FinishTraceSpanCommand command) {
        TraceValidation.requireNonNull(command.spanId(), "spanId");
        LocalDateTime endedAt = now();
        LocalDateTime startedAt = findSpanStartedAt(command.spanId());
        TraceSpanEntity entity = new TraceSpanEntity();
        entity.setStatus(TraceValidation.requireText(command.status(), "status"));
        entity.setErrorCode(command.errorCode());
        entity.setErrorMessage(command.errorMessage());
        entity.setEndedAt(endedAt);
        entity.setLatencyMs(latencyMillis(startedAt, endedAt));
        try {
            spanMapper.update(entity, new LambdaUpdateWrapper<TraceSpanEntity>()
                    .eq(TraceSpanEntity::getId, command.spanId()));
        } catch (Exception ex) {
            log.warn("Trace span finish failed, spanId={}", command.spanId(), ex);
        }
    }

    @Override
    public TraceDetailDTO getTrace(Long tenantId, Long userId, String traceId) {
        TraceRootEntity root = rootMapper.selectOne(new LambdaQueryWrapper<TraceRootEntity>()
                .eq(TraceRootEntity::getTenantId, TraceValidation.requireNonNull(tenantId, "tenantId"))
                .eq(TraceRootEntity::getUserId, TraceValidation.requireNonNull(userId, "userId"))
                .eq(TraceRootEntity::getTraceId, TraceValidation.requireText(traceId, "traceId")));
        if (root == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Trace is unavailable");
        }

        List<TraceSpanDTO> spans = spanMapper.selectList(new LambdaQueryWrapper<TraceSpanEntity>()
                        .eq(TraceSpanEntity::getTraceId, traceId)
                        .orderByAsc(TraceSpanEntity::getStartedAt)
                        .orderByAsc(TraceSpanEntity::getId))
                .stream()
                .map(this::toSpanDTO)
                .toList();
        List<TokenUsageDTO> tokenUsages = tokenUsageService.listByTrace(tenantId, userId, traceId);
        return toTraceDetailDTO(root, spans, tokenUsages);
    }

    @Override
    public PageResult<TraceSummaryDTO> pageTraces(QueryTracePageCommand command) {
        Long tenantId = TraceValidation.requireNonNull(command.tenantId(), "tenantId");
        Long userId = TraceValidation.requireNonNull(command.userId(), "userId");
        int pageNo = normalizePageNo(command.pageNo());
        int pageSize = normalizePageSize(command.pageSize());

        LambdaQueryWrapper<TraceRootEntity> wrapper = new LambdaQueryWrapper<TraceRootEntity>()
                .eq(TraceRootEntity::getTenantId, tenantId)
                .eq(TraceRootEntity::getUserId, userId)
                .eq(command.applicationId() != null, TraceRootEntity::getApplicationId, command.applicationId())
                .eq(command.profileId() != null, TraceRootEntity::getProfileId, command.profileId())
                .eq(command.status() != null && !command.status().isBlank(), TraceRootEntity::getStatus, command.status())
                .eq(command.entrypoint() != null && !command.entrypoint().isBlank(), TraceRootEntity::getEntrypoint, command.entrypoint())
                .orderByDesc(TraceRootEntity::getStartedAt)
                .orderByDesc(TraceRootEntity::getId);
        Page<TraceRootEntity> page = rootMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<TraceRootEntity> roots = page.getRecords();
        Map<String, TokenUsageAggregateDTO> tokenAggregates = tokenUsageService.aggregateByTraceIds(
                tenantId,
                userId,
                roots.stream().map(TraceRootEntity::getTraceId).toList(),
                command.modelConfigId()
        );
        List<TraceSummaryDTO> records = roots.stream()
                .map(root -> toTraceSummaryDTO(root, tokenAggregates.get(root.getTraceId())))
                .toList();
        return PageResult.of(records, pageNo, pageSize, page.getTotal());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime findRootStartedAt(String traceId) {
        try {
            TraceRootEntity root = rootMapper.selectOne(new LambdaQueryWrapper<TraceRootEntity>()
                    .eq(TraceRootEntity::getTraceId, traceId));
            return root == null ? null : root.getStartedAt();
        } catch (Exception ex) {
            log.warn("Trace root start time query failed, traceId={}", traceId, ex);
            return null;
        }
    }

    private LocalDateTime findSpanStartedAt(Long spanId) {
        try {
            TraceSpanEntity span = spanMapper.selectById(spanId);
            return span == null ? null : span.getStartedAt();
        } catch (Exception ex) {
            log.warn("Trace span start time query failed, spanId={}", spanId, ex);
            return null;
        }
    }

    private long latencyMillis(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(start, end).toMillis());
    }

    private TraceRootDTO toRootDTO(TraceRootEntity entity) {
        return new TraceRootDTO(
                entity.getId(),
                entity.getTraceId(),
                entity.getTenantId(),
                entity.getApplicationId(),
                entity.getUserId(),
                entity.getProfileId(),
                entity.getConversationId(),
                entity.getClientRequestId(),
                entity.getEntrypoint(),
                entity.getAgentMode(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getLatencyMs(),
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private TraceSpanDTO toSpanDTO(TraceSpanEntity entity) {
        return new TraceSpanDTO(
                entity.getId(),
                entity.getTraceId(),
                entity.getParentSpanId(),
                entity.getSpanName(),
                entity.getSpanType(),
                entity.getComponent(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getLatencyMs(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getAttributes(),
                entity.getCreatedAt()
        );
    }

    private TraceDetailDTO toTraceDetailDTO(
            TraceRootEntity root,
            List<TraceSpanDTO> spans,
            List<TokenUsageDTO> tokenUsages
    ) {
        return new TraceDetailDTO(
                root.getTraceId(),
                root.getTenantId(),
                root.getApplicationId(),
                root.getUserId(),
                root.getProfileId(),
                root.getConversationId(),
                root.getClientRequestId(),
                root.getEntrypoint(),
                root.getAgentMode(),
                root.getStatus(),
                root.getErrorCode(),
                root.getErrorMessage(),
                root.getStartedAt(),
                root.getEndedAt(),
                root.getLatencyMs(),
                root.getMetadata(),
                spans,
                tokenUsages
        );
    }

    private TraceSummaryDTO toTraceSummaryDTO(TraceRootEntity root, TokenUsageAggregateDTO tokenAggregate) {
        return new TraceSummaryDTO(
                root.getTraceId(),
                root.getApplicationId(),
                root.getUserId(),
                root.getProfileId(),
                root.getConversationId(),
                root.getEntrypoint(),
                root.getAgentMode(),
                root.getStatus(),
                root.getLatencyMs(),
                tokenAggregate == null ? 0 : tokenAggregate.totalTokens(),
                tokenAggregate != null && Boolean.TRUE.equals(tokenAggregate.estimated()),
                root.getStartedAt(),
                root.getEndedAt()
        );
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
