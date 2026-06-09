# MCP 工具发现与结构化调用优化方案

## 背景

当前项目已经具备 MCP Server 管理、MCP Tool 发现、Profile 绑定、Agent Runtime 工具分发、STDIO/HTTP MCP Client 调用等基础能力。后续优化目标是把 MCP 工具调用链路表达得更标准、更稳定，并兼容不同模型供应商的工具调用能力。

核心结论：

- 数据库只保存 MCP 工具元数据，不保存工具实现。
- 工具发现不建议每次对话都 refresh，应该在新增、手动刷新、后台定时或调用失败提示时触发。
- 模型工具调用推荐采用 OpenAI `tools` / `tool_calls` 结构化模式优先，保留 `@mcp:xxx {...}` 文本协议兜底。

## 端到端链路

### 1. 用户/Admin 注册 MCP Server

平台允许配置两类 MCP Server。

STDIO 类型：

- 配置 `command` / `args`
- 平台后续通过 `StdioMcpClient` 启动子进程
- 通过 `stdin` / `stdout` 按 JSON-RPC 2.0 通信

HTTP 类型：

- 配置 `baseUrl`
- 平台后续通过 `HttpMcpClient` 请求远程 MCP endpoint
- 当前设计采用单端点 `POST` JSON-RPC，兼容 `application/json` 和 `text/event-stream`

### 2. 平台发现 MCP 工具

注册 MCP Server 后，平台调用：

```text
initialize
tools/list
```

MCP Server 返回它支持的工具列表，例如：

```text
weather.current
calculator
search
```

### 3. 平台把工具元数据存进数据库

写入 `mcp_tools` 的是工具描述信息，不是工具实现。

数据库主要保存：

- `name`：工具名
- `description`：工具描述
- `parameter_schema`：参数 JSON Schema
- `status`：工具状态
- `mcp_server_id`：所属 MCP Server

真正执行逻辑仍然在 MCP Server 那边。平台只保存“这个工具存在、怎么调用、参数长什么样”。

### 4. 工具发现不建议每次对话都 refresh

不建议用户每次发消息都先 refresh。

原因：

- 会拖慢对话首 token 时间。
- 远端 MCP Server 抖动会影响聊天主链路。
- `tools/list` 属于管理/同步动作，不应该和每次推理强绑定。

更合理的 refresh 触发方式：

- 新增 MCP Server 时发现一次。
- 用户/Admin 手动点击“刷新工具”时重新 `tools/list`。
- 后台定时 refresh。
- 工具调用失败时提示可能需要刷新。

### 5. 用户在 Profile 里绑定 MCP 工具

用户/Admin 从 `mcp_tools` 中勾选工具。

平台把绑定关系写入：

```text
profile_mcp_tools
```

这一步的意义是授权：不是所有数据库里的 MCP 工具都能被当前 Agent 使用，只有当前 Profile 绑定过的工具才允许进入 Agent Runtime。

### 6. 用户发起对话请求

请求里可以带本次启用的工具 ID，例如：

```text
enabledMcpToolIds
```

平台会筛选本次对话允许使用的工具集合：

```text
当前租户
+ 当前 Profile 已绑定
+ 本次用户勾选
+ MCP Server 状态 ACTIVE
+ MCP Tool 状态 AVAILABLE
```

筛选后的工具才会进入模型调用和 Agent Runtime。

### 7. 平台把工具传给模型

推荐主模式是 OpenAI tool calling。

请求模型时，不是把工具纯文本塞进 system prompt，而是把工具作为和 `messages` 并列的 `tools` 字段传过去：

```json
{
  "messages": [],
  "tools": [],
  "tool_choice": "auto"
}
```

含义：

- `messages` 是对话内容。
- `tools` 是平台给模型的可用工具表。
- `tool_choice=auto` 表示由模型自己判断是否需要调用工具。

### 8. 保留文本工具调用兜底

不是所有 OpenAI 兼容模型都稳定支持 `tools` / `tool_calls`。

推荐策略：

```text
如果模型配置 supportsToolCalling = true：
    优先传 tools 字段，走 tool_calls

如果模型不支持或返回不标准：
    降级使用 @mcp:xxx {...} 文本协议
```

最终设计不是二选一，而是：

```text
tool_calls 优先
@mcp 文本协议兜底
```

形象理解：

```text
function calling = 平台给模型一张正式工具表
文本协议 = 平台在 prompt 里告诉模型“请按这个格式写工具调用命令”
```

当前项目主链路是后者：

```text
ToolsSlotSource
→ 把可用 MCP 工具写进 system prompt
→ 模型按约定输出 @mcp:xxx {...}
→ DefaultAgentRuntimeService 解析 @mcp:xxx {...}
→ Agent Runtime 分发并执行 MCP 工具
```

因此当前实现更像是“在上下文文本中告诉模型工具调用格式”，而不是“通过模型 API 的 `tools` 字段传入正式工具表”。后续优化应该在保留当前文本协议兜底能力的基础上，引入结构化 `tools` / `tool_calls` 主路径。

### 9. 模型决定是否调用工具

如果走结构化 tool calling，模型返回：

```json
{
  "tool_calls": [
    {
      "function": {
        "name": "mcp__weather_current",
        "arguments": "{\"city\":\"重庆\"}"
      }
    }
  ]
}
```

如果走文本兜底，模型返回：

```text
@mcp:weather.current {"city":"重庆"}
```

### 10. Agent Runtime 解析工具调用

Runtime 优先读取：

```text
result.toolCalls()
```

如果没有 `tool_calls`，再尝试解析 assistant message 里的文本调用：

```text
@mcp:xxx {...}
@skill:xxx {...}
```

这样可以同时兼容标准 tool calling 和现有文本协议。

### 11. Agent Runtime 校验工具调用

执行前必须校验：

- 工具是否在本次 available tools 里。
- `sourceType` 是否匹配，例如 `MCP`。
- 参数是不是 JSON object。
- 是否高风险工具。
- 高风险工具是否已经用户确认。
- 是否超过最大工具调用次数。

校验失败时，不应该直接调用 MCP Server，而应该把错误 observation 返回给模型或终止工具执行。

### 12. Agent Runtime 分发工具调用

Runtime 不直接处理 MCP 协议细节。

它把调用交给 `AgentToolDispatcher`。

如果 `sourceType = MCP`：

```text
AgentToolDispatcher
→ McpToolExecutor
→ 查询 mcp_tool
→ 查询所属 mcp_server
→ 根据 server_type 选择客户端
```

### 13. MCP Client 调用 MCP Server

如果 `server_type = STDIO`：

```text
StdioMcpClient
→ 启动子进程
→ 写入 JSON-RPC 请求
→ initialize
→ tools/call
→ 从 stdout 读取 JSON-RPC 响应
```

如果 `server_type = HTTP`：

```text
HttpMcpClient
→ POST 到 baseUrl
→ 发送 JSON-RPC 请求
→ initialize
→ tools/call
→ 读取 JSON 或 SSE 响应
```

### 14. MCP Server 执行工具并返回结果

MCP Server 执行真正的外部能力，例如：

- 查询天气
- 查资料
- 计算表达式
- 调第三方 API
- 访问某个业务系统

然后把结果返回给平台。

### 15. Agent 把工具结果作为 observation 再喂给模型

工具结果不会直接等同于最终回答。

标准流程是：

```text
模型要求调用工具
→ Agent 调 MCP 工具
→ MCP Server 返回结果
→ Agent 把结果作为 tool/observation 消息加入上下文
→ 再次调用模型
```

### 16. 模型基于工具结果生成最终回答

最终回答由模型生成。

例如工具返回：

```json
{
  "city": "重庆",
  "temperatureCelsius": 31,
  "condition": "多云"
}
```

模型最终回答用户：

```text
重庆当前多云，约 31°C，适合短时间外出，但注意防晒和补水。
```

## 一句话总结

用户/Admin 注册 MCP Server 后，平台通过 `initialize` + `tools/list` 发现工具，并把工具元数据存入数据库；用户在 Profile 中绑定允许使用的 MCP 工具。对话时，平台根据租户、Profile、本次勾选和工具状态筛出可用工具，优先以 OpenAI `tools` 结构化字段传给模型，模型返回 `tool_calls`；如果模型不支持，则降级解析 `@mcp:xxx {...}` 文本调用。Agent Runtime 校验工具权限、参数和风险后，根据 `mcp_tool` 找到所属 `mcp_server`，再按 STDIO 或 HTTP 选择 MCP Client 调用 `tools/call`。MCP Server 返回结果后，Agent 把结果作为 observation 再交给模型，最后由模型生成自然语言回答。

## 后续优化建议

### 1. 增加 MCP 工具刷新接口

建议新增：

```text
POST /api/mcp-servers/{id}/refresh-tools
```

职责：

- 调用 MCP Server 的 `tools/list`
- 新增数据库中不存在的工具
- 更新已存在工具的描述和参数 schema
- 对远端已删除工具做禁用或标记处理
- 更新 `last_discovered_at`

### 2. 修正同名工具执行歧义

当前如果只按 `toolName` 查第一个可用工具，在多租户或多 MCP Server 存在同名工具时可能选错。

更稳的方案：

- 优先让 Runtime 执行时携带 `mcpToolId`
- 或者按当前租户、当前 Profile 可用工具范围、tool name 联合查询
- OpenAI tool name 可编码 tool id，例如 `mcp__123__weather_current`

### 3. 增加模型级 tool calling 开关

建议模型配置增加：

```text
supportsToolCalling
```

当为 true 且本次有 tools：

```text
请求体写入 tools + tool_choice=auto
Runtime 优先解析 tool_calls
```

当为 false：

```text
不发送 tools
继续使用 @mcp:xxx {...} 文本协议
```

### 4. 保留文本协议作为兼容层

即使支持结构化 tool calling，也不建议立即删除文本协议。

原因：

- Mock 模型和部分 demo 可能依赖文本协议。
- 部分 OpenAI 兼容供应商对 `tools` 支持不稳定。
- 文本协议可以作为异常降级路径。
