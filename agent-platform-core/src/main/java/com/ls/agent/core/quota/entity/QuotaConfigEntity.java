package com.ls.agent.core.quota.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.VersionedEntity;

@TableName("quota_configs")
public class QuotaConfigEntity extends VersionedEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("daily_limit_tokens")
    private Long dailyLimitTokens;

    @TableField("monthly_limit_tokens")
    private Long monthlyLimitTokens;

    @TableField("single_request_limit_tokens")
    private Long singleRequestLimitTokens;

    private String status;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getDailyLimitTokens() {
        return dailyLimitTokens;
    }

    public void setDailyLimitTokens(Long dailyLimitTokens) {
        this.dailyLimitTokens = dailyLimitTokens;
    }

    public Long getMonthlyLimitTokens() {
        return monthlyLimitTokens;
    }

    public void setMonthlyLimitTokens(Long monthlyLimitTokens) {
        this.monthlyLimitTokens = monthlyLimitTokens;
    }

    public Long getSingleRequestLimitTokens() {
        return singleRequestLimitTokens;
    }

    public void setSingleRequestLimitTokens(Long singleRequestLimitTokens) {
        this.singleRequestLimitTokens = singleRequestLimitTokens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
