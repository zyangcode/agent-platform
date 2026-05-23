package com.ls.agent.core.quota.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.QueryTokenUsagePageCommand;
import com.ls.agent.core.quota.command.QueryTokenUsageSummaryCommand;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.quota.dto.TokenUsageAggregateDTO;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.quota.dto.TokenUsageSummaryDTO;
import com.ls.agent.core.quota.dto.TokenUsageTopModelDTO;
import com.ls.agent.core.quota.entity.TokenUsageLogEntity;
import com.ls.agent.core.quota.mapper.TokenUsageLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DefaultTokenUsageService implements TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenUsageService.class);
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOP_MODEL_LIMIT = 5;

    private final TokenUsageLogMapper tokenUsageMapper;

    public DefaultTokenUsageService(TokenUsageLogMapper tokenUsageMapper) {
        this.tokenUsageMapper = tokenUsageMapper;
    }

    @Override
    public void record(RecordTokenUsageCommand command) {
        TokenUsageLogEntity entity = new TokenUsageLogEntity();
        entity.setTraceId(requireText(command.traceId(), "traceId"));
        entity.setSpanId(command.spanId());
        entity.setTenantId(requireNonNull(command.tenantId(), "tenantId"));
        entity.setApplicationId(command.applicationId());
        entity.setUserId(command.userId());
        entity.setProfileId(command.profileId());
        entity.setModelConfigId(requireNonNull(command.modelConfigId(), "modelConfigId"));
        entity.setProviderId(requireNonNull(command.providerId(), "providerId"));
        entity.setModelName(requireText(command.modelName(), "modelName"));
        entity.setProviderType(requireText(command.providerType(), "providerType"));
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

    @Override
    public PageResult<TokenUsageDTO> pageTokenUsages(QueryTokenUsagePageCommand command) {
        Long tenantId = requireNonNull(command.tenantId(), "tenantId");
        Long userId = requireNonNull(command.userId(), "userId");
        int pageNo = normalizePageNo(command.pageNo());
        int pageSize = normalizePageSize(command.pageSize());

        LambdaQueryWrapper<TokenUsageLogEntity> wrapper = baseOwnedWrapper(tenantId, userId)
                .eq(command.applicationId() != null, TokenUsageLogEntity::getApplicationId, command.applicationId())
                .eq(command.modelConfigId() != null, TokenUsageLogEntity::getModelConfigId, command.modelConfigId())
                .eq(command.providerId() != null, TokenUsageLogEntity::getProviderId, command.providerId())
                .orderByDesc(TokenUsageLogEntity::getCreatedAt)
                .orderByDesc(TokenUsageLogEntity::getId);
        Page<TokenUsageLogEntity> page = tokenUsageMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<TokenUsageDTO> records = page.getRecords().stream()
                .map(this::toDTO)
                .toList();
        return PageResult.of(records, pageNo, pageSize, page.getTotal());
    }

    @Override
    public TokenUsageSummaryDTO summarizeTokenUsages(QueryTokenUsageSummaryCommand command) {
        Long tenantId = requireNonNull(command.tenantId(), "tenantId");
        Long userId = requireNonNull(command.userId(), "userId");
        List<TokenUsageLogEntity> usages = tokenUsageMapper.selectList(baseOwnedWrapper(tenantId, userId)
                .eq(command.applicationId() != null, TokenUsageLogEntity::getApplicationId, command.applicationId())
                .ge(command.startedFrom() != null, TokenUsageLogEntity::getCreatedAt, command.startedFrom())
                .lt(command.startedTo() != null, TokenUsageLogEntity::getCreatedAt, command.startedTo()));

        int promptTokens = usages.stream().map(TokenUsageLogEntity::getPromptTokens).mapToInt(this::defaultInt).sum();
        int completionTokens = usages.stream().map(TokenUsageLogEntity::getCompletionTokens).mapToInt(this::defaultInt).sum();
        int totalTokens = usages.stream().map(TokenUsageLogEntity::getTotalTokens).mapToInt(this::defaultInt).sum();
        int estimatedCount = (int) usages.stream().filter(usage -> Boolean.TRUE.equals(usage.getEstimated())).count();
        return new TokenUsageSummaryDTO(
                command.applicationId(),
                promptTokens,
                completionTokens,
                totalTokens,
                usages.size(),
                estimatedCount,
                usages.size() - estimatedCount,
                topModels(usages)
        );
    }

    @Override
    public List<TokenUsageDTO> listByTrace(Long tenantId, Long userId, String traceId) {
        return tokenUsageMapper.selectList(baseOwnedWrapper(
                        requireNonNull(tenantId, "tenantId"),
                        requireNonNull(userId, "userId"))
                        .eq(TokenUsageLogEntity::getTraceId, requireText(traceId, "traceId"))
                        .orderByAsc(TokenUsageLogEntity::getCreatedAt)
                        .orderByAsc(TokenUsageLogEntity::getId))
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public Map<String, TokenUsageAggregateDTO> aggregateByTraceIds(
            Long tenantId,
            Long userId,
            List<String> traceIds,
            Long modelConfigId
    ) {
        requireNonNull(tenantId, "tenantId");
        requireNonNull(userId, "userId");
        if (traceIds == null || traceIds.isEmpty()) {
            return Map.of();
        }
        return tokenUsageMapper.selectList(baseOwnedWrapper(tenantId, userId)
                        .in(TokenUsageLogEntity::getTraceId, traceIds)
                        .eq(modelConfigId != null, TokenUsageLogEntity::getModelConfigId, modelConfigId))
                .stream()
                .collect(Collectors.groupingBy(
                        TokenUsageLogEntity::getTraceId,
                        Collectors.collectingAndThen(Collectors.toList(), this::aggregateTokenUsage)
                ));
    }

    private LambdaQueryWrapper<TokenUsageLogEntity> baseOwnedWrapper(Long tenantId, Long userId) {
        return new LambdaQueryWrapper<TokenUsageLogEntity>()
                .eq(TokenUsageLogEntity::getTenantId, tenantId)
                .eq(TokenUsageLogEntity::getUserId, userId);
    }

    private TokenUsageDTO toDTO(TokenUsageLogEntity entity) {
        return new TokenUsageDTO(
                entity.getId(),
                entity.getTraceId(),
                entity.getSpanId(),
                entity.getTenantId(),
                entity.getApplicationId(),
                entity.getUserId(),
                entity.getProfileId(),
                entity.getModelConfigId(),
                entity.getProviderId(),
                entity.getModelName(),
                entity.getProviderType(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getTotalTokens(),
                entity.getEstimated(),
                entity.getCreatedAt()
        );
    }

    private List<TokenUsageTopModelDTO> topModels(List<TokenUsageLogEntity> usages) {
        return usages.stream()
                .collect(Collectors.groupingBy(this::modelKey))
                .entrySet()
                .stream()
                .map(entry -> toTopModel(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(TokenUsageTopModelDTO::totalTokens).reversed())
                .limit(TOP_MODEL_LIMIT)
                .toList();
    }

    private TokenUsageTopModelDTO toTopModel(ModelKey key, List<TokenUsageLogEntity> usages) {
        int totalTokens = usages.stream()
                .map(TokenUsageLogEntity::getTotalTokens)
                .mapToInt(this::defaultInt)
                .sum();
        return new TokenUsageTopModelDTO(
                key.modelConfigId(),
                key.modelName(),
                key.providerType(),
                usages.size(),
                totalTokens
        );
    }

    private TokenUsageAggregateDTO aggregateTokenUsage(List<TokenUsageLogEntity> usages) {
        int totalTokens = usages.stream()
                .map(TokenUsageLogEntity::getTotalTokens)
                .mapToInt(this::defaultInt)
                .sum();
        boolean estimated = usages.stream()
                .anyMatch(usage -> Boolean.TRUE.equals(usage.getEstimated()));
        return new TokenUsageAggregateDTO(totalTokens, estimated);
    }

    private ModelKey modelKey(TokenUsageLogEntity entity) {
        return new ModelKey(entity.getModelConfigId(), entity.getModelName(), entity.getProviderType());
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
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

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private <T> T requireNonNull(T value, String field) {
        return Objects.requireNonNull(value, field + " is required");
    }

    private record ModelKey(Long modelConfigId, String modelName, String providerType) {
    }
}
