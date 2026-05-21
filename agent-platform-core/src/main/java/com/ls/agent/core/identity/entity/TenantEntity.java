package com.ls.agent.core.identity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.BaseEntity;

@TableName("tenants")
public class TenantEntity extends BaseEntity {

    private String name;
    private String code;
    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
