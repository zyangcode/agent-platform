package com.ls.agent.gateway;

import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.identity.api.AuthService;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.api.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class GatewayApplicationTest {

    @MockBean
    private AuthService authService;

    @MockBean
    private ApplicationService applicationService;

    @MockBean
    private ModelConfigService modelConfigService;

    @MockBean
    private ModelInvokeService modelInvokeService;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private SkillRegistry skillRegistry;

    @MockBean
    private McpToolQueryService mcpToolQueryService;

    @MockBean
    private McpToolRegistry mcpToolRegistry;

    @MockBean
    private ConversationMapper conversationMapper;

    @MockBean
    private ConversationMessageMapper conversationMessageMapper;

    @MockBean
    private MemoryMapper memoryMapper;

    @Test
    void contextLoads() {
    }
}
