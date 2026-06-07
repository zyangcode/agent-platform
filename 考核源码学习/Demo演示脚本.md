# Agent Platform Demo 演示脚本

> 使用环境：Docker Compose 一键启动（`docker compose up -d`）
> 访问地址：http://localhost
> 默认账号：admin / 123456

---

## 场景 1：基础 Agent 对话 + 工具调用（3 分钟）

**目标**：展示 Agent 自动调用工具，ReAct 循环，SSE 流式输出

### 操作步骤

1. **登录** → `admin / 123456`

2. **创建 Application**
   - Applications 页 → 点「Create Application」
   - Name: `Demo App` → 创建
   - 复制弹出 API Key（`sk-` 开头），妥善保存

3. **创建 Model Config**
   - Model Management 页 → 先创建 Provider：
     ```
     Name: DeepSeek
     Provider Type: OPENAI_COMPATIBLE
     Base URL: https://api.deepseek.com/v1
     API Key: sk-你的DeepSeek密钥
     ```
   - 再创建 Model Config：
     ```
     Provider: DeepSeek
     Model Name: deepseek-chat
     Display Name: DeepSeek V3
     ```

4. **创建 Profile**
   - Profiles 页 → 创建：
     ```
     Name: Demo Agent
     Application: Demo App
     Model Config: DeepSeek V3
     Execution Mode: Basic Agent
     补充提示词: 请用中文简洁回答
     ```
   - 保存后 → 在 Tool Bindings 勾选 `weather.current` → Save bindings

5. **Chat 对话**
   - Agent Chat → 选 Demo App → Agent 模式 → Demo Agent Profile
   - 发送：`重庆明天天气怎么样？如果适合打篮球，帮我算一下20人包场3小时，每小时150元每人多少钱`

6. **观察结果**
   - 右侧 Runtime 面板：看到 `thinking → action → observation → message_delta → message → done`
   - 工具调用过程实时可见：先调 weather → 再调 calculator
   - 最终回答包含天气 + 计算结果

---

## 场景 2：MCP 工具自动发现（2 分钟）

**目标**：展示标准 MCP 协议（JSON-RPC 2.0）的工具自动发现和调用

### 操作步骤

1. **创建 MCP Server**
   - Tools → MCP Tab → 填：
     ```
     Name: My Toolbox
     Server Type: STDIO
     Connection Config: {"command": "python C:\\Users\\WYZ\\my-tools.py"}
     ```
   - 点「创建」→ translate 和 ip_lookup 自动出现

2. **绑定到 Profile**
   - Profiles → Demo Agent → Tool Bindings → 勾选 `translate` → Save

3. **Chat 测试**
   - 发送：`翻译成中文: The weather is great today`
   - 模型直接调用 `@mcp:translate`，返回翻译结果

---

## 场景 3：RAG 知识库问答（2 分钟）

**目标**：展示知识文档语义检索 + 引用来源

### 操作步骤

1. **录入知识文档**
   - Tools → 知识库 RAG Tab：
     ```
     标题: 重庆团建场地推荐
     来源链接: https://wiki.internal/venues
     内容:
       重庆南山植物园适合20-50人团建，有草坪和会议室，人均约100元
       重庆铁山坪森林公园适合户外拓展，人均80-150元
       重庆中央公园免费开放，适合大型团队活动
     ```
   - 点「录入文档」

2. **Chat 测试**
   - 发送：`重庆有什么团建场地推荐`
   - 回答底部出现「参考来源」卡片

---

## 场景 4：Trace 全链路追踪（1 分钟）

**目标**：展示请求级全链路 Span 时间线

### 操作步骤

1. Traces 页 → 列表显示所有请求 Trace
2. 点一条进入详情：
   - 看到 Span 时间线：`agent_runtime.run → context.build → model.invoke → tool.execute`
   - 每个 Span 有：耗时、Token 用量、模型名、状态
   - 底部有 Token 用量明细
   - 用户ID、应用ID 清晰标注

---

## 场景 5：Token 用量监控（1 分钟）

**目标**：展示 Token 配额和消耗明细

### 操作步骤

1. Token Usage 页 → 选 Demo App
2. 查看：累计 Token、请求次数、Top 模型排行
3. 下方表格：每次调用的详细 Token 消耗（prompt/completion/estimated）

---

## 场景 6：Swagger API 文档（30 秒）

**目标**：展示自动生成的 API 文档

### 操作步骤

1. 打开 `http://localhost:8080/swagger-ui.html`
2. 展示所有 REST API 自动文档化
3. 可直接在页面上尝试 API 调用

---

## 场景 7：Jar Skill 热加载（2 分钟 - 可选）

**目标**：展示插件上传、校验、热加载

1. Tools → Skills Tab
2. 选 Jar 文件 + 填 Manifest → 上传
3. Profile 绑定 → Chat 调用

---

## 场景 8：Docker Compose 一键部署（30 秒）

```bash
docker compose up -d
# 等待 30 秒
curl http://localhost/api/auth/login
```

---

## 演示 Checklist

- [ ] 基础 Agent 对话 + 工具调用
- [ ] MCP 工具自动发现 + 翻译
- [ ] RAG 知识库问答 + 引用
- [ ] Trace 全链路 + Span 时间线
- [ ] Token 用量明细
- [ ] Swagger API 文档
- [ ] Docker Compose 一键启动
