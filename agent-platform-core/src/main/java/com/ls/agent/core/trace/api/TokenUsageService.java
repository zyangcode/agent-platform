package com.ls.agent.core.trace.api;

import com.ls.agent.core.trace.command.RecordTokenUsageCommand;

public interface TokenUsageService {

    void record(RecordTokenUsageCommand command);
}
