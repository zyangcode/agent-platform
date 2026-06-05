package com.ls.agent.core.support.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@MapperScan({
        "com.ls.agent.core.agent.mapper",
        "com.ls.agent.core.experience.mapper",
        "com.ls.agent.core.identity.mapper",
        "com.ls.agent.core.memory.mapper",
        "com.ls.agent.core.mcp.mapper",
        "com.ls.agent.core.model.mapper",
        "com.ls.agent.core.profile.mapper",
        "com.ls.agent.core.rag.mapper",
        "com.ls.agent.core.skill.mapper",
        "com.ls.agent.core.trace.mapper",
        "com.ls.agent.core.quota.mapper",
        "com.ls.agent.core.security.mapper",
        "com.ls.agent.core.alert.mapper"
})
public class MybatisPlusConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
