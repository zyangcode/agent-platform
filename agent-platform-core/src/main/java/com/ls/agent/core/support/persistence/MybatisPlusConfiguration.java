package com.ls.agent.core.support.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@MapperScan("com.ls.agent.core.**.mapper")
public class MybatisPlusConfiguration {
}
