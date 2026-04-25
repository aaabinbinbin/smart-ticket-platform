# Agent 架构说明

## 定位

本项目的 Agent 是受控型业务 Agent。它不是让大模型自由决定所有动作，而是在后端业务边界内让模型参与理解、补全和表达，真正的业务变更由后端 Tool、权限、参数校验和风险确认控制。

这种设计适合工单、审批、运维、客服等高约束业务场景。

## 主链路

`AgentFacade` 是 Agent 应用层主入口，当前主链如下：

```text
load session context
-> hydrate memory
-> route intent
-> resolve execution policy
-> build/load plan
-> continue pending / execute deterministic command / execute read-only ReAct
-> render reply
-> update session context
-> remember memory
-> write trace
-> return reply + context + plan + traceId
```

同步接口使用一次性返回，SSE 接口复用同一条业务主链，只额外输出事件：

```text
accepted -> route -> status/delta -> final
```

其中 `final` 事件必须包含完整 `AgentChatResult`。

## 核心组件

### IntentRouter

负责把用户输入路由到业务意图：

- `QUERY_TICKET`
- `CREATE_TICKET`
- `TRANSFER_TICKET`
- `SEARCH_HISTORY`

低置信度请求不会直接执行，而是进入澄清分支。

### AgentPlanner

负责生成和推进执行计划。

计划中包含：

- `currentStage`
- `nextAction`
- `waitingForUser`
- `nextSkillCode`
- `riskLevel`
- `requiredSlots`
- `completedSteps`

典型状态：

- 低置信度：`WAIT_USER + CLARIFY_INTENT`
- 缺少创建工单字段：`COLLECT_REQUIRED_SLOTS + COLLECT_SLOTS`
- 高风险动作：`WAIT_USER + CONFIRM_HIGH_RISK`
- 正常执行：`EXECUTE_SKILL + EXECUTE_TOOL`
- 返回结果：`SUMMARIZE_RESULT + RETURN_RESULT`

### SkillRegistry

Skill 是 Agent 能力的注册单元，描述“能做什么”，并绑定底层 Tool。

每个 Skill 包含：

- skill code
- 支持的 intent
- 输入字段
- 所需权限
- 风险等级
- 是否允许自动执行
- 绑定的 Tool

当前主链通过 `SkillRegistry` 选择能力，`AgentFacade` 不再直接按 intent 硬编码分发具体 Tool。新增能力时，优先新增 Skill 和 Tool，再注册到 Spring 容器。

### AgentExecutionGuard

执行前的统一边界控制。

它负责：

- Tool 是否存在
- Tool 是否支持当前 intent
- 参数是否完整
- 风险等级是否需要确认
- 是否允许自动执行

例如转派工单属于高风险写操作，会先返回确认摘要。用户回复“确认执行”后，才继续执行真实 Tool。

### ExecutionPolicy

执行策略位于意图路由和具体执行之间，负责按 intent、风险等级、工具白名单、确认要求和预算上限决定本轮执行模式。

典型模式：

- `READ_ONLY_REACT`：查询工单、检索历史案例，只允许暴露只读工具。
- `READ_ONLY_DETERMINISTIC`：LLM 不可用或降级时，查询类请求走后端确定性查询。
- `WRITE_COMMAND_EXECUTE`：创建工单等低风险写操作，经过参数校验、权限校验和风险判断后执行。
- `HIGH_RISK_CONFIRMATION`：转派工单等高风险写操作，必须先进入确认 pendingAction。
- `PENDING_CONTINUATION`：继续上一轮补参或确认。

### PendingActionCoordinator

`PendingActionCoordinator` 是补参、确认、取消的统一入口。

它负责：

- 创建工单缺参时保存草稿。
- 用户补参时合并参数。
- 转派等高风险操作确认前保存 pendingAction。
- 用户确认后执行确定性命令。
- 用户取消后清空 pendingAction，确保旧操作不会继续执行。

它只修改内存中的 session context，不直接保存 session、不写 memory、不写 trace；这些动作仍由外层主链统一收口。

### Tool

Tool 是实际执行业务动作的后端能力。

当前已有：

- 查询工单
- 创建工单（type/category/priority/typeProfile 由 TicketCommandService 内部 enrichment 自动补全）
- 转派工单
- 检索历史案例

Tool 不负责规划，也不负责风险决策；它只执行已经被放行的业务动作。

### TicketCreateEnrichmentService

工单创建字段自动补全服务，用于简化用户输入。

- 用户只需传 title + description，系统根据规则自动推断 type、category、priority、typeProfile。
- 如果用户显式传了某个字段，优先尊重用户输入，不覆盖。
- 当前采用规则实现（关键词匹配），后续可扩展 LLM enrichment 分支（需超时和降级设计）。
- enrichment 在 `TicketCommandService.createTicket()` 内部统一调用，Agent 创建工单和 HTTP 创建工单均自动受益。

写操作禁止让 LLM 直接修改业务数据。创建、转派等动作必须走：

```text
intent route
-> parameter extraction / slot filling
-> command draft
-> guard / permission / risk check
-> confirmation if needed
-> deterministic command execution
-> summary
-> reply rendering
```

### Memory

当前实现三类记忆：

- **工作记忆**：保存在会话上下文中，用于多轮补槽和当前活跃工单。
- **工单领域记忆**：记录当前会话与工单相关的上下文，存储在 Redis（TTL 30 分钟）。
- **用户偏好记忆**：持久化用户偏好，例如常用摘要视角，存储在数据库。

三类记忆均包含可靠性元数据：

| 字段 | 说明 |
|---|---|
| `source` | 记忆来源：USER_EXPLICIT / TOOL_RESULT / INFERRED / LLM_EXTRACTED |
| `confidence` | 置信度（0-1），越低越只能用于推荐，不能自动执行 |
| `expiresAt` | 过期时间，过期后不再使用 |

> **记忆不是权威事实源。** 数据库 / Tool 实时查询结果 = 权威事实。Agent memory = 上下文缓存和偏好线索。低置信度记忆只能用于推荐，不能自动执行。涉及工单状态、处理人、审批状态时必须实时查数据库。

主链入口会 hydrate memory（跳过过期记忆），Tool 执行后会 remember（附带 source/confidence/expiresAt）。

### Trace

每次 Agent 调用都会生成 trace。

trace 记录：

- route
- planner 决策
- prompt 版本
- Spring AI tool calling 是否使用
- fallback 是否触发
- skill/tool 执行结果
- 最终回复
- 耗时

接口响应会返回 `traceId`，便于定位问题和复盘执行链路。

## Spring AI 与 fallback

项目支持 Spring AI Tool Calling，但它不是唯一执行路径。

当 Spring AI 未启用、不可用、没有产生 Tool 调用或调用失败时，系统会进入确定性 fallback：

```text
route intent
-> skill registry
-> parameter extractor
-> guard
-> backend tool
```

这保证了即使没有模型服务，核心工单链路仍然可运行。

## 高压治理

P6 后 Agent 增加了基础高压治理：

- `AgentRateLimitService`：用户级和全局滑动窗口限流。
- `AgentSessionLockService`：同一 sessionId 单实例互斥，避免 pendingAction、activeTicketId、recentMessages 并发串写。
- `AgentTurnBudgetService`：控制每轮 LLM、Tool、RAG 次数和总耗时。
- `AgentDegradePolicyService`：把限流、超时、预算超限和降级转换为结构化 summary。
- `AgentBulkheadConfig`：预留 LLM/RAG 隔离线程池。

当前 session lock 是单 JVM 内存锁，多实例部署时可替换为 Redis lock，调用方契约不变。

## SSE 流式输出

P7 后新增：

```text
POST /api/agent/chat/stream
```

事件类型：

- `accepted`：请求已进入 Agent。
- `route`：意图路由完成。
- `status`：主链状态提示。
- `delta`：只读总结文本。
- `final`：完整 `AgentChatResult`。
- `error`：错误码、错误原因和 traceId。

流式接口只改变输出体验，不改变业务执行语义。写操作仍然走确定性命令链路，ReAct 仍然只允许只读工具。

## 高风险操作确认

高风险动作不会自动落地。

当前典型高风险动作：

- 转派工单

执行流程：

```text
user asks transfer
-> route TRANSFER_TICKET
-> planner marks high risk
-> guard returns NEED_CONFIRMATION
-> save pending action
-> user confirms
-> execute transfer tool
```

取消或未确认时，不会执行真实业务变更。

## RAG 与知识闭环

Agent 检索历史案例时会进入 RAG 能力。

RAG 侧已经实现：

- **双路召回**：originalQuery + rewrittenQuery 各自检索，合并后去重再 rerank。rewrite 被安全规则判定为不安全时降级为仅使用 originalQuery。
- **query rewrite 安全规则**：禁止删除否定词和核心故障词，改写后长度不少于原文 50%。
- MySQL fallback 检索
- pgvector 可选主路径
- rerank（基于词覆盖 + 用户反馈）
- 用户反馈
- 知识候选审核
- 工单关闭后可靠入库

工单关闭后的知识构建不是直接同步执行，而是通过 RabbitMQ 和数据库 task 保证可靠性。

## 可观测接口

Trace 查询：

```text
GET /api/agent/traces/by-session?sessionId=xxx
GET /api/agent/traces/recent-by-user?userId=1&limit=20
GET /api/agent/traces/by-failure?failureType=FALLBACK
```

最近指标：

```text
GET /api/agent/traces/metrics/recent-by-user?userId=1&limit=50
```

指标包括：

- 调用总数
- 澄清次数
- Spring AI 使用次数
- Spring AI 成功次数
- fallback 次数
- 降级次数
- 失败次数
- 平均耗时
- P95 耗时
- route 分布
- skill 使用次数
- failureType 分布

压测与稳定性验收见：

```text
docs/agent-stability-acceptance.md
scripts/agent-smoke-load.ps1
```

## 当前边界

- 当前是受控型 Agent 初版，不是完全自治 Agent。
- Skill 目前由 Java 类注册，不是 Markdown 动态加载。
- Prompt 已文件化并支持版本记录，但还可以继续细化模板管理。
- pgvector 默认关闭，需要真实 PostgreSQL + pgvector 环境。
- 前端审核页面未实现，但后端审核 API 已完成。
- `AgentFacade` 当前仍承载主链编排，后续如继续演进，可把主链进一步迁移到 `AgentOrchestrator`。
