# IDEA 本地启动指南

本文说明如何在 IntelliJ IDEA 中启动阶段 1 的 Web 和 Gateway 服务。

## 1. 启动前准备

需要先准备 PostgreSQL：

```text
database: agent_platform
username: agent
password: agent123
port: 15432
```

推荐使用项目专用 Docker PostgreSQL。它映射到本机 `15432` 端口，避免和你电脑上已有的 PostgreSQL 5432 端口冲突：

```powershell
docker compose -f docker-compose.dev.yml up -d
```

确认数据库可用：

```powershell
docker exec agent-platform-dev-postgres pg_isready -U agent -d agent_platform
```

## 2. 已提供的本地配置

项目已新增两个开发配置文件：

```text
agent-platform-gateway/src/main/resources/application-dev.yml
agent-platform-web/src/main/resources/application-dev.yml
```

它们只在启用 `dev` profile 时生效。

默认配置：

```text
Gateway port: 8081
Web port: 8080
Database: jdbc:postgresql://localhost:15432/agent_platform
Database username: agent
Database password: agent123
Internal token: dev-internal-token
JWT secret: dev-only-change-me-dev-only-change-me
```

如果你的本地数据库账号不同，可以用环境变量覆盖：

```text
AGENT_PLATFORM_DB_URL
AGENT_PLATFORM_DB_USERNAME
AGENT_PLATFORM_DB_PASSWORD
AGENT_PLATFORM_INTERNAL_TOKEN
AGENT_PLATFORM_GATEWAY_URL
AGENT_PLATFORM_JWT_SECRET
AGENT_PLATFORM_JWT_EXPIRES_IN_SECONDS
```

## 3. IDEA 启动 Gateway

新建 Spring Boot Run Configuration：

```text
Name: Agent Platform Gateway
Module: agent-platform-gateway
Main class: com.ls.agent.gateway.GatewayApplication
```

Program arguments 填：

```text
--spring.profiles.active=dev
```

启动成功后，Gateway 监听：

```text
http://localhost:8081
```

## 4. IDEA 启动 Web

新建 Spring Boot Run Configuration：

```text
Name: Agent Platform Web
Module: agent-platform-web
Main class: com.ls.agent.web.WebApplication
```

Program arguments 填：

```text
--spring.profiles.active=dev
```

启动成功后，Web 监听：

```text
http://localhost:8080
```

## 5. 启动顺序

先启动：

```text
Agent Platform Gateway
```

再启动：

```text
Agent Platform Web
```

原因是 Web 的 Chat SSE 接口会通过 HTTP 调用 Gateway：

```text
frontend / Apifox / curl
  -> Web :8080
  -> Gateway :8081
  -> Core
```

## 6. 真实模型怎么配

真实模型 API Key 不写在 `application-dev.yml` 里。

原因：

```text
启动配置只负责服务运行。
模型供应商、模型名称、API Key 属于业务数据。
```

启动 Web 和 Gateway 后，通过后台接口创建：

```text
providerType = OPENAI_COMPATIBLE
baseUrl = https://api.deepseek.com/v1
apiKey = 你的 DeepSeek API Key
modelName = deepseek-chat
```

具体测试命令见：

```text
实际开发/阶段1/03-阶段1测试指南.md
```

## 7. 常见问题

如果启动时报数据库连接失败，先检查：

```text
PostgreSQL 是否已启动
数据库名是否是 agent_platform
账号密码是否是 agent / agent123
项目默认连接端口是否是 15432
```

如果 Web 调 Chat SSE 报 Gateway 连接失败，检查：

```text
Gateway 是否已启动
Gateway 是否运行在 8081
Web 的 AGENT_PLATFORM_GATEWAY_URL 是否被改过
```

如果真实模型调用失败，检查：

```text
providerType 是否是 OPENAI_COMPATIBLE
baseUrl 是否以 /v1 结尾
API Key 是否有效
modelName 是否和供应商文档一致
```
