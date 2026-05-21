package com.ls.agent.core.support.persistence;

import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDateTime;

public abstract class SoftDeleteEntity extends BaseEntity {

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
