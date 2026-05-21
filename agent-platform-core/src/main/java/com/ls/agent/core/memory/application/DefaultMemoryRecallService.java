package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultMemoryRecallService implements MemoryRecallService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MemoryMapper memoryMapper;

    public DefaultMemoryRecallService(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public List<MemoryDTO> recall(Long tenantId, Long applicationId, Long userId, Long profileId, String query, int limit) {
        return memoryMapper.selectList(new LambdaQueryWrapper<MemoryEntity>()
                        .eq(MemoryEntity::getTenantId, tenantId)
                        .eq(MemoryEntity::getUserId, userId)
                        .eq(MemoryEntity::getStatus, STATUS_ACTIVE)
                        .and(wrapper -> wrapper
                                .isNull(MemoryEntity::getApplicationId)
                                .or()
                                .eq(MemoryEntity::getApplicationId, applicationId))
                        .and(wrapper -> wrapper
                                .isNull(MemoryEntity::getProfileId)
                                .or()
                                .eq(MemoryEntity::getProfileId, profileId))
                        .orderByDesc(MemoryEntity::getUpdatedAt)
                        .last("limit " + Math.max(1, limit)))
                .stream()
                .map(memory -> new MemoryDTO(memory.getMemoryType(), memory.getContent()))
                .toList();
    }
}
