package com.ls.agent.gateway;

import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.identity.api.AuthService;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.alert.api.AlertEventService;
import com.ls.agent.core.experience.api.ExperienceSkillService;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.mcp.api.McpServerService;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import com.ls.agent.core.mcp.api.McpToolRegistry;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.api.MemoryManagementService;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.application.DefaultMemoryConsolidationService;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.api.SkillRegistry;
import com.ls.agent.core.quota.api.QuotaService;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.security.api.SecurityEventService;
import com.ls.agent.core.security.api.SensitiveDataScanner;
import com.ls.agent.core.skill.api.JarSkillService;
import com.ls.agent.core.trace.api.TraceService;
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
    private ApiKeyService apiKeyService;

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
    private McpServerService mcpServerService;

    @MockBean
    private McpToolExecutor mcpToolExecutor;

    @MockBean
    private McpToolRegistry mcpToolRegistry;

    @MockBean
    private MessageHistoryService messageHistoryService;

    @MockBean
    private ConversationRepository conversationRepository;

    @MockBean
    private MemoryRecallService memoryRecallService;

    @MockBean
    private MemoryManagementService memoryManagementService;

    @MockBean
    private MemoryWriteService memoryWriteService;

    @MockBean
    private DefaultMemoryConsolidationService memoryConsolidationService;

    @MockBean
    private TraceService traceService;

    @MockBean
    private QuotaService quotaService;

    @MockBean
    private TokenUsageService tokenUsageService;

    @MockBean
    private SensitiveDataScanner sensitiveDataScanner;

    @MockBean
    private SecurityEventService securityEventService;

    @MockBean
    private AlertEventService alertEventService;

    @MockBean
    private ExperienceSkillService experienceSkillService;

    @MockBean
    private JarSkillService jarSkillService;

    @Test
    void contextLoads() {
    }
}
