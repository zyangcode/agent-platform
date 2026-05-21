package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.identity.api.AuthService;
import com.ls.agent.core.identity.command.LoginCommand;
import com.ls.agent.core.identity.command.RegisterCommand;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.identity.dto.LoginResult;
import com.ls.agent.core.identity.dto.RegisterResult;
import com.ls.agent.web.dto.LoginRequest;
import com.ls.agent.web.dto.LoginResponse;
import com.ls.agent.web.dto.RegisterRequest;
import com.ls.agent.web.security.CurrentUser;
import com.ls.agent.web.security.JwtTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证授权控制器。
 * 处理用户的注册、登录以及获取当前用户信息。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 身份认证与权限服务 */
    private final AuthService authService;
    /** JWT 令牌服务，用于生成和验证 Token */
    private final JwtTokenService jwtTokenService;

    /**
     * 构造函数注入依赖。
     */
    public AuthController(AuthService authService, JwtTokenService jwtTokenService) {
        // 初始化服务实例
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 处理用户注册请求。
     *
     * @param request 注册请求 DTO，包含用户名、密码和显示名称
     * @return 包含注册成功信息的通用响应体
     */
    @PostMapping("/register")
    public ApiResponse<RegisterResult> register(@Valid @RequestBody RegisterRequest request) {
        // 1. 将 Web 层的 Request 转换为 Core 层的 Command 命令对象
        // 2. 调用业务层执行注册逻辑
        RegisterResult result = authService.register(new RegisterCommand(
                request.username(),     // 获取用户名
                request.password(),     // 获取原始密码
                request.displayName()   // 获取昵称
        ));
        // 3. 返回成功响应
        return ApiResponse.success(result);
    }

    /**
     * 处理用户登录请求。
     *
     * @param request 登录请求 DTO，包含用户名和密码
     * @return 包含 JWT Token 和用户信息的通用响应体
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 1. 调用业务层进行身份验证（用户名密码校验）
        LoginResult result = authService.login(new LoginCommand(request.username(), request.password()));
        // 2. 验证通过后，为该用户生成 JWT Token
        String token = jwtTokenService.generate(result.user());
        // 3. 构造登录响应，包含 Token、类型（Bearer）、过期时间和用户信息
        return ApiResponse.success(new LoginResponse(
                token, 
                "Bearer", 
                jwtTokenService.expiresInSeconds(), 
                result.user()
        ));
    }

    /**
     * 获取当前登录用户的信息。
     *
     * @param currentUser 当前用户上下文（由 ArgumentResolver 自动解析）
     * @return 包含用户详细信息的通用响应体
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserDTO> me(CurrentUser currentUser) {
        // 1. 根据当前登录用户的 ID 查询最新信息
        CurrentUserDTO result = authService.getCurrentUser(currentUser.userId());
        // 2. 返回用户信息
        return ApiResponse.success(result);
    }
}
