package com.ls.agent.web.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 自定义 Spring MVC 参数解析器。
 * 允许在 Controller 方法参数中直接通过 {@link CurrentUser} 获取当前登录用户信息。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 判断当前解析器是否支持该参数。
     * 只有当参数类型为 {@link CurrentUser} 时才处理。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    /**
     * 实际解析参数的逻辑。
     * 从 Spring Security 的安全上下文中提取已认证的用户 Principal。
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        // 1. 从当前线程的安全上下文中获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 2. 校验认证信息是否存在，并且 Principal 是否为我们存入的 CurrentUser 对象
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            // 如果未登录或类型不匹配，返回 null
            return null;
        }
        
        // 3. 返回解析出的用户信息对象，供 Controller 使用
        return currentUser;
    }
}
