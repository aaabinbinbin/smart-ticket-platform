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
-> build/load plan
-> select skill
-> guard before execution
-> Spring AI tool calling or deterministic fallback
-> execute tool
-> update plan
-> update session context
-> remember memory
-> write trace
-> return reply + context + plan + traceId
```

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

### Tool

Tool 是实际执行业务动作的后端能力。

当前已有：

- 查询工单
- 创建工单
- 转派工单
- 检索历史案例

Tool 不负责规划，也不负责风险决策；它只执行已经被放行的业务动作。

### Memory

当前实现三类记忆：

- 工作记忆：保存在会话上下文中，用于多轮补槽和当前活跃工单。
- 工单领域记忆：记录当前会话与工单相关的上下文。
- 用户偏好记忆：持久化用户偏好，例如常用摘要视角。

主链入口会 hydrate memory，Tool 执行后会 remember。

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

- query rewrite
- MySQL fallback 检索
- pgvector 可选主路径
- rerank
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
- route 分布
- skill 使用次数

## 当前边界

- 当前是受控型 Agent 初版，不是完全自治 Agent。
- Skill 目前由 Java 类注册，不是 Markdown 动态加载。
- Prompt 已文件化并支持版本记录，但还可以继续细化模板管理。
- pgvector 默认关闭，需要真实 PostgreSQL + pgvector 环境。
- 前端审核页面未实现，但后端审核 API 已完成。
