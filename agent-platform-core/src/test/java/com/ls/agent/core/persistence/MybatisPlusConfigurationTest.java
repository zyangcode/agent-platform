package com.ls.agent.core.persistence;

import com.ls.agent.core.profile.mapper.AgentProfileMapper;
import com.ls.agent.core.support.persistence.MybatisPlusConfiguration;
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
        contextRunner.run(context -> assertThat(context).hasSingleBean(AgentProfileMapper.class));
    }
}
