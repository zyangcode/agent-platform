# Interview Guide 设计借鉴记录

> 参考项目：`D:\study\蓝山最终考核项目\interview-guide-master`（JavaGuide 开源项目）。本文只记录可借鉴的设计方法和可吸收的工程规则，不照搬业务模型。

---

## 1. 项目概况

Interview Guide 是一个智能 AI 面试官平台（Spring Boot 4.0 + Java 21 + Spring AI + React + PostgreSQL pgvector + Redis）。功能涵盖简历分析、模拟面试（文字+语音）、面试安排、RAG 知识库、多模型管理。与 agent-platform 同为 AI Agent 类项目，但更偏应用层，agent-platform 更偏平台层。

---

## 2. 可直接借鉴的 8 个设计点

### 2.1 错误码分域编号（高优先级）

ErrorCode 分 10 个域，每个域名一个数字段：

```text
通用 1xxx → 简历 2xxx → 面试 3xxx → 存储 4xxx → 导出 5xxx
知识库 6xxx → AI服务 7xxx → 限流 8xxx → 日程 9xxx → 语音 10xxx
```

agent-platform 当前 ErrorCode 只有枚举名和 HTTP 状态码，没有数字编号。建议阶段 2 补：

```text
identity 1xxx → model 2xxx → profile 3xxx → skill 4xxx
mcp 5xxx → agent 6xxx → gateway 7xxx → quota 8xxx → security 9xxx
```

### 2.2 Prompt 模板独立文件管理

Interview Guide 把 Prompt 放在 `resources/prompts/`，用 StringTemplate 格式：

```text
resources/prompts/
  ├── resume/
  ├── interview/
  └── voice-interview/
```

agent-platform 当前 System Prompt 部分硬编码在 `DefaultAgentContextBuilder` 里。建议后续放到 `core/src/main/resources/prompts/`，用文件管理，AgentContextBuilder 按需加载。

### 2.3 禁止清单速查表

Interview Guide 用一张表格汇总所有禁止项：

```text
| 禁止项 | 原因 |
|--------|------|
| throw new RuntimeException(...) | 绕过全局异常处理 |
| 直接返回 Entity 给前端 | 暴露内部结构 |
| @Value 散落在 Service 中 | 配置应集中管理 |
| 事务内调用外部 API（LLM、S3） | 占用 DB 连接 |
| 同类内部调用 @Transactional | AOP 代理不生效 |
| catch (Exception e) {} 静默忽略 | 隐藏错误 |
```

agent-platform 的禁止规则散落在 CLAUDE.md、技术选型文档和各模块文档中。建议在技术选型文档末尾加一张同样的速查表。

### 2.4 @ConfigurationProperties 替代散落 @Value

```java
// 好：集中管理
@ConfigurationProperties("web.gateway")
public record WebGatewayProperties(String internalBaseUrl, String internalToken) {}
```

agent-platform 当前 HttpGatewayClient 用 `@Value`，只有两处，但后续 Gateway filter、quota、trace 的安全/内部配置会增多，应整理到 Properties 类。

### 2.5 可重复注解多维度限流（阶段 2 参考）

```java
@RateLimit(dimension = GLOBAL, count = 10)
@RateLimit(dimension = IP, count = 10)
@RateLimit(dimension = USER, count = 10)
```

AOP + Redis Lua 脚本滑动窗口。agent-platform 阶段 2 Gateway 治理链里的 QuotaFilter 可参考这种注解式多维度限流方案。

### 2.6 结构化日志规范

```java
log.info("Session created: sessionId={}, role={}", id, role);
log.error("Evaluation failed: sessionId={}", id, e);  // 异常作为最后参数
```

禁止 `log.error("Error: {}", e.getMessage())`（丢失堆栈）。agent-platform 当前日志带 trace_id，但格式规范未显式记录。

### 2.7 异步任务失败重试机制

Interview Guide 用 Redis Stream + 最大 3 次重试 + 超过后标记 FAILED。agent-platform 阶段 2 的简历分析不涉及，但 Token 结算的异步补偿（如果预扣成功但结算失败）可参考。

### 2.8 配置安全落盘

运行时 API Key 加密后写入 `~/.interview-guide/` 用户目录，不在数据库存储。agent-platform 模型 API Key 存在 `model_providers.api_key_encrypted`，已用 SecretEncryptor，策略不同但同样安全。

---

## 3. 不建议照搬的内容

| 内容 | 原因 |
|------|------|
| Java 21 + 虚拟线程 | agent-platform 定在 Java 17 |
| Gradle（非 Maven） | 切换成本高 |
| Spring Data JPA + ddl-auto | agent-platform 用 MyBatis-Plus + Flyway，更可控 |
| Redis + Redisson | agent-platform MVP 无 Redis |
| pgvector 向量库 | 阶段 1 不做完整 RAG |
| MapStruct | agent-platform 用 Record + 手动转换，简单够用 |
| Apache Tika / iText | 文档解析和 PDF 导出不在 agent-platform 范围内 |
| WebSocket | 语音面试不在 agent-platform 范围 |

---

## 4. 已吸收到 agent-platform 的对查

| Interview Guide 规则 | agent-platform 现状 |
|------|------|
| 不可变数据载体优先用 Record | ApiResponse/PageResult/CurrentUser/DTO 全部用 Record ✅ |
| 禁止直接返回 Entity 给前端 | Controller→DTO→Entity 三层分离 ✅ |
| 统一响应包装 | `ApiResponse<T>` + `PageResult<T>` ✅ |
| 业务异常禁止 RuntimeException | `BizException(ErrorCode)` ✅ |
| @Transactional 放 Service 层 | 全部在 Application Service ✅ |
| 敏感配置不入代码仓库 | .gitignore 覆盖 application-local.yml ✅ |
| 禁止通配符导入 | ArchUnit 未覆盖此规则（建议后续补） |

---

## 5. 最终结论

Interview Guide 的工程规范精细度高于 agent-platform 当前状态。**最应该立即吸收的**：错误码分域编号 + 禁止清单速查表 + @ConfigurationProperties 替代 @Value + Prompt 文件化管理。
