package com.ls.agent.core.profile.command;

import java.util.List;

public record BindMcpToolsCommand(
        Long tenantId,
        Long ownerUserId,
        Long profileId,
        List<Long> mcpToolIds
) {
}
