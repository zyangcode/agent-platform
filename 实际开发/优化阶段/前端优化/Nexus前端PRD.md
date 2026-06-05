# Nexus 前端 PRD（产品需求文档）

> 版本：v1.0 | 日期：2026-05-31 | 后端 API 已就绪，前端需重设计

---

## 1. 产品概述

### 1.1 产品定位

**Nexus** — 面向企业的可观测 AI Agent 工作台。多模型、永续记忆、自研 Agent 编排引擎、全链路治理。

### 1.2 核心用户

| 角色 | 描述 | 使用场景 |
|------|------|---------|
| **User（用户）** | 所有使用者 | 对话、创建 Agent Profile、上传 Skill、管理知识库和记忆、创建 API Key、查看 Trace/Token 用量 |
| **Admin（管理员）** | IT 管理员 | 以上所有权限 + 模型供应商管理、全局 Skill 上架/下架、安全策略、Token 配额、用户管理、告警配置 |

### 1.3 界面结构

一个工作台，按角色展示不同入口：

| 角色 | 看到的界面 |
|------|-----------|
| **User** | 登录 → 对话页（首页） → 侧边栏：Profile、Skill、知识库、记忆、Trace、Token、API Key、设置 |
| **Admin** | 同上 + 侧边栏底部多一个"管理后台"入口 → 模型、用户、安全策略、配额、告警 |

---

## 2. 信息架构（IA）

所有角色共享同一套主工作台，Admin 侧边栏底部多一个"管理后台"入口。

```
Nexus
│
├── 登录/注册页
│
├── 💬 对话页（默认首页，所有角色都能用）
│   ├── 左侧：对话历史列表
│   ├── 中间：聊天区 + 消息流
│   └── 右侧：运行时面板（Trace / Team / 工具调用）
│
├── 👤 工作台（User / Developer / Admin 共用）
│   ├── Agent Profile 管理
│   ├── Skill 市场
│   ├── MCP 工具
│   ├── 知识库（RAG）
│   ├── 记忆管理
│   ├── Token 用量
│   ├── Trace 链路
│   ├── Application + API Key
│   └── 个人设置
│
└── 🛡 Admin 后台（仅 Admin 角色可见此入口）
    ├── 模型供应商管理
    ├── 模型配置管理
    ├── MCP Server 管理
    ├── 用户管理
    ├── 全局安全策略
    ├── Token 配额策略
    └── 告警配置
```

**角色权限矩阵**：

| 功能 | User | Admin |
|------|:--:|:--:|
| 对话（Agent / Team / Direct） | ✅ | ✅ |
| 创建/管理 Agent Profile | ✅ 自己的 | ✅ 全部 |
| 上传/管理 Skill | ✅ 自己的 | ✅ 全部 + 上下架 |
| 知识库 + 记忆（使用 + 管理） | ✅ 自己的 | ✅ 全部 |
| Token 用量 | ✅ 自己的 | ✅ 全局 |
| Trace 链路 | ✅ 自己的 | ✅ 全局 |
| Application + API Key | ✅ 自己的 | ✅ 全部 |
| MCP 工具使用 | ✅ | ✅ |
| 个人设置 | ✅ | ✅ |
| MCP Server 管理 | ❌ | ✅ |
| 模型供应商/配置 | ❌ | ✅ |
| 用户管理 | ❌ | ✅ |
| 安全/配额/告警策略 | ❌ | ✅ |

---

## 3. 页面详细需求

### 3.1 对话页（最高优先级）

**路由**: `/chat`

**布局**: 三栏

```
┌──────────┬───────────────────┬──────────────┐
│ 对话历史  │     聊天区         │  运行时面板    │
│ (260px)  │    (自适应)        │  (360px)     │
│          │                   │              │
│ 搜索     │  [消息流]          │  Trace 时间线  │
│ 新建对话  │  - 用户消息        │  工具调用记录  │
│ ─────    │  - 助手回复        │  Token 消耗   │
│ 历史1    │  - 工具调用        │  Team 进度    │
│ 历史2    │                   │              │
│ ...      │  [输入框]          │              │
│          │  [模式选择]        │              │
│          │  [Profile选择]     │              │
└──────────┴───────────────────┴──────────────┘
```

**顶部工具栏**（输入框上方）:
- Agent Mode 选择：Agent / Team / Direct Model
- Profile 下拉
- Application 下拉
- 模型配置下拉（Direct Model 模式）
- 可用 Skill/MCP 勾选

**消息流**:
- 用户消息：右对齐气泡
- 助手回复：左对齐，Markdown 渲染，代码高亮
- 工具调用：可折叠卡片，显示 toolName + 参数 + 结果
- 思考过程（thinking）：灰色小字，默认折叠
- 流式输出：token 级打字机效果
- 错误消息：红色边框提示

**运行时面板**（右侧，默认折叠）:
- Trace Span 时间线（实时更新）
- Team 模式：Planner 计划 / Executor 进度 / Reviewer 结果
- Token 消耗实时计数

### 3.2 Agent Profile 管理

**路由**: `/profiles`

**列表页**:
- 卡片网格布局
- 每张卡片：名称、类型(GENERAL/DOMAIN)、状态、executionMode(BASIC/TEAM)、创建时间
- 筛选：按类型、状态
- 新建按钮（醒目主色按钮）

**创建/编辑页**:
- 表单布局：左侧表单，右侧实时预览
- 基本信息：名称、描述、类型（GENERAL/DOMAIN）
- Execution Mode：BASIC（单Agent）/ TEAM（Planner→Executor→Reviewer）
- Prompt 编辑：大文本框，Markdown 支持，字数统计
- Skill 绑定：多选列表，搜索+勾选，已选在上
- MCP 工具绑定：同上
- 记忆策略：下拉（DISABLED / READ_ONLY / READ_WRITE / SESSION_ONLY）
- 可见范围：PRIVATE / APPLICATION / PUBLIC
- 保存按钮 + 取消

### 3.3 Skill 市场

**路由**: `/skills`

**Tab 切换**: 配置型 Skill | Jar Skill | 经验型 Skill

**配置型 Skill 列表**:
- 表格：名称、code、描述、状态、绑定数
- 点击行展开详情（参数 schema、示例）
- 状态标签：ENABLED / DISABLED

**Jar Skill 上传**:
- 拖拽上传区
- 上传后显示校验结果（通过/失败/警告）
- 版本历史列表

**经验型 Skill 管理**:
- 卡片列表：名称、触发域、关键词、内容预览
- 新建/编辑：Markdown 编辑器 + 触发关键词标签输入

### 3.4 知识库（RAG）

**路由**: `/knowledge`

- 文档列表（表格：文件名、类型、大小、分段数、上传时间）
- 上传：拖拽 PDF/DOCX/MD/TXT，显示解析进度
- 文档详情：分段列表 + 每段文本预览
- 删除确认弹窗

### 3.5 记忆管理

**路由**: `/memories`

- 记忆列表（卡片：类型标签、内容预览、来源对话、更新时间）
- 搜索框（关键词搜索）
- 删除按钮（软删除，状态改为 INACTIVE）
- 类型筛选：SUMMARY / PREFERENCE / FACT / PROCEDURAL

### 3.6 Token 用量

**路由**: `/token-usage`

- 概览卡片：今日用量 / 本月用量 / 剩余配额
- 调用明细表格：时间、模型、Prompt Tokens、Completion Tokens、总 Tokens、来源应用
- 时间范围筛选
- 简单柱状图（按天汇总）

### 3.7 Trace 链路

**路由**: `/traces`

- Trace 列表（表格：TraceID、时间、状态、耗时、应用、Profile）
- Trace 详情页：
  - 基本信息区（TraceID、状态、耗时、入口）
  - Span 时间线（甘特图式）
  - 点击 Span 展开属性（入参、出参、耗时、token）
  - Team Span 特殊展示（角色标签、taskId、retryIndex）

### 3.8 Application 与 API Key

**路由**: `/applications`

- 应用列表（卡片：名称、创建时间、Key 前缀 `sk-****`）
- 创建应用弹窗
- 查看 API Key（首次展示完整 key，后续只显示前缀）
- 吊销 Key 确认
- 复制 Key 按钮

### 3.9 登录/注册

**路由**: `/login`

- 极简设计：居中卡片
- 用户名 + 密码
- 登录 / 注册 Tab 切换
- 登录后跳转对话页

---

## 4. 全局设计约束

### 4.1 设计风格（待确认）

深色科技风，具体方向二选一后出 UI 稿。

### 4.2 技术栈（不新增依赖）

- React 18 + TypeScript
- Vite
- Tailwind CSS
- shadcn/ui（已有组件库）
- ECharts（图表）
- i18n 中英文切换（已有）

### 4.3 响应式策略

MVP 只做桌面端（≥1280px），移动端暂缓。

### 4.4 组件复用

以下组件必须在全局 Design System 中统一：

- Button（Primary / Secondary / Ghost / Danger）
- Input / Textarea / Select
- Card / Modal / Drawer
- Table（排序、分页、行展开）
- Tag / Badge / Status Dot
- Tabs
- Toast / Notification
- Skeleton Loading
- Empty State

---

## 5. 页面优先级

| 优先级 | 页面 | 原因 |
|--------|------|------|
| P0 | 对话页 | 核心体验，面试 Demo 主展示 |
| P0 | 登录/注册 | 入口 |
| P1 | Agent Profile 管理 | Developer 核心功能 |
| P1 | Application + API Key | 双通道入口必备 |
| P1 | Trace 链路 | 可观测性亮点 |
| P2 | Skill 市场 | 工具生态 |
| P2 | Token 用量 | 治理能力展示 |
| P3 | 知识库（RAG） | 增强功能 |
| P3 | 记忆管理 | 辅助管理 |
| P3 | Admin 端 | 后台管理 |
