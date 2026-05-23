package com.ls.agent.core.security.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.CreatedEntity;

@TableName("security_events")
public class SecurityEventEntity extends CreatedEntity {

    @TableField("trace_id")
    private String traceId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("application_id")
    private Long applicationId;

    @TableField("user_id")
    private Long userId;

    @TableField("event_type")
    private String eventType;

    private String location;

    @TableField("source_text_hash")
    private String sourceTextHash;

    @TableField("masked_sample")
    private String maskedSample;

    private String action;

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSourceTextHash() {
        return sourceTextHash;
    }

    public void setSourceTextHash(String sourceTextHash) {
        this.sourceTextHash = sourceTextHash;
    }

    public String getMaskedSample() {
        return maskedSample;
    }

    public void setMaskedSample(String maskedSample) {
        this.maskedSample = maskedSample;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
