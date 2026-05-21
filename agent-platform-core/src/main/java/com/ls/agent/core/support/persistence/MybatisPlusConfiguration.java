package com.ls.agent.core.support.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(DataSource.class)
@MapperScan("com.ls.agent.core.**.mapper")
public class MybatisPlusConfiguration {
}
