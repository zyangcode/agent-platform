# Token 配额并发扣减与 Redis 优化方案

## 背景

当前项目中，Application 是 API Key 和额度统计的归属单位，不等同于“一个正在使用的人”。

一个 User 可以创建多个 Application，每个 Application 有自己的 API Key。Token 消耗按 Application 统计。因此同一个 Application 可能同时产生多个请求。

## 为什么同一个 Application 会有并发请求

### 1. 同一个用户可能并发操作

即使只有一个登录用户，也可能同时发起多个请求：

```text
打开多个浏览器标签页
前端按钮未禁用导致连点
一次对话未结束又发起下一次对话
浏览器或网络层自动重试
```

这些请求在后端看来可能都是：

```text
同一个 tenantId
同一个 applicationId
同一个 userId
同一个 profileId
```

但它们是多次并发调用。

### 2. API Key 可以被程序并发调用

Application 绑定 API Key，开发者可能把同一个 API Key 放到脚本、服务、定时任务或 worker 中。

例如：

```text
10 个 worker 同时调用 /api/ai/chat/stream
```

这些请求都属于同一个 Application。

### 3. 一个外部应用背后可能有多个终端用户

用户可能用平台 API Key 做了自己的业务系统。

外部系统中有很多终端用户：

```text
外部用户 1
外部用户 2
外部用户 3
...
```

这些外部用户同时访问时，在本平台看来可能都是同一个 Application 发出的请求。

### 4. 重试会制造重复并发

如果客户端超时后自动重试：

```text
原请求仍在执行
重试请求又进来了
```

没有幂等控制时，两个请求都会进入模型调用链路，也都会尝试消耗配额。

## 当前配额实现的问题

当前 `DefaultQuotaService.reserve()` 主要做了：

```text
检查单次请求 token 上限
touch quota_config version
插入 quota_reservation
```

但它没有真正完成：

```text
日已用量 + 本次预扣 <= 日额度
月已用量 + 本次预扣 <= 月额度
```

因此，如果同一个 Application 并发 100 个请求进来，每个请求预估 1000 token，当前实现可能都能 reserve 成功。

问题本质：

```text
Application = 额度和 API Key 的归属单位
Request = 实际一次调用

一个 Application 可以同时产生多个 Request
```

所以配额判断必须按并发场景设计，不能假设同一 Application 同时只有一个请求。

## 正确的配额扣减目标

正确配额链路应该满足：

```text
请求开始前：原子预扣 estimatedTokens
请求成功后：按 actualTokens 结算，多退少补
请求失败后：释放预扣
重复请求：幂等处理，不能重复扣
并发请求：不能突破日/月额度
```

同时要保留数据库记录，用于审计、查询、Trace 和后续账单统计。

## Redis 适合解决什么

Redis 适合负责实时并发控制：

- 原子配额预扣
- 原子退回/补扣
- 请求频率限制
- 幂等防重复提交
- 短期热点计数

PostgreSQL 仍然负责最终持久化：

- `quota_reservations`
- `token_usage_logs`
- Trace
- 管理后台查询
- 审计和报表

推荐架构：

```text
Redis = 实时额度账本
PostgreSQL = 最终审计账本
```

## Redis Key 设计

### Application 日额度

```text
quota:day:app:{tenantId}:{applicationId}:{yyyyMMdd}
```

示例：

```text
quota:day:app:1:1001:20260609
```

### Application 月额度

```text
quota:month:app:{tenantId}:{applicationId}:{yyyyMM}
```

示例：

```text
quota:month:app:1:1001:202606
```

### User 日额度

```text
quota:day:user:{tenantId}:{userId}:{yyyyMMdd}
```

### User 月额度

```text
quota:month:user:{tenantId}:{userId}:{yyyyMM}
```

### 请求预扣记录

```text
quota:reservation:{traceId}
```

保存内容：

```json
{
  "tenantId": 1,
  "applicationId": 1001,
  "userId": 2001,
  "estimatedTokens": 1000,
  "status": "RESERVED"
}
```

### 幂等请求

```text
idempotent:chat:{tenantId}:{applicationId}:{clientRequestId}
```

用于防止同一个客户端请求重复进入模型调用链路。

## 预扣流程

请求进入 Gateway 后，先根据 Profile、模型和输入估算本次请求的 token 成本。

MVP 可以先用固定预估：

```text
estimatedTokens = 1000
```

更精细后续再按输入长度、上下文预算、模型 max tokens 估算。

预扣时必须原子执行：

```text
读取 dayUsed
读取 monthUsed
判断 dayUsed + estimatedTokens <= dailyLimit
判断 monthUsed + estimatedTokens <= monthlyLimit
通过则 dayUsed += estimatedTokens
通过则 monthUsed += estimatedTokens
写入 reservation
失败则不扣，直接返回 429
```

这一步建议用 Redis Lua 脚本完成，避免并发下读写分离造成超扣。

## 为什么必须用原子脚本

错误写法：

```text
GET used
if used + estimated <= limit
INCRBY used estimated
```

这个写法在并发下有竞态。

例如当前已用 9000，额度 10000，两个请求同时预扣 1000：

```text
请求 A 读到 used=9000，通过
请求 B 读到 used=9000，也通过
请求 A INCRBY 1000
请求 B INCRBY 1000
最终 used=11000，额度被突破
```

正确写法是把判断和扣减放到同一个 Lua 脚本里：

```text
Redis 单线程执行 Lua
判断 + 扣减 是一个原子操作
```

## 结算流程

模型调用成功后，模型返回真实 token usage：

```text
actualTokens
```

结算逻辑：

```text
delta = actualTokens - estimatedTokens
```

如果：

```text
actualTokens < estimatedTokens
```

说明预扣多了，需要退回：

```text
DECRBY quota keys estimatedTokens - actualTokens
```

如果：

```text
actualTokens > estimatedTokens
```

说明预扣少了，需要补扣：

```text
INCRBY quota keys actualTokens - estimatedTokens
```

补扣时如果超过额度，可以有两种策略：

```text
策略 A：允许本次完成，但记录超额，后续请求拒绝
策略 B：严格拒绝最终结果，但用户体验较差
```

更推荐策略 A，因为模型已经调用完成，拒绝最终结果不能节省成本。

## 失败释放流程

如果请求在模型调用前或调用中失败：

```text
释放 reservation
退回 estimatedTokens
标记 reservation = RELEASED
```

注意释放也必须幂等：

```text
同一个 traceId 多次 release，只能退回一次
```

因此 `quota:reservation:{traceId}` 中要保存状态：

```text
RESERVED
COMMITTED
RELEASED
```

Lua 脚本释放时先判断当前状态是否仍为 `RESERVED`。

## 幂等控制

对话请求应使用 `clientRequestId` 做幂等。

首次请求：

```text
SET idempotent:chat:{tenantId}:{applicationId}:{clientRequestId} traceId NX EX 300
```

如果设置成功，说明是新请求。

如果设置失败，说明相同请求已经提交过：

```text
返回已有 traceId
或拒绝重复提交
或复用已有执行结果
```

MVP 阶段可以先拒绝重复提交：

```text
HTTP 409 Duplicate request
```

这样可以防止重复点击、网络重试导致重复扣额度和重复调用模型。

## 数据库如何配合

Redis 做实时扣减，但数据库仍要写：

### 请求开始

写入：

```text
quota_reservations.status = RESERVED
estimated_tokens = estimatedTokens
trace_id = traceId
```

### 请求成功

更新：

```text
quota_reservations.status = COMMITTED
actual_tokens = actualTokens
```

同时写入：

```text
token_usage_logs
```

### 请求失败

更新：

```text
quota_reservations.status = RELEASED
```

数据库更新要保持幂等：

```text
where trace_id = ?
and status = RESERVED
```

## Redis 与数据库不一致怎么办

Redis 和数据库是双账本，必须考虑不一致。

推荐补偿机制：

### 1. 定期对账

后台任务按天/月扫描数据库：

```text
token_usage_logs
quota_reservations
```

重新聚合真实消耗，修正 Redis 或生成差异报告。

### 2. Redis 过期策略

日额度 key 设置到次日过期：

```text
TTL 到第二天 00:00 后再保留一段缓冲
```

月额度 key 设置到下月过期：

```text
TTL 到下月 1 日后再保留一段缓冲
```

### 3. Redis 不可用降级

Redis 不可用时有两种策略：

```text
保守策略：直接拒绝 AI 调用，返回 503
宽松策略：降级到数据库 CAS/行锁扣减
```

对于考核项目，建议采用保守策略或明确 fallback 到数据库，避免配额失控。

## 接口链路建议

Gateway 中配额链路建议改成：

```text
SensitiveDataFilter
→ RateLimitFilter
→ QuotaReserveFilter
→ RuntimeInvokeFilter
→ QuotaCommit/Release
→ TokenRecordFilter
→ AlertFilter
```

关键点：

- 限流和配额必须在模型调用前。
- commit/release 必须在 finally 或异常分支中保证执行。
- alert 失败不能影响用户响应。

## 实现优先级

### P0：Redis 原子 Token 预扣

先实现：

```text
reserve(traceId, tenantId, applicationId, userId, estimatedTokens)
commit(traceId, actualTokens)
release(traceId)
```

保证并发下不能突破日/月额度。

### P1：clientRequestId 幂等

防止重复点击、网络重试、客户端自动重放。

### P1：Gateway 限流

按 API Key / Application / User 做 QPS 或 RPM 限制。

### P2：数据库对账任务

定期用数据库真实 usage 修正 Redis 统计。

### P3：更准确的 estimatedTokens

从固定 1000 优化为基于上下文、输入长度、模型配置的动态估算。

## 一句话总结

Application 是额度和 API Key 的归属单位，不代表同一时间只能有一个请求。同一个 Application 可能因为多标签页、重复点击、API Key worker、外部多用户或客户端重试产生并发请求。因此配额扣减必须按并发设计：请求开始前用 Redis Lua 原子预扣日/月额度，请求成功后按真实 token 多退少补，请求失败时幂等释放，同时用 PostgreSQL 保留 reservation 和 token usage 审计记录。
