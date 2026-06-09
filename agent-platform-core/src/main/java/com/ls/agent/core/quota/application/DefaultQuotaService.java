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
import com.ls.agent.core.quota.entity.QuotaUsageEntity;
import com.ls.agent.core.quota.mapper.QuotaConfigMapper;
import com.ls.agent.core.quota.mapper.QuotaReservationMapper;
import com.ls.agent.core.quota.mapper.QuotaUsageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class DefaultQuotaService implements QuotaService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_COMMITTED = "COMMITTED";
    private static final String STATUS_RELEASED = "RELEASED";
    private static final String SUBJECT_APPLICATION = "APPLICATION";
    private static final String SUBJECT_USER = "USER";
    private static final String PERIOD_DAY = "DAY";
    private static final String PERIOD_MONTH = "MONTH";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final QuotaConfigMapper configMapper;
    private final QuotaReservationMapper reservationMapper;
    private final QuotaUsageMapper usageMapper;

    public DefaultQuotaService(
            QuotaConfigMapper configMapper,
            QuotaReservationMapper reservationMapper,
            QuotaUsageMapper usageMapper
    ) {
        this.configMapper = configMapper;
        this.reservationMapper = reservationMapper;
        this.usageMapper = usageMapper;
    }

    @Override
    @Transactional
    public QuotaReservationDTO reserve(ReserveQuotaCommand command) {
        String traceId = requireText(command.traceId(), "traceId");
        Long tenantId = requireNonNull(command.tenantId(), "tenantId");
        Long estimatedTokens = normalizeTokens(command.estimatedTokens());

        QuotaReservationEntity existing = findReserved(traceId);
        if (existing != null) {
            return toDTO(existing);
        }

        QuotaConfigEntity config = findActiveConfig(tenantId, command.applicationId(), command.userId());
        QuotaUsageReservation usageReservation = null;
        if (config != null && exceedsSingleRequestLimit(config, estimatedTokens)) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded");
        }
        if (config != null) {
            usageReservation = reserveUsage(config, estimatedTokens);
        }

        QuotaReservationEntity reservation = new QuotaReservationEntity();
        reservation.setTraceId(traceId);
        reservation.setTenantId(tenantId);
        reservation.setApplicationId(command.applicationId());
        reservation.setUserId(command.userId());
        reservation.setEstimatedTokens(estimatedTokens);
        applyUsageReservation(reservation, usageReservation);
        reservation.setStatus(STATUS_RESERVED);
        reservation.setVersion(0);
        try {
            reservationMapper.insert(reservation);
        } catch (RuntimeException ex) {
            rollbackUsageReservation(tenantId, usageReservation, estimatedTokens);
            throw ex;
        }
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
        int updated = reservationMapper.update(update, statusCasWrapper(reservation));
        if (updated > 0) {
            usageMapper.adjustReservedTokens(
                    reservation.getTenantId(),
                    reservation.getQuotaSubjectType(),
                    reservation.getQuotaSubjectId(),
                    reservation.getQuotaDayPeriodKey(),
                    reservation.getQuotaMonthPeriodKey(),
                    update.getActualTokens() - normalizeTokens(reservation.getEstimatedTokens()));
        }
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
        int updated = reservationMapper.update(update, statusCasWrapper(reservation));
        if (updated > 0) {
            usageMapper.adjustReservedTokens(
                    reservation.getTenantId(),
                    reservation.getQuotaSubjectType(),
                    reservation.getQuotaSubjectId(),
                    reservation.getQuotaDayPeriodKey(),
                    reservation.getQuotaMonthPeriodKey(),
                    -normalizeTokens(reservation.getEstimatedTokens()));
        }
    }

    private QuotaUsageReservation reserveUsage(QuotaConfigEntity config, Long estimatedTokens) {
        Long tokens = normalizeTokens(estimatedTokens);
        if (tokens == 0) {
            return null;
        }
        LocalDate today = LocalDate.now();
        String dayPeriodKey = DAY_FORMATTER.format(today);
        String monthPeriodKey = MONTH_FORMATTER.format(today);
        QuotaUsageEntity dailyUsage = usage(config, PERIOD_DAY, dayPeriodKey, tokens);
        QuotaUsageEntity monthlyUsage = usage(config, PERIOD_MONTH, monthPeriodKey, tokens);
        int reserved = usageMapper.reserveTokens(
                dailyUsage,
                monthlyUsage,
                normalizeLimit(config.getDailyLimitTokens()),
                normalizeLimit(config.getMonthlyLimitTokens()));
        if (reserved == 0) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded");
        }
        return new QuotaUsageReservation(config.getSubjectType(), config.getSubjectId(), dayPeriodKey, monthPeriodKey);
    }

    private void applyUsageReservation(QuotaReservationEntity reservation, QuotaUsageReservation usageReservation) {
        if (usageReservation == null) {
            return;
        }
        reservation.setQuotaSubjectType(usageReservation.subjectType());
        reservation.setQuotaSubjectId(usageReservation.subjectId());
        reservation.setQuotaDayPeriodKey(usageReservation.dayPeriodKey());
        reservation.setQuotaMonthPeriodKey(usageReservation.monthPeriodKey());
    }

    private void rollbackUsageReservation(Long tenantId, QuotaUsageReservation usageReservation, Long estimatedTokens) {
        if (usageReservation == null) {
            return;
        }
        usageMapper.adjustReservedTokens(
                tenantId,
                usageReservation.subjectType(),
                usageReservation.subjectId(),
                usageReservation.dayPeriodKey(),
                usageReservation.monthPeriodKey(),
                -normalizeTokens(estimatedTokens));
    }

    private QuotaUsageEntity usage(QuotaConfigEntity config, String periodType, String periodKey, Long tokens) {
        QuotaUsageEntity usage = new QuotaUsageEntity();
        usage.setTenantId(config.getTenantId());
        usage.setSubjectType(config.getSubjectType());
        usage.setSubjectId(config.getSubjectId());
        usage.setPeriodType(periodType);
        usage.setPeriodKey(periodKey);
        usage.setReservedTokens(tokens);
        usage.setVersion(0);
        return usage;
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

    private Long normalizeLimit(Long tokens) {
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

    private record QuotaUsageReservation(
            String subjectType,
            Long subjectId,
            String dayPeriodKey,
            String monthPeriodKey
    ) {
    }
}
