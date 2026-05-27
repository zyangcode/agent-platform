# MVP Claude Code 设计借鉴记录

> 参考项目目录：`D:\study\蓝山最终考核项目\mvp-claude-code-main`。
> 参考文章：`https://blog.csdn.net/2401_87961121/article/details/161424324?utm_source=miniapp_weixin`。本次网页抓取超时，因此本文主要基于本地项目源码、README 和 `docs/HARNESS_DESIGN.md` 分析。
> 本文只记录可借鉴的设计方法和阶段取舍，不直接照搬其 CLI 形态、LangChain4j 技术栈或本地文件/命令工具能力。

---

## 1. 总体判断

`mvp-claude-code-main` 是一个偏教学和面试展示的 Java 版 Claude Code / Agent Harness 项目。它不是企业级平台，也不是多用户 Web 控制台，而是用较少代码展示 Agent Runtime 的核心结构：

```text
CLI 输入
  -> AgentLoop while 循环
  -> ContextBuilder 组装上下文
  -> Streaming LLM 调用
  -> ToolDispatcher 分发工具
  -> 工具结果写回 history
  -> 下一轮模型调用
```

它对当前 `agent-platform` 的价值不在于直接复用代码，而在于帮助明确：

```text
Agent Runtime 应该自己控制循环、工具边界、上下文压缩、流式输出和子任务隔离。
模型框架只应该作为调用适配层，不应该接管完整 Agent 执行流程。
```

当前项目仍然应该保持既有主线：

```text
Spring Boot + PostgreSQL + Web/Gateway/Core 分层
Spring AI / OpenAI-compatible Provider 封装在 core.model
Agent Runtime、Skill/MCP、Trace/Token、权限和多租户隔离由平台自研控制
```

---

## 2. 两个项目的定位差异

| 维度 | 当前 `agent-platform` | `mvp-claude-code-main` |
|---|---|---|
| 产品形态 | 多用户 Agent 平台 + Web 控制台 + Gateway 治理 | 本地 CLI 编程助手 |
| 技术栈 | Spring Boot、MyBatis-Plus、PostgreSQL、React | Java 17、LangChain4j、Picocli、文件系统 |
| 模块边界 | common/core/web/gateway/frontend 多模块 | 单体 Jar |
| 鉴权 | JWT、API Key、RBAC、Application 隔离 | 本地进程，无多用户鉴权 |
| 工具能力 | Skill/MCP，需授权、审计、配额和安全策略 | File/Bash/Search/Task 等本地工具 |
| 流式输出 | 当前是事件级 SSE，尚非 token 级 | 模型 token 回调级流式 |
| 记忆 | 数据库持久化，面向 tenant/user/application | Markdown 文件记忆 |
| 子 Agent | 阶段 4 规划 | 已有 Explore/Plan/Verification/General |
| 安全边界 | 平台级权限、Gateway 治理链、敏感数据扫描 | 本地确认和工具裁剪 |

结论：

```text
它是 Agent Harness 参考，不是平台架构参考。
能借鉴 Runtime 设计，不应替换当前工程架构。
```

---

## 3. 最值得借鉴的设计

### 3.1 Token 级流式输出

项目中的 `AIService.streamingChat()` 使用 `StreamingChatLanguageModel`，通过 token 回调实时输出，再用 `CompletableFuture<AiMessage>` 等待完整响应：

```text
onNext(token)      -> 实时输出 token
onComplete(result) -> 拿到完整 AiMessage，包括文本和工具调用
onError(error)     -> 标记 future 异常
```

这正好对应当前项目的一个已知差距：

```text
当前 Chat 是事件级 SSE：thinking -> message -> done
message 通常是完整回答，不是模型 token 逐段输出
```

可借鉴方向：

```text
core.model.provider 增加 streaming invoke 能力
OpenAiCompatibleProvider 支持 stream=true
Gateway SSE 将 token delta 转成 message/delta 事件
AgentRuntime 等完整模型响应结束后再判断是否有工具调用
Token usage 在 done/final 事件后结算
```

阶段判断：

```text
阶段 3 当前不强行改。
若用户体验优先，可以先做前端假打字效果。
真正 token 级流式输出应单独作为阶段 3 收口增强或阶段 4 前置重构。
```

### 3.2 ToolDispatcher / ToolRegistry

该项目没有依赖注解反射自动调用工具，而是用显式 Map 分发：

```text
Map<String, BaseTool>
register(tool)
unregister(name)
without(names...)
only(names...)
execute(toolRequest)
```

这个设计对当前项目很有价值。当前项目 Skill 和 MCP 是两条执行链：

```text
SkillRegistry / SkillExecutor
McpToolRegistry / McpToolExecutor
```

后续可以在 `core.agent.tool` 或 `core.tool` 内部提供统一运行时工具视图：

```text
AgentTool {
  name
  description
  parameterSchema
  sourceType: SKILL | MCP
  riskLevel
  enabled
  invoker
}
```

收益：

```text
Agent Runtime 不关心工具来自 Skill 还是 MCP
Profile 工具绑定、Chat 工具展示、Trace 工具调用记录可以复用同一套描述
阶段 4 Executor 只拿授权后的工具列表
高危工具确认、工具审计、工具白名单可以统一做
```

阶段判断：

```text
阶段 3 当前已经能展示和绑定 Skill/MCP，不必马上重构。
阶段 4 Team 前建议吸收，作为 Executor 工具权限边界。
```

### 3.3 子 Agent 工具隔离

项目的 `SubagentRunner` 按类型裁剪工具能力：

```text
EXPLORE       只读探索
PLAN          规划方案，白名单只保留 file/search
VERIFICATION  验证审查，可跑只读诊断
GENERAL       通用执行，但移除 task 防递归
```

关键点不是这些名字，而是两层安全边界：

```text
第一层：工具列表物理裁剪，拿不到工具就无法调用
第二层：Prompt 约束，告诉模型不要越界
```

这对当前阶段 4 的 Planner/Executor/Reviewer 非常适合：

```text
Planner 只规划，不调工具
Executor 只执行被授权工具
Reviewer 只审查，不做写操作
```

建议阶段 4 采用更贴合当前项目的映射：

| 阶段 4 角色 | 工具权限 |
|---|---|
| Planner | 无工具或只读查询工具 |
| Executor | Profile/User 授权后的 Skill/MCP |
| Reviewer | 只读 Trace/上下文/结果，不调用高危工具 |

不要照搬 File/Bash 权限模型，因为当前项目是 Web 平台，不是本地编程助手。

### 3.4 上下文压缩

该项目的 `CompactService` 分三层：

```text
microCompact：每轮裁剪过长工具输出，零模型成本
autoCompact：超过 token 阈值时调用模型总结旧上下文
manualCompact：用户手动触发压缩
```

当前项目已有 `core.context` 定位，后续可以吸收为：

```text
ContextWindowManager
ContextCompressor
ToolOutputTrimmer
ConversationSummarizer
```

适用时机：

```text
历史会话列表完成后
真实工具调用变多后
Team 多轮执行导致上下文明显膨胀后
```

不建议现在做复杂压缩，因为阶段 3 主要目标还是前端 MVP 稳定可演示。

### 3.5 工具执行确认

项目提供 `ToolExecutionConfirmation`，高危工具执行前让用户确认，并把拒绝结果写回 history，让模型调整策略。

当前项目可借鉴为：

```text
riskLevel = LOW | MEDIUM | HIGH
HIGH 工具执行前需要用户确认
确认/拒绝结果写入 Trace Span 和工具调用记录
拒绝时返回 ToolResult.denied，让模型知道工具未执行
```

这对未来 Jar Skill、文件操作、外部系统写操作、Git/部署类工具很重要。

---

## 4. 不建议直接吸收的内容

### 4.1 不引入 LangChain4j

当前项目已经确定：

```text
Spring Boot 3.4.x
Spring AI / OpenAI-compatible Provider 封装在 core.model
自研 Agent Runtime
```

如果再引入 LangChain4j，会让模型调用层出现两套抽象，增加调试成本。

建议：

```text
只借鉴 streaming handler / CompletableFuture 桥接思想
不引入 LangChain4j 依赖
```

### 4.2 不照搬 FileTool / BashTool

该项目面向本地 CLI，天然可以读写文件、执行命令。当前平台是多用户系统，直接开放这类能力风险很高。

当前项目若后续支持类似能力，必须具备：

```text
租户隔离
工作目录沙箱
权限审批
高危操作确认
Trace 审计
超时和输出裁剪
管理员开关
```

因此 File/Bash 类能力只能作为未来高危 Skill 示例，不应作为普通 MVP 工具。

### 4.3 不照搬文件型 Memory

项目使用 `~/.agent/memory` 和 Markdown 文件管理记忆，适合本地助手。

当前平台已经有：

```text
tenant_id
user_id
application_id
conversation_id
memory 表归属
PostgreSQL 持久化
```

因此记忆系统仍应走数据库，不改成文件系统。

### 4.4 不照搬 CLI 交互模式

Picocli、`/compact`、`/memory`、`/quit` 都是 CLI 产品能力，不适合直接进入 Web 控制台。

可转化为：

```text
Chat 页面的“压缩上下文”按钮
Memory 页面的记忆查看/编辑
Trace 页面的工具调用详情
```

---

## 5. 对当前阶段的影响

当前项目处于阶段 3 前端控制台 MVP 收口。此参考项目不应该改变阶段 3 的主线。

阶段 3 当前优先级仍然是：

```text
1. 手工验收登录、Application、Profile、Chat、Trace、Token Usage
2. 修 Demo blocker
3. 完善模型配置、Profile 保存、应用切换、工具绑定等已实现闭环
4. 记录非阻塞项
```

本参考项目只形成后续路线依据：

| 借鉴点 | 建议阶段 |
|---|---|
| token 级真实流式输出 | 阶段 3 增强或阶段 4 前置 |
| 统一 ToolRegistry / AgentTool | 阶段 4 前 |
| Planner/Executor/Reviewer 工具隔离 | 阶段 4 |
| 上下文压缩 | 阶段 4 或长对话增强 |
| 高危工具确认 | Jar Skill / 高危 Skill 前 |

---

## 6. 推荐落地顺序

建议按以下顺序吸收，不要一次性重构：

```text
1. 先完成阶段 3 手工验收和问题台账收口。
2. 若继续打磨 Chat 体验，先做前端假打字效果或后端 message 分片，作为低风险体验增强。
3. 单独设计真正 token 级 streaming provider，不和 Team 同时改。
4. 阶段 4 开始前，抽象 AgentTool 统一 Skill/MCP 运行时工具视图。
5. 阶段 4 实现 Team 时，引入 Planner/Executor/Reviewer 工具权限裁剪。
6. 工具调用和 Team 多轮执行稳定后，再补上下文压缩。
7. Jar Skill 或高危工具上线前，再补工具确认和风险等级。
```

---

## 7. 最终取舍

应该吸收：

```text
Agent Runtime 自主控制循环
token 级 streaming 的回调桥接思路
ToolDispatcher / ToolRegistry 的运行时工具视图
子 Agent 工具物理隔离
上下文压缩的三层策略
高危工具确认机制
```

不应该吸收：

```text
LangChain4j 技术栈
Picocli CLI 产品形态
本地 File/Bash 工具裸能力
Markdown 文件型 Memory
单体 Jar 工程结构
```

一句话结论：

```text
MVP Claude Code 是 Agent Harness 参考，不是平台架构参考。
当前项目保留 Web/Gateway/Core/PostgreSQL 主架构，只在 Runtime、Tool、Streaming、Team 设计上吸收它的成熟思路。
```
