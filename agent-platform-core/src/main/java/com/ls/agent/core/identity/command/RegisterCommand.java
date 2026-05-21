package com.ls.agent.core.identity.command;

public record RegisterCommand(
        String username,
        String password,
        String displayName
) {
}
