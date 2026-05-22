package com.ls.agent.core.memory;

import com.ls.agent.core.memory.application.DefaultMemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultMemoryWriteServiceTest {

    private final MemoryMapper memoryMapper = mock(MemoryMapper.class);
    private final DefaultMemoryWriteService service = new DefaultMemoryWriteService(memoryMapper);

    @Test
    void recordStoresActiveMemory() {
        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "SUMMARY",
                "User: hello\nAssistant: done",
                90001L
        ));

        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getMemoryType()).isEqualTo("SUMMARY");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getSourceConversationId()).isEqualTo(90001L);
    }
}
