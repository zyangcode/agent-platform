package com.ls.agent.core.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.quota.entity.QuotaUsageEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaUsageMapper extends BaseMapper<QuotaUsageEntity> {

    default int reserveTokens(QuotaUsageEntity dailyUsage, QuotaUsageEntity monthlyUsage, Long dailyLimit, Long monthlyLimit) {
        ensureUsage(dailyUsage);
        ensureUsage(monthlyUsage);
        int dayUpdated = reservePeriodTokens(
                dailyUsage.getTenantId(), dailyUsage.getSubjectType(), dailyUsage.getSubjectId(),
                dailyUsage.getPeriodType(), dailyUsage.getPeriodKey(), dailyUsage.getReservedTokens(), dailyLimit);
        if (dayUpdated == 0) {
            return 0;
        }
        int monthUpdated = reservePeriodTokens(
                monthlyUsage.getTenantId(), monthlyUsage.getSubjectType(), monthlyUsage.getSubjectId(),
                monthlyUsage.getPeriodType(), monthlyUsage.getPeriodKey(), monthlyUsage.getReservedTokens(), monthlyLimit);
        if (monthUpdated == 0) {
            adjustPeriodTokens(
                    dailyUsage.getTenantId(), dailyUsage.getSubjectType(), dailyUsage.getSubjectId(),
                    dailyUsage.getPeriodType(), dailyUsage.getPeriodKey(), -dailyUsage.getReservedTokens());
            return 0;
        }
        return 1;
    }

    default void adjustReservedTokens(
            Long tenantId,
            String subjectType,
            Long subjectId,
            String dayPeriodKey,
            String monthPeriodKey,
            Long delta
    ) {
        if (delta == null || delta == 0 || subjectType == null || subjectId == null) {
            return;
        }
        adjustPeriodTokens(tenantId, subjectType, subjectId, "DAY", dayPeriodKey, delta);
        adjustPeriodTokens(tenantId, subjectType, subjectId, "MONTH", monthPeriodKey, delta);
    }

    @Insert("""
            insert into quota_usage (
                tenant_id, subject_type, subject_id, period_type, period_key, reserved_tokens, status, version
            ) values (
                #{usage.tenantId}, #{usage.subjectType}, #{usage.subjectId},
                #{usage.periodType}, #{usage.periodKey}, 0, 'ACTIVE', 0
            )
            on conflict (tenant_id, subject_type, subject_id, period_type, period_key) do nothing
            """)
    int ensureUsage(@Param("usage") QuotaUsageEntity usage);

    @Update("""
            update quota_usage
            set reserved_tokens = reserved_tokens + #{tokens},
                version = version + 1,
                updated_at = current_timestamp
            where tenant_id = #{tenantId}
              and subject_type = #{subjectType}
              and subject_id = #{subjectId}
              and period_type = #{periodType}
              and period_key = #{periodKey}
              and status = 'ACTIVE'
              and (#{limit} <= 0 or reserved_tokens + #{tokens} <= #{limit})
            """)
    int reservePeriodTokens(
            @Param("tenantId") Long tenantId,
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("periodType") String periodType,
            @Param("periodKey") String periodKey,
            @Param("tokens") Long tokens,
            @Param("limit") Long limit
    );

    @Update("""
            update quota_usage
            set reserved_tokens = greatest(0, reserved_tokens + #{delta}),
                version = version + 1,
                updated_at = current_timestamp
            where tenant_id = #{tenantId}
              and subject_type = #{subjectType}
              and subject_id = #{subjectId}
              and period_type = #{periodType}
              and period_key = #{periodKey}
              and status = 'ACTIVE'
            """)
    int adjustPeriodTokens(
            @Param("tenantId") Long tenantId,
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("periodType") String periodType,
            @Param("periodKey") String periodKey,
            @Param("delta") Long delta
    );
}
