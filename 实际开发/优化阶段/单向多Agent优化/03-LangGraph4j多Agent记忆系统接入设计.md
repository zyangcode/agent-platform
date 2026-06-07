# LangGraph4j 多 Agent 记忆系统接入设计

> 本文补充 `单向多Agent优化` 的 LangGraph4j 多 Agent 方案，说明如何复用 `记忆系统优化/00-单Agent记忆与RAG统一设计文档.md` 中的 Memory / RAG / Context Slot 能力。核心原则：多 Agent 可以读记忆，但不能让每个 Agent 直接写长期记忆；长期写入统一收口到 ReflectNode / Finalize 阶段。

## 1. 结论

现有单 Agent 记忆系统可以作为 LangGraph4j 多 Agent 的底座，接入点不是每个 Agent 自己查库，而是：

```text
LangGraphTeamRuntimeService
  -> TeamRunContextBuilder
  -> core.context 组装 Team 专用 Slot
       -> MemorySlotSource
       -> RagSlotSource
       -> ExperienceSlotSource
  -> LangGraph4j StateGraph
       Planner / Executor / Reviewer / Reflector
  -> ReflectNode 汇总经验
  -> MemoryWriteService 准入/脱敏/去重/冲突处理后写入
```

外部仍然保持 `TEAM` 模式。Memory/RAG 不新增独立多 Agent 存储系统，只复用现有 `core.memory`、`core.rag`、`core.context`。

## 2. 为什么不能让每个 Agent 直接写记忆

多 Agent 流程里会产生大量中间态：

- Planner 的初稿计划。
- Executor 的失败尝试。
- ReAct 中间 observation。
- Reviewer 的质疑和不通过原因。
- Replan 前后的替代路线。

这些内容有调试价值，但不都适合进入长期记忆。如果每个 Agent 都能直接写 `memories`，会出现：

- 低置信推测污染长期记忆。
- 同一任务多轮重试写入重复记忆。
- 失败草稿被后续召回误用。
- Reviewer 的负面判断和 Planner 的未执行计划混在一起。
- 多 Agent 并发写入导致冲突处理复杂化。

因此长期记忆写入必须统一收口：

```text
Agent 节点只产出候选经验 -> ReflectNode 归纳 -> MemoryWriteService 做准入 -> 写入长期记忆
```

## 3. 角色记忆权限矩阵

| 角色 | 读 Memory | 写 Memory | 读 RAG | 写 RAG | 说明 |
|---|---:|---:|---:|---:|---|
| Planner | 可读摘要级/偏好级/项目事实 | 否 | 可读 RAG 摘要 | 否 | 用于任务拆分和依赖编排，不写长期记忆 |
| Executor | 可读任务相关记忆 | 否 | 可读 RAG，可调用工具 | 否 | 执行子任务，产生候选经验 |
| ReActTaskGraph | 可读当前子任务相关记忆 | 否 | 可读授权 RAG/工具结果 | 否 | 只在子图 state 中记录中间观察 |
| Task Evaluator | 可读任务目标和结果 | 否 | 可读必要引用 | 否 | 只评估子任务，不写记忆 |
| Global Reviewer | 可读计划、结果、草稿、引用来源 | 否 | 可读 RAG citation | 否 | 只审查最终质量，不改记忆 |
| Reflector | 可读完整执行轨迹 | 可写 reflection/tool_failure/summary | 可读 | 否 | 唯一生成长期写入候选的 Agent |
| Orchestrator / FinalAnswerNode | 可读必要上下文 | 控制写入时机 | 可读 | 否 | 收口最终答案和写入策略 |

约束：

- Planner、Executor、Reviewer 不直接调用 `MemoryWriteService`。
- Reflector 也不直接绕过准入写表，必须走 `MemoryWriteService`。
- RAG 文档入库仍然只走管理接口 `Web -> Core`，多 Agent 运行时不自动写 RAG。

## 4. Team 专用 Context Slot Schema

单 Agent 的 Slot 不能原样给所有角色使用。多 Agent 需要按角色裁剪上下文，避免上下文膨胀和权限过宽。

建议新增 Team Schema：

```text
TEAM_PLANNER_CONTEXT
  PROFILE_SYSTEM
  CONVERSATION_HISTORY_SUMMARY
  TASK_MEMORY: preference / fact / summary, topK 小
  RAG_RECALL: 只给摘要和标题
  CONSTRAINT

TEAM_EXECUTOR_CONTEXT
  PROFILE_SYSTEM
  CURRENT_TASK
  TASK_MEMORY: 与当前 task 相关
  RAG_RECALL: 与当前 task 相关 chunk
  TOOL_CONTEXT
  EXPERIENCE_SKILL

TEAM_REVIEWER_CONTEXT
  ORIGINAL_USER_GOAL
  TASK_PLAN
  EXECUTION_RESULTS
  RAG_CITATIONS
  REVIEW_CRITERIA

TEAM_REFLECTION_CONTEXT
  ORIGINAL_USER_GOAL
  PLAN_HISTORY
  EXECUTION_RESULTS
  TOOL_FAILURES
  REVIEW_RESULT
  FINAL_ANSWER
  TRACE_SUMMARY
```

对应实现建议：

```java
public enum ContextSchemaKind {
    DEFAULT_AGENT,
    REACT_AGENT,
    TEAM_PLANNER,
    TEAM_EXECUTOR,
    TEAM_REVIEWER,
    TEAM_REFLECTION
}
```

`TeamRunContextBuilder` 根据节点角色调用 `ContextSchemaAssembler`，而不是节点自己拼 Prompt。

## 5. LangGraphTeamState 中的记忆快照

LangGraph4j state 应保存可序列化快照，不保存数据库 Entity 或 Spring Bean。

建议字段：

```java
public class LangGraphTeamState extends AgentState {
    String traceId;
    Long tenantId;
    Long applicationId;
    Long userId;
    Long profileId;

    String originalUserMessage;
    TeamContextSnapshot contextSnapshot;

    List<MemorySnippet> plannerMemories;
    Map<String, List<MemorySnippet>> taskMemories;
    List<RagSnippet> plannerRagSnippets;
    Map<String, List<RagSnippet>> taskRagSnippets;

    TaskPlanDTO plan;
    Map<String, ExecutionResultDTO> completedResults;
    Map<String, TaskEvaluationDTO> taskEvaluations;
    ReviewResultDTO globalReview;

    List<ReflectionCandidate> reflectionCandidates;
    List<MemoryWriteCandidate> memoryWriteCandidates;
    String finalAnswer;
}
```

快照对象只保留必要信息：

```text
MemorySnippet:
  memoryId / category / summary / contentPreview / score / importance / source

RagSnippet:
  documentId / chunkId / title / sourceUri / contentPreview / score

ReflectionCandidate:
  type / evidence / confidence / suggestedMemoryCategory
```

不要把完整 chunk 原文、完整用户隐私、完整工具返回塞进 LangGraph state。

## 6. 节点接入方式

### 6.1 build_team_context 节点

主图开始后先构建团队上下文：

```text
START
  -> build_team_context
  -> plan
```

职责：

- 调 `core.context` 构建 `TEAM_PLANNER` 初始上下文。
- 预取少量 planner memory / rag summary。
- 初始化 Team limits 和 trace context。
- 发 `team_context` 或写入 trace attributes。

### 6.2 plan 节点

Planner 只读 `TEAM_PLANNER_CONTEXT`：

- 用户目标。
- Profile 约束。
- 偏好/事实/摘要类 Memory。
- RAG 摘要或标题。

Planner 输出 TaskPlan，不写 memory。

### 6.3 schedule / execute_batch 节点

`schedule` 根据 dependsOn 选 ready tasks。

`execute_batch` 对每个 task 构建 `TEAM_EXECUTOR_CONTEXT`：

```text
taskId -> current task
       -> task scoped memory recall
       -> task scoped rag recall
       -> authorized tools
       -> Executor / ReActTaskGraph
```

注意：

- 每个 task 的 memory/rag 召回必须带 task query，不应直接复用 Planner 的全部上下文。
- 并发任务共享同一个 bounded executor 策略，不能无界并发查 memory/rag。
- 工具失败只写入 `reflectionCandidates`，不直接写 `memories`。

### 6.4 evaluate_tasks / global_review 节点

Evaluator 和 Reviewer 只读执行结果、计划、引用来源和必要上下文：

- 不调用工具。
- 不写 Memory。
- 输出结构化评估和风险。

### 6.5 reflect 节点

Reflector 是唯一生成长期记忆候选的节点。

输入：

- 原始用户目标。
- 计划历史。
- 子任务结果。
- 工具失败记录。
- GlobalReview。
- 最终答案草稿。
- Trace 摘要。

输出 `MemoryWriteCandidate`：

```json
{
  "category": "reflection|tool_failure|summary|fact|preference",
  "content": "...",
  "evidenceTaskIds": ["task-1", "task-3"],
  "confidence": 0.87,
  "sensitivity": "none|redacted|blocked",
  "writeReason": "team_reflection"
}
```

ReflectNode 不直接拼 SQL，也不绕过隐私扫描。

### 6.6 finalize_memory_writes 节点

建议在 `reflect` 后加一个单独节点：

```text
reflect -> finalize_memory_writes -> final_answer -> END
```

职责：

- 检查 Profile 的 memoryStrategy。
- 对候选内容做敏感扫描。
- 去重、冲突处理、supersede。
- 调 `MemoryWriteService` 写入。
- 写入失败不影响最终回答，只记录 trace。

## 7. 多 Agent 写入类型

建议第一版只允许写这些 category：

| category | 来源 | 示例 | 默认 |
|---|---|---|---|
| reflection | Reflector | 这类任务适合先检索配置文档再调用工具 | 允许 |
| tool_failure | Executor/ReAct 候选，Reflector 汇总 | 某工具参数 city 需要英文城市名 | 允许 |
| summary | Orchestrator/Reflector | 本轮团队完成了某项目的 RAG 方案设计 | 允许 |
| fact | 高置信结果 | 当前项目 Team 模式将替换为 LangGraph4j | 谨慎允许 |
| preference | 用户显式表达 | 用户希望直接替换旧 Team，不保留旧实现 | 仅显式允许 |

禁止写入：

- Planner 未执行计划。
- Reviewer 的纯质疑。
- ReAct 中间草稿。
- 工具返回原文。
- RAG chunk 原文。
- 低置信推测。

## 8. Trace 与 SSE

新增或复用 Span：

```text
team.context.build
  -> memory.recall       role=planner/executor/reviewer/reflector
  -> rag.search          role=planner/executor/reviewer/reflector

team.reflect
  -> team.memory.candidate
  -> memory.write
  -> memory.dedup
  -> memory.conflict.resolve
```

SSE 建议：

| 阶段 | SSE 事件 | 说明 |
|---|---|---|
| build_team_context | `team_context` | 可选，前端不一定展示 |
| execute task recall | 不新增或 trace-only | 避免事件太吵 |
| reflect | `team_reflect` | 展示反思摘要 |
| memory write | `team_memory_write` | 可选，只展示写入数量，不展示敏感内容 |

第一版可以只写 Trace，不扩展前端 SSE，以降低联调成本。

## 9. 安全与隔离

多 Agent 记忆接入必须继承单 Agent 的隔离边界：

- `tenant_id`
- `application_id`
- `owner_user_id`
- `profile_id`
- `memoryStrategy`
- `source_type=memory|rag`

额外要求：

- 子任务并发召回不能丢失 tenant/app/user/profile filter。
- LangGraphTeamState 不保存完整敏感原文。
- Trace attributes 只保存 hash、数量、category、status，不保存完整内容。
- RAG citation 只能来自 `RagSnippet`，不能来自 `MemorySnippet`。

## 10. 实施步骤

### Step A：Team Context Schema

- 新增 `ContextSchemaKind.TEAM_PLANNER/TEAM_EXECUTOR/TEAM_REVIEWER/TEAM_REFLECTION`。
- 扩展 `ContextSchemaAssembler` 支持 Team Schema。
- 增加单元测试验证不同角色 Slot 不同。

### Step B：LangGraphTeamState 快照字段

- 增加 memory/rag snippets、reflectionCandidates、memoryWriteCandidates。
- 保证 state 可序列化。
- 不引入 Entity/Mapper/Service 对象。

### Step C：节点接入 Context

- `build_team_context` 构建 Planner 初始上下文。
- `execute_batch` 按 task 构建 Executor 上下文。
- `global_review` 构建 Reviewer 上下文。
- `reflect` 构建 Reflection 上下文。

### Step D：ReflectNode 写入候选

- 生成结构化 `MemoryWriteCandidate`。
- 限制可写 category。
- 候选内容只保留摘要和证据 taskId。

### Step E：FinalizeMemoryWritesNode

- memoryStrategy 检查。
- 敏感扫描。
- 去重/冲突处理。
- 调 `MemoryWriteService`。
- Trace 记录写入数量和失败原因。

### Step F：测试

- Planner 可读偏好但不能写 memory。
- Executor 工具失败不会立即写 memory。
- Reflector 可生成 tool_failure candidate。
- `READ_ONLY` 下不写入长期记忆。
- `SESSION_ONLY` 下不读写长期记忆。
- RAG citation 不引用 memory。
- 并发 task recall 不串 tenant/profile。

## 11. 与现有文档关系

- `记忆系统优化/00-单Agent记忆与RAG统一设计文档.md`：Memory/RAG 的基础能力和护栏。
- `单向多Agent优化/01-LangGraph4j多Agent协作替换设计.md`：LangGraph4j 多 Agent 主流程。
- 本文：两者之间的接入层设计，定义多 Agent 如何安全复用 Memory/RAG。
