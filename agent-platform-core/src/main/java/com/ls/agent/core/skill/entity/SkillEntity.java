package com.ls.agent.core.skill.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.BaseEntity;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;

@TableName(value = "skills", autoResultMap = true)
public class SkillEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    private String name;
    private String code;
    private String description;

    @TableField("skill_type")
    private String skillType;

    private String scope;

    @TableField(value = "permission_declaration", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode permissionDeclaration;

    private String status;

    @TableField("current_version_id")
    private Long currentVersionId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSkillType() {
        return skillType;
    }

    public void setSkillType(String skillType) {
        this.skillType = skillType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public JsonNode getPermissionDeclaration() {
        return permissionDeclaration;
    }

    public void setPermissionDeclaration(JsonNode permissionDeclaration) {
        this.permissionDeclaration = permissionDeclaration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }
}
