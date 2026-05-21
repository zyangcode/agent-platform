package com.ls.agent.core.identity.command;

public record LoginCommand(
        String username,
        String password
) {
}
