# Agent 稳定性验收说明

## 目标

P8 阶段用于证明 `smart-ticket-agent` 重构后的结果可观测、可压测、可讲清楚。验收重点不是继续扩大 Agent 能力，而是确认同步接口、SSE 接口、确定性写链路、只读 ReAct、pendingAction、trace 和高压治理在同一套边界内稳定工作。

## 必跑测试

```bash
mvn -q -pl smart-ticket-agent -am test
```

```bash
mvn -q -pl smart-ticket-api -am test
```

覆盖重点：

- `QUERY_TICKET` 正常查询。
- `SEARCH_HISTORY` 正常检索历史案例。
- `CREATE_TICKET` 缺参进入 pendingAction，补参后继续处理。
- `TRANSFER_TICKET` 必须确认后执行，取消后不执行。
- ReAct 只暴露只读工具，写工具不会进入 tool catalog。
- session lock 能阻止同一 session 并发串写。
- 单轮预算能阻止 LLM / Tool / RAG 超限。
- SSE final 事件返回完整 `AgentChatResult`。

## 压测脚本

脚本位置：

```powershell
.\scripts\agent-smoke-load.ps1
```

同步接口示例：

```powershell
.\scripts\agent-smoke-load.ps1 -BaseUrl "http://localhost:8080" -Token "<JWT>" -Concurrency 10 -Requests 50
```

SSE 接口示例：

```powershell
.\scripts\agent-smoke-load.ps1 -BaseUrl "http://localhost:8080" -Token "<JWT>" -Concurrency 5 -Requests 20 -Stream
```

脚本输出：

- 成功请求数。
- 失败请求数。
- 平均耗时。
- P95 耗时。
- 前 5 条错误样例。

## 指标口径

现有指标接口：

```text
GET /api/agent/traces/metrics/recent-by-user?userId=1&limit=50
```

指标来源是 `AgentTraceRecord`，不新增数据库表。主要字段：

- `total`：纳入统计的 trace 总数。
- `clarifyCount`：进入澄清或等待用户输入的次数。
- `springAiUsedCount`：使用 Spring AI Tool Calling 的次数。
- `springAiSuccessCount`：Spring AI 成功且未 fallback 的次数。
- `fallbackCount`：进入确定性 fallback 的次数。
- `degradedCount`：降级完成次数。
- `failedCount`：失败或带 failureType 的次数。
- `averageElapsedMillis`：平均耗时。
- `p95ElapsedMillis`：P95 耗时。
- `routeDistribution`：意图分布。
- `skillUsage`：技能使用分布。
- `failureDistribution`：失败类型分布。

## 稳定性验收建议

建议以 3 轮脚本结果为准：

| 场景 | 并发 | 请求数 | 预期 |
|---|---:|---:|---|
| 同步普通对话 | 10 | 50 | 失败数为 0 或只出现可解释的鉴权/业务校验失败 |
| 同步同 session 压力 | 10 | 50 | 允许出现 `AGENT_SESSION_BUSY`，不应出现 pendingAction 串写 |
| SSE 查询 | 5 | 20 | 能收到 final 事件，连接能正常关闭 |

验收时重点观察：

- `/api/agent/chat` 协议没有变化。
- `/api/agent/chat/stream` final 事件包含完整 `AgentChatResult`。
- traceId 能返回并能查到对应 trace。
- `failureDistribution` 中的 `AGENT_SESSION_BUSY`、`AGENT_BUDGET_EXCEEDED`、`AGENT_DEGRADED` 可解释。
- LLM 不可用时，查询类请求能走确定性降级。

## 面试表达

可以这样描述：

```text
我把智能工单平台的 Agent 重构为受控型业务 Agent：查询和历史检索允许只读 ReAct，创建、转派等写操作全部走确定性 Command 链路；通过 pendingAction 支持补槽和高风险确认，通过 session lock、rate limit、预算和降级策略保证压力下可控；同时提供同步和 SSE 双接口，final 事件复用完整 AgentChatResult，并用 trace metrics、压测脚本和稳定性验收文档证明链路可观测、可回归。
```
