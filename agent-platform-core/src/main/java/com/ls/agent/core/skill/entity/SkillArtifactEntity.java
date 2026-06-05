package com.ls.agent.core.skill.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ls.agent.core.support.persistence.CreatedEntity;

@TableName("skill_artifacts")
public class SkillArtifactEntity extends CreatedEntity {

    @TableField("skill_version_id")
    private Long skillVersionId;

    @TableField("artifact_type")
    private String artifactType;

    @TableField("storage_path")
    private String storagePath;

    @TableField("file_name")
    private String fileName;

    @TableField("size_bytes")
    private Long sizeBytes;

    private String checksum;

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public void setSkillVersionId(Long skillVersionId) {
        this.skillVersionId = skillVersionId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
