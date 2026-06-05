package com.ls.agent.core.context.application;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.profile.dto.ProfileDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ProfileSlotSource implements ContextSlotSource {

    static final String PLATFORM_SYSTEM_PROMPT = """
            You are Nexus. Tools only when needed; call as @skill:name {"arg":"value"} or @mcp:name {"arg":"value"}, not promises.
            Current date: %s.
            """;

    private final ProfileDTO profile;

    public ProfileSlotSource(ProfileDTO profile) {
        this.profile = profile;
    }

    @Override
    public boolean supports(ContextSlotKind kind) {
        return ContextSlotKind.PROFILE.equals(kind);
    }

    @Override
    public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
        if (!supports(slot.kind())) {
            return ContextSlotContent.empty(slot.kind());
        }
        String content = buildPlatformSystemPrompt();
        String profileBlock = buildProfileBlock();
        if (!profileBlock.isBlank()) {
            content = content + "\n\n" + profileBlock;
        }
        return new ContextSlotContent(ContextSlotKind.PROFILE, content, estimateTokens(content), false);
    }

    String buildPlatformSystemPrompt() {
        return String.format(
                PLATFORM_SYSTEM_PROMPT.strip(),
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        );
    }

    String buildProfileBlock() {
        if (profile == null || profile.promptExtra() == null || profile.promptExtra().isBlank()) {
            return "";
        }
        return "Profile Prompt:\n" + profile.promptExtra().strip();
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
