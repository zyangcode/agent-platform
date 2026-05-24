package com.ls.agent.core.support.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@MapperScan({
        "com.ls.agent.core.agent.mapper",
        "com.ls.agent.core.identity.mapper",
        "com.ls.agent.core.memory.mapper",
        "com.ls.agent.core.mcp.mapper",
        "com.ls.agent.core.model.mapper",
        "com.ls.agent.core.profile.mapper",
        "com.ls.agent.core.skill.mapper",
        "com.ls.agent.core.trace.mapper",
        "com.ls.agent.core.quota.mapper",
        "com.ls.agent.core.security.mapper",
        "com.ls.agent.core.alert.mapper"
})
public class MybatisPlusConfiguration {
}
