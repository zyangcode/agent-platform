package com.ls.agent.core.context.api;

import com.ls.agent.core.context.dto.MicroCompactResult;

public interface MicroCompactService {

    MicroCompactResult compact(String role, String content);
}
