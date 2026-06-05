package com.ls.agent.core.persistence;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ls.agent.core.profile.mapper.AgentProfileMapper;
import com.ls.agent.core.alert.mapper.AlertEventMapper;
import com.ls.agent.core.quota.mapper.QuotaConfigMapper;
import com.ls.agent.core.quota.mapper.QuotaReservationMapper;
import com.ls.agent.core.quota.mapper.TokenUsageLogMapper;
import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import com.ls.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.ls.agent.core.security.mapper.SecurityEventMapper;
import com.ls.agent.core.support.persistence.MybatisPlusConfiguration;
import com.ls.agent.core.trace.mapper.TraceRootMapper;
import com.ls.agent.core.trace.mapper.TraceSpanMapper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MybatisPlusConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceTransactionManagerAutoConfiguration.class,
                    MybatisPlusAutoConfiguration.class,
                    SqlInitializationAutoConfiguration.class
            ))
            .withUserConfiguration(MybatisPlusConfiguration.class)
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:mock",
                    "spring.sql.init.mode=never"
            );

    @Test
    void registersCoreMappersWhenDataSourceExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentProfileMapper.class);
            assertThat(context).hasSingleBean(TraceRootMapper.class);
            assertThat(context).hasSingleBean(TraceSpanMapper.class);
            assertThat(context).hasSingleBean(QuotaConfigMapper.class);
            assertThat(context).hasSingleBean(QuotaReservationMapper.class);
            assertThat(context).hasSingleBean(TokenUsageLogMapper.class);
            assertThat(context).hasSingleBean(SecurityEventMapper.class);
            assertThat(context).hasSingleBean(AlertEventMapper.class);
            assertThat(context).hasSingleBean(KnowledgeDocumentMapper.class);
            assertThat(context).hasSingleBean(KnowledgeChunkMapper.class);
        });
    }

    @Test
    void registersPaginationInterceptorWhenDataSourceExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor.getInterceptors())
                    .anySatisfy(innerInterceptor ->
                            assertThat(innerInterceptor).isInstanceOf(PaginationInnerInterceptor.class));
        });
    }

    @Test
    void registersOptimisticLockerInterceptorForVersionedEntities() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor.getInterceptors())
                    .anySatisfy(innerInterceptor ->
                            assertThat(innerInterceptor).isInstanceOf(OptimisticLockerInnerInterceptor.class));
        });
    }
}
