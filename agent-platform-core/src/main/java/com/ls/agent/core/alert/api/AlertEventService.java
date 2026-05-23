package com.ls.agent.core.alert.api;

import com.ls.agent.core.alert.command.RecordAlertEventCommand;

public interface AlertEventService {

    void record(RecordAlertEventCommand command);
}
