package com.ls.agent.core.quota.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.VersionedEntity;

@TableName("quota_usage")
public class QuotaUsageEntity extends VersionedEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("period_type")
    private String periodType;

    @TableField("period_key")
    private String periodKey;

    @TableField("reserved_tokens")
    private Long reservedTokens;

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

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public Long getReservedTokens() {
        return reservedTokens;
    }

    public void setReservedTokens(Long reservedTokens) {
        this.reservedTokens = reservedTokens;
    }
}
