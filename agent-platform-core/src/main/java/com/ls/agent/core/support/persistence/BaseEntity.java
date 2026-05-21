package com.ls.agent.core.support.persistence;

import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDateTime;

public abstract class BaseEntity extends CreatedEntity {

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
