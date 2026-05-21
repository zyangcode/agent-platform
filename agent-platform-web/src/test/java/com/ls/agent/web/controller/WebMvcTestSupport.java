package com.ls.agent.web.controller;

import com.ls.agent.web.config.WebMvcConfig;
import com.ls.agent.web.error.GlobalExceptionHandler;
import com.ls.agent.web.security.CurrentUserArgumentResolver;
import com.ls.agent.web.security.JwtAuthenticationFilter;
import com.ls.agent.web.security.JwtTokenService;
import com.ls.agent.web.security.RestAccessDeniedHandler;
import com.ls.agent.web.security.RestAuthenticationEntryPoint;
import com.ls.agent.web.security.SecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        SecurityConfig.class,
        JwtTokenService.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        CurrentUserArgumentResolver.class,
        WebMvcConfig.class,
        GlobalExceptionHandler.class
})
class WebMvcTestSupport {
}
