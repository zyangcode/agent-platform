package com.ls.agent.core.profile.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.support.persistence.JsonNodeTypeHandler;
import com.ls.agent.core.support.persistence.CreatedEntity;

@TableName(value = "profile_skills", autoResultMap = true)
public class ProfileSkillEntity extends CreatedEntity {

    @TableField("profile_id")
    private Long profileId;

    @TableField("skill_id")
    private Long skillId;

    @TableField("enabled_by_default")
    private Boolean enabledByDefault;

    private Boolean required;

    @TableField(value = "config_override", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode configOverride;

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public Boolean getEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(Boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public JsonNode getConfigOverride() {
        return configOverride;
    }

    public void setConfigOverride(JsonNode configOverride) {
        this.configOverride = configOverride;
    }
}
