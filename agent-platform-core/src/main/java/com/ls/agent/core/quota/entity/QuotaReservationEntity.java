package com.ls.agent.core.quota.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.VersionedEntity;

@TableName("quota_reservations")
public class QuotaReservationEntity extends VersionedEntity {

    @TableField("trace_id")
    private String traceId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("application_id")
    private Long applicationId;

    @TableField("user_id")
    private Long userId;

    @TableField("estimated_tokens")
    private Long estimatedTokens;

    @TableField("actual_tokens")
    private Long actualTokens;

    private String status;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEstimatedTokens() {
        return estimatedTokens;
    }

    public void setEstimatedTokens(Long estimatedTokens) {
        this.estimatedTokens = estimatedTokens;
    }

    public Long getActualTokens() {
        return actualTokens;
    }

    public void setActualTokens(Long actualTokens) {
        this.actualTokens = actualTokens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
