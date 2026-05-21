package com.ls.agent.core.memory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.core.memory.application.DefaultMemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultMemoryRecallServiceTest {

    private final MemoryMapper memoryMapper = mock(MemoryMapper.class);
    private final DefaultMemoryRecallService service = new DefaultMemoryRecallService(memoryMapper);

    @Test
    void recallReturnsActiveMemoriesAsDtos() {
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(memory("LONG_TERM", "Ada likes concise answers.")));

        List<MemoryDTO> result = service.recall(1L, 20001L, 10001L, 50001L, "Ada", 5);

        assertThat(result).containsExactly(new MemoryDTO("LONG_TERM", "Ada likes concise answers."));
    }

    private MemoryEntity memory(String type, String content) {
        MemoryEntity entity = new MemoryEntity();
        entity.setMemoryType(type);
        entity.setContent(content);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
