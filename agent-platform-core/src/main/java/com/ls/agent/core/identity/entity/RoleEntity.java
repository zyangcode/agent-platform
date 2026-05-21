package com.ls.agent.core.identity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.CreatedEntity;

@TableName("roles")
public class RoleEntity extends CreatedEntity {

    private String code;
    private String name;
    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
