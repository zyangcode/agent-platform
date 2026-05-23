package com.ls.agent.core.quota.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.quota.api.QuotaService;
import com.ls.agent.core.quota.command.CommitQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReserveQuotaCommand;
import com.ls.agent.core.quota.dto.QuotaReservationDTO;
import com.ls.agent.core.quota.entity.QuotaConfigEntity;
import com.ls.agent.core.quota.entity.QuotaReservationEntity;
import com.ls.agent.core.quota.mapper.QuotaConfigMapper;
import com.ls.agent.core.quota.mapper.QuotaReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultQuotaService implements QuotaService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_COMMITTED = "COMMITTED";
    private static final String STATUS_RELEASED = "RELEASED";
    private static final String SUBJECT_APPLICATION = "APPLICATION";
    private static final String SUBJECT_USER = "USER";

    private final QuotaConfigMapper configMapper;
    private final QuotaReservationMapper reservationMapper;

    public DefaultQuotaService(
            QuotaConfigMapper configMapper,
            QuotaReservationMapper reservationMapper
    ) {
        this.configMapper = configMapper;
        this.reservationMapper = reservationMapper;
    }

    @Override
    @Transactional
    public QuotaReservationDTO reserve(ReserveQuotaCommand command) {
        String traceId = requireText(command.traceId(), "traceId");
        Long tenantId = requireNonNull(command.tenantId(), "tenantId");
        Long estimatedTokens = normalizeTokens(command.estimatedTokens());

        QuotaConfigEntity config = findActiveConfig(tenantId, command.applicationId(), command.userId());
        if (config != null && exceedsSingleRequestLimit(config, estimatedTokens)) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded");
        }
        if (config != null) {
            touchConfigVersion(config);
        }

        QuotaReservationEntity existing = findReserved(traceId);
        if (existing != null) {
            return toDTO(existing);
        }

        QuotaReservationEntity reservation = new QuotaReservationEntity();
        reservation.setTraceId(traceId);
        reservation.setTenantId(tenantId);
        reservation.setApplicationId(command.applicationId());
        reservation.setUserId(command.userId());
        reservation.setEstimatedTokens(estimatedTokens);
        reservation.setStatus(STATUS_RESERVED);
        reservation.setVersion(0);
        reservationMapper.insert(reservation);
        return toDTO(reservation);
    }

    @Override
    @Transactional
    public void commit(CommitQuotaReservationCommand command) {
        QuotaReservationEntity reservation = findReserved(requireText(command.traceId(), "traceId"));
        if (reservation == null) {
            return;
        }
        QuotaReservationEntity update = new QuotaReservationEntity();
        update.setActualTokens(normalizeTokens(command.actualTokens()));
        update.setStatus(STATUS_COMMITTED);
        reservationMapper.update(update, statusCasWrapper(reservation));
    }

    @Override
    @Transactional
    public void release(ReleaseQuotaReservationCommand command) {
        QuotaReservationEntity reservation = findReserved(requireText(command.traceId(), "traceId"));
        if (reservation == null) {
            return;
        }
        QuotaReservationEntity update = new QuotaReservationEntity();
        update.setStatus(STATUS_RELEASED);
        reservationMapper.update(update, statusCasWrapper(reservation));
    }

    private QuotaConfigEntity findActiveConfig(Long tenantId, Long applicationId, Long userId) {
        if (applicationId != null) {
            QuotaConfigEntity applicationConfig = findConfig(tenantId, SUBJECT_APPLICATION, applicationId);
            if (applicationConfig != null) {
                return applicationConfig;
            }
        }
        if (userId != null) {
            return findConfig(tenantId, SUBJECT_USER, userId);
        }
        return null;
    }

    private QuotaConfigEntity findConfig(Long tenantId, String subjectType, Long subjectId) {
        return configMapper.selectOne(new LambdaQueryWrapper<QuotaConfigEntity>()
                .eq(QuotaConfigEntity::getTenantId, tenantId)
                .eq(QuotaConfigEntity::getSubjectType, subjectType)
                .eq(QuotaConfigEntity::getSubjectId, subjectId)
                .eq(QuotaConfigEntity::getStatus, STATUS_ACTIVE));
    }

    private QuotaReservationEntity findReserved(String traceId) {
        return reservationMapper.selectOne(new LambdaQueryWrapper<QuotaReservationEntity>()
                .eq(QuotaReservationEntity::getTraceId, traceId)
                .eq(QuotaReservationEntity::getStatus, STATUS_RESERVED));
    }

    private LambdaUpdateWrapper<QuotaReservationEntity> statusCasWrapper(QuotaReservationEntity reservation) {
        return new LambdaUpdateWrapper<QuotaReservationEntity>()
                .eq(QuotaReservationEntity::getTraceId, reservation.getTraceId())
                .eq(QuotaReservationEntity::getStatus, STATUS_RESERVED)
                .eq(QuotaReservationEntity::getVersion, reservation.getVersion());
    }

    private void touchConfigVersion(QuotaConfigEntity config) {
        Integer currentVersion = config.getVersion() == null ? 0 : config.getVersion();
        QuotaConfigEntity update = new QuotaConfigEntity();
        update.setVersion(currentVersion + 1);
        int updated = configMapper.update(update, new LambdaUpdateWrapper<QuotaConfigEntity>()
                .eq(QuotaConfigEntity::getId, config.getId())
                .eq(QuotaConfigEntity::getStatus, STATUS_ACTIVE)
                .eq(QuotaConfigEntity::getVersion, currentVersion));
        if (updated == 0) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota reservation conflict");
        }
    }

    private boolean exceedsSingleRequestLimit(QuotaConfigEntity config, Long estimatedTokens) {
        Long limit = config.getSingleRequestLimitTokens();
        return limit != null && limit > 0 && estimatedTokens > limit;
    }

    private QuotaReservationDTO toDTO(QuotaReservationEntity entity) {
        return new QuotaReservationDTO(
                entity.getTraceId(),
                entity.getTenantId(),
                entity.getApplicationId(),
                entity.getUserId(),
                entity.getEstimatedTokens(),
                entity.getActualTokens(),
                entity.getStatus(),
                entity.getVersion()
        );
    }

    private Long normalizeTokens(Long tokens) {
        return tokens == null || tokens < 0 ? 0L : tokens;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
        return value;
    }

    private <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
        return value;
    }
}
