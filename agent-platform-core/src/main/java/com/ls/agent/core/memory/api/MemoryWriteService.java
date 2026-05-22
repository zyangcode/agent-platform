package com.ls.agent.core.memory.api;

import com.ls.agent.core.memory.command.RecordMemoryCommand;

public interface MemoryWriteService {

    void record(RecordMemoryCommand command);
}
