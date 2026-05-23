package com.ls.agent.core.security.api;

import com.ls.agent.core.security.command.RecordSecurityEventCommand;

public interface SecurityEventService {

    void record(RecordSecurityEventCommand command);
}
