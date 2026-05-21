# Spring Security 与 JWT 学习笔记

本文整理 Spring Security + JWT 的核心逻辑，并结合几篇参考文章提炼适合本项目阶段 1 的落地方案。

参考文章主要覆盖了：

- Spring Security 基础、默认登录页、过滤器链。
- 数据库用户登录、`UserDetailsService`、`PasswordEncoder`。
- 获取当前登录用户、`SecurityContextHolder`。
- 角色权限、资源权限、RBAC。
- 前后端分离项目中使用 JWT 做无状态认证。

本项目不能照搬教程里的所有内容。阶段 1 目标是先跑通：

```text
注册 -> 登录 -> 生成 JWT -> 携带 JWT 访问接口 -> 识别当前用户 -> 基础角色控制
```

## 1. Spring Security 到底负责什么

Spring Security 不是用户系统，也不是 JWT 工具。

它负责的是：

```text
拦截 HTTP 请求
判断这个请求是否需要登录
判断当前请求是谁发起的
判断当前用户有没有权限访问
决定放行、返回 401、返回 403
```

它工作的核心位置在 Controller 之前：

```text
frontend
  -> HTTP 请求
  -> Spring Security Filter Chain
  -> Controller
  -> Service
  -> Core
```

## 2. JWT 到底负责什么

JWT 是一种令牌格式。

登录成功后，后端生成 JWT 返回给前端。前端之后每次请求都带上：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.xxx
```

JWT 负责携带必要身份信息，例如：

```json
{
  "sub": "10001",
  "username": "zhangsan",
  "tenantId": "20001",
  "roles": ["USER"],
  "iat": 1710000000,
  "exp": 1710086400
}
```

注意：

```text
JWT 不是加密，只是签名。
不要放密码、API Key 明文、手机号、身份证等敏感信息。
```

## 3. Spring Security 与 JWT 的关系

JWT 本身不会拦截请求。

Spring Security 本身也不天然知道怎么解析你的 JWT。

所以中间需要一个桥：

```text
JwtAuthenticationFilter
```

它负责：

```text
从 HTTP Header 取 JWT
校验 JWT
解析用户身份
构造 Authentication
放入 SecurityContextHolder
```

这样 Spring Security 才知道：

```text
当前请求已经登录
当前用户是谁
当前用户有哪些角色
```

## 4. 两种认证模式：Session 与 JWT

参考文章里讲了 Spring Security 默认登录页和 `JSESSIONID`。这是传统 Session 模式。

### Session 模式

```text
用户登录
  -> 服务端创建 Session
  -> 浏览器保存 JSESSIONID Cookie
  -> 后续请求靠 JSESSIONID 找回登录状态
```

特点：

- 服务端保存登录状态。
- 适合传统后端页面项目。
- Spring Security 默认容易走这套。

### JWT 模式

```text
用户登录
  -> 服务端生成 JWT
  -> 前端保存 JWT
  -> 后续请求 Authorization: Bearer token
  -> 服务端每次解析 JWT
```

特点：

- 服务端不保存 Session。
- 适合前后端分离。
- 每次请求都靠 token 恢复登录态。

本项目阶段 1 使用 JWT 模式。

所以要配置：

```java
session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

## 5. 认证与授权

这两个词要分清楚。

### 认证 Authentication

回答：

```text
你是谁？
```

例如：

- 用户名密码登录。
- JWT 是否有效。
- 当前请求能不能解析出用户。

### 授权 Authorization

回答：

```text
你能做什么？
```

例如：

- ADMIN 才能访问 `/api/admin/**`。
- 当前用户只能修改自己的 Profile。
- 当前用户只能使用自己有权限的 Application。

阶段 1 的建议：

```text
Spring Security 做粗粒度角色控制。
Core 应用层做业务资源权限兜底。
```

## 6. 参考文章里值得借鉴的点

### 6.1 过滤器链是 Spring Security 的核心

文章里反复提到 Filter Chain，这是必须理解的。

请求不会直接进 Controller，而是先过一组过滤器：

```text
Security Filter Chain
  -> JWT Filter
  -> 认证状态判断
  -> 权限判断
  -> Controller
```

阶段 1 要把自定义 `JwtAuthenticationFilter` 加到过滤器链里。

### 6.2 `SecurityContextHolder` 保存当前用户

JWT 解析成功后，要构造 `Authentication`，然后放入：

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
```

之后 Controller 可以用：

```java
@AuthenticationPrincipal CurrentUser currentUser
```

拿到当前登录用户。

### 6.3 数据库登录不要用默认用户

Spring Security 默认会生成一个 `user` 用户和随机密码，这只适合入门测试。

本项目必须走数据库：

```text
users 表
  -> UserMapper
  -> IdentityAuthService
  -> PasswordEncoder.matches
```

### 6.4 密码必须加密存储

参考文章里提到了 `BCryptPasswordEncoder`。

阶段 1 使用：

```java
new BCryptPasswordEncoder()
```

注册时：

```text
明文密码 -> BCrypt 加密 -> 入库
```

登录时：

```text
明文密码 + 数据库密文 -> PasswordEncoder.matches -> 判断是否正确
```

### 6.5 前后端分离要返回 JSON 错误

不要跳转到默认登录页。

未登录返回：

```http
401 Unauthorized
```

无权限返回：

```http
403 Forbidden
```

所以需要：

```text
RestAuthenticationEntryPoint
RestAccessDeniedHandler
```

### 6.6 RBAC 不要阶段 1 做太重

参考文章里有角色表、权限表、资源权限等内容。

这些可以借鉴思想，但阶段 1 不建议完整照搬。

阶段 1 只需要：

```text
ADMIN
USER
```

复杂的：

```text
role_permission
permission_resource
动态 URL 权限
按钮权限
菜单权限
```

先不做，避免提前把项目复杂度拉满。

## 7. 本项目推荐边界

本项目是多模块结构：

```text
agent-platform-common
agent-platform-core
agent-platform-web
agent-platform-gateway
```

Spring Security + JWT 主要放在 `web` 模块。

### web 模块负责

```text
HTTP 安全配置
JWT 生成与解析
登录接口
注册接口
当前用户接口
401 / 403 JSON 返回
```

### core.identity 负责

```text
用户注册
密码加密保存
用户名密码校验
查询用户基础信息
用户状态判断
```

### common 不负责

```text
不放业务用户对象
不放 JWT 解析逻辑
不放 Spring Security 配置
```

### gateway 阶段 1 不负责 web 登录

Gateway 主要是 AI 调用入口的治理链。

普通用户登录态先在 `web` 处理。

## 8. 完整登录流程

```text
frontend
  -> POST /api/auth/login
  -> AuthController
  -> AuthAppService
  -> core.identity.IdentityAuthService
  -> UserMapper 查询 users 表
  -> PasswordEncoder.matches 校验密码
  -> JwtTokenService.generateToken
  -> 返回 accessToken
```

返回示例：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.xxx.xxx",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "userId": 10001,
    "username": "zhangsan",
    "role": "USER"
  }
}
```

## 9. 完整访问接口流程

```text
frontend
  -> GET /api/profiles
  -> Header: Authorization: Bearer token
  -> Spring Security Filter Chain
  -> JwtAuthenticationFilter
  -> JwtTokenService.validateToken
  -> JwtTokenService.parseToken
  -> CurrentUser
  -> UsernamePasswordAuthenticationToken
  -> SecurityContextHolder
  -> Controller
  -> Service
  -> Core
```

如果 token 合法：

```text
放行
```

如果没有 token：

```text
401
```

如果 token 合法但权限不足：

```text
403
```

## 10. 阶段 1 应该写哪些类

### web.security

```text
SecurityConfig
JwtAuthenticationFilter
JwtTokenService
JwtProperties
JwtClaims
CurrentUser
RestAuthenticationEntryPoint
RestAccessDeniedHandler
```

### web.auth

```text
AuthController
AuthAppService
LoginRequest
LoginResponse
RegisterRequest
RegisterResponse
CurrentUserResponse
```

### core.identity.api

```text
IdentityAuthService
RegisterCommand
LoginCommand
LoginUserResult
UserDetailResult
```

### core.identity.application

```text
IdentityAuthServiceImpl
```

### core.identity.entity / mapper

```text
UserEntity
UserMapper
```

## 11. 核心类职责

| 类 | 所在模块 | 职责 |
| --- | --- | --- |
| `SecurityConfig` | web | 配置 Spring Security 过滤器链 |
| `JwtAuthenticationFilter` | web | 每个请求解析 JWT 并恢复登录态 |
| `JwtTokenService` | web | 生成、校验、解析 JWT |
| `JwtProperties` | web | JWT 密钥、过期时间、issuer 等配置 |
| `CurrentUser` | web | 当前登录用户对象 |
| `RestAuthenticationEntryPoint` | web | 未登录返回 401 JSON |
| `RestAccessDeniedHandler` | web | 无权限返回 403 JSON |
| `AuthController` | web | 暴露注册、登录、当前用户接口 |
| `AuthAppService` | web | 编排 web 登录流程，连接 core 和 JWT |
| `IdentityAuthService` | core | core 暴露的用户认证能力 |
| `UserMapper` | core | 查询用户表 |

## 12. 最小可运行闭环

阶段 1 先不要追求完整权限系统。

先跑通：

```text
1. POST /api/auth/register 注册用户
2. POST /api/auth/login 登录拿 token
3. GET /api/auth/me 带 token 返回当前用户
4. 不带 token 访问 /api/auth/me 返回 401
5. USER 访问 /api/admin/** 返回 403
6. ADMIN 访问 /api/admin/** 放行
```

这个闭环跑通后，再接 Profile、Application、Chat。

## 13. 推荐配置方向

示意代码：

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/chat/stream-test").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

## 14. 不建议阶段 1 做的事

```text
不做 Spring Security 默认登录页
不做 Session 登录
不做 rememberMe
不做 OAuth2
不做动态 URL 权限
不做完整 RBAC 权限后台
不把 JWT 逻辑放到 core
不让 Filter 直接查 Mapper
```

## 15. 阶段 1 最重要的原则

```text
Spring Security 只守 HTTP 入口。
JWT 只表达登录身份。
core.identity 只处理用户认证业务。
业务资源权限必须在 core 应用层再校验。
```

这可以避免两个问题：

1. web 层安全配置变得过重。
2. 绕过 web 的内部调用缺少业务权限兜底。

## 16. 参考文章

- https://blog.csdn.net/m0_74065705/article/details/142468012
- https://blog.csdn.net/weixin_49822012/article/details/159892231
- https://blog.csdn.net/weixin_49822012/article/details/159979627
- https://blog.csdn.net/weixin_49822012/article/details/159966541

