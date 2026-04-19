# 阶段十：Agent 会话上下文增强

## 1. 阶段目标

阶段十在阶段九“单 Agent 编排”的基础上，补齐短期多轮对话能力。

本阶段解决的问题是：

1. 用户可以用“它”“这个”“刚才那个工单”引用上一轮工单。
2. Tool 缺少必要参数时，Agent 可以保存待补充动作。
3. 用户下一轮补充信息后，Agent 优先恢复 pending action，而不是重新开始完整路由。
4. 上下文仍然只作为短期会话状态，不替代 MySQL 中的工单事实数据。

本阶段不做：

1. 长期记忆。
2. 复杂实体图谱。
3. 多 Agent。
4. 多 Tool 自动循环。
5. 默认 RAG。

## 2. Redis 会话结构

会话上下文继续保存在 Redis：

```text
agent:session:{sessionId}
```

对应 Java 对象：

```text
AgentSessionContext
```

当前字段：

| 字段 | 说明 |
| --- | --- |
| `activeTicketId` | 当前对话最近查询、创建或操作过的工单 ID |
| `activeAssigneeId` | 当前对话最近操作或确认过的处理人 ID |
| `lastIntent` | 最近一次识别出的 Agent 意图 |
| `recentMessages` | 最近几轮用户消息摘要 |
| `pendingAction` | 当前等待用户补充或确认的动作 |

注意：

`activeTicketId` 和 `activeAssigneeId` 只是会话指针，不是事实来源。查询工单详情仍然必须走 `QueryTicketTool -> TicketService`，写操作仍然必须走 Tool 和 biz 层。

## 3. Pending Action 结构

新增对象：

```text
AgentPendingAction
```

字段：

| 字段 | 说明 |
| --- | --- |
| `pendingIntent` | 待继续执行的业务意图 |
| `pendingToolName` | 待继续调用的 Tool 名称 |
| `pendingParameters` | 已经抽取到的结构化参数 |
| `awaitingFields` | 当前仍缺少的字段 |
| `lastToolResult` | 最近一次 Tool 观察结果，便于排查和后续回复生成 |

pending action 只保存“下一轮如何继续”的短期状态，不保存复杂推理链，也不保存长期用户画像。

## 4. 当前调用流程

阶段十后的 `/api/agent/chat` 主流程：

```text
AgentChatController
  -> AgentChatService
     -> TicketAgentOrchestrator
        -> AgentSessionCacheService 读取 agent:session:{sessionId}
        -> 判断是否存在 pendingAction
```

如果存在 pending action：

```text
AgentPendingActionService
  -> 从当前消息抽取补充参数
  -> 与 pendingParameters 合并
  -> 执行简单指代消解
  -> 构造 ToolCallPlan
  -> ToolCallPlanValidator 校验计划
  -> AgentToolExecutor 校验 requiredFields 并执行 Tool
  -> AgentResponseComposer 生成澄清或总结
  -> AgentContextUpdater 更新 activeTicketId / activeAssigneeId / lastIntent / recentMessages
  -> AgentPendingActionService 根据 ToolResult 保留或清理 pendingAction
  -> AgentSessionCacheService 保存上下文
```

如果不存在 pending action：

```text
继续阶段九正常单 Agent 编排：
fallback route/plan
  -> LLM tool-call-plan
  -> 代码校验
  -> 指代消解
  -> AgentToolExecutor
  -> 回复生成
  -> 上下文更新
  -> pendingAction 刷新
```

## 5. Pending Action 更新时机

### 5.1 保存 pending action

当 `AgentToolExecutor` 或具体 Tool 返回：

```text
AgentToolStatus.NEED_MORE_INFO
```

编排层会保存：

```text
pendingIntent
pendingToolName
pendingParameters
awaitingFields
lastToolResult
```

典型场景：

```text
用户：创建一个工单
系统：请补充标题、描述、分类、优先级
```

此时本轮不会执行写数据库动作，只会把待执行创建动作存入 Redis 会话。

### 5.2 恢复 pending action

下一轮用户消息到来时，编排器先检查 `pendingAction`。

如果存在：

1. 不重新做完整意图路由。
2. 把用户当前消息当作补充信息。
3. 抽取补充参数。
4. 与 `pendingParameters` 合并。
5. 重新校验 Tool 计划和必填参数。
6. 参数完整后通过 `AgentToolExecutor` 继续执行。

### 5.3 清理 pending action

当 Tool 返回：

```text
SUCCESS
FAILED
```

说明本次 pending action 已经结束，系统会清理 `pendingAction`。

当 Tool 继续返回：

```text
NEED_MORE_INFO
```

系统会用新的参数和缺失字段刷新 `pendingAction`，继续等待下一轮补充。

## 6. 参数合并策略

pending 参数合并遵循保守策略：

1. 已有参数默认不被覆盖。
2. 当前消息只补充原来为空的字段。
3. 标题、描述、分类、优先级要求用户表达中有较明确线索，避免“补充一下，优先级是高”把标题误覆盖成这句话。
4. 工单 ID 和处理人 ID 仍然进入 Tool 前的 requiredFields 校验。

示例：

```text
pendingParameters.priority = null
用户：补充一下，优先级是高
合并后：priority = HIGH
```

## 7. 简单指代消解

新增组件：

```text
AgentContextReferenceResolver
```

当前只做短期、明确指代：

| 用户表达 | 映射目标 |
| --- | --- |
| “它” | `activeTicketId` |
| “这个” | `activeTicketId` |
| “这个工单” | `activeTicketId` |
| “刚才那个工单” | `activeTicketId` |
| “他” | `activeAssigneeId` |
| “这个处理人” | `activeAssigneeId` |

示例：

```text
上一轮：帮我查一下 100 号工单
上下文：activeTicketId = 100
本轮：帮我查一下刚才那个工单
参数：ticketId = 100
```

再如：

```text
上下文：activeTicketId = 100
本轮：把它转给 3，确认
参数：ticketId = 100, assigneeId = 3
```

当前版本不做复杂姓名解析。“把它转给张三”这类自然语言可以进入转派意图和 pending action，但如果没有可解析的 `assigneeId`，仍然会追问目标处理人 ID。后续可以单独增加“处理人解析 Tool”或用户目录查询能力。

## 8. 与阶段九的关系

阶段九负责：

```text
单 Agent 编排
LLM 生成受控 Tool 调用计划
代码校验 Tool、intent、风险等级、确认要求
Tool 执行和观察
```

阶段十新增：

```text
pending action
多轮补参恢复
简单指代消解
Redis 会话状态增强
```

阶段十没有改变安全边界：

1. LLM 仍然不能直接执行 Tool。
2. 写操作仍然必须通过 Tool 和 biz 层。
3. 高风险写操作仍然需要用户确认。
4. requiredFields 仍然由代码校验。
5. biz 层仍然是最终权限和状态机裁决点。

## 9. 已知限制

1. 当前只保存短期会话上下文。
2. 处理人姓名到用户 ID 的解析还没有落地。
3. 创建工单参数抽取仍然较保守，复杂自然语言补参后续可以继续增强。
4. 当前仍然是单 Tool 单轮观察，不做多 Tool 自动循环。
5. 当前不会把历史工单知识默认接入 QUERY_TICKET、CREATE_TICKET、TRANSFER_TICKET。

## 10. 是否可以进入阶段十一

可以进入阶段十一。

阶段十一建议开始做知识构建服务与向量化入库，但需要继续保持边界：

1. RAG 用于历史经验和知识检索，不替代事实查询。
2. `QUERY_TICKET` 仍然优先查询当前工单事实数据。
3. `CREATE_TICKET`、`TRANSFER_TICKET` 不依赖历史检索片段才能执行。
4. RAG 不应默认进入所有请求链路。

## 11. 阶段 10.5：执行边界平台化整理

阶段十完成后，又做了一次小重构，目标是让 Agent 编排代码更像流程，让安全边界更像平台能力。

重构前，执行前校验分散在：

```text
TicketAgentOrchestrator
ToolCallPlanValidator
AgentToolExecutor
AgentPendingActionService
具体 Tool
biz 层
```

重构后新增：

```text
com.smartticket.agent.execution.AgentExecutionGuard
com.smartticket.agent.execution.AgentExecutionDecision
com.smartticket.agent.execution.AgentExecutionDecisionStatus
```

### 11.1 AgentExecutionGuard

`AgentExecutionGuard` 是统一执行边界入口，负责：

1. 校验 `toolName` 是否存在。
2. 校验 Tool 是否支持当前 `intent`。
3. 校验 Tool 是否声明风险等级。
4. 判断高风险写操作是否需要用户确认。
5. 根据 Tool 元数据校验 requiredFields。
6. 输出统一的 `AgentExecutionDecision`。

Guard 不执行 Tool，也不调用 biz 层。

### 11.2 AgentExecutionDecision

`AgentExecutionDecision` 把执行前判断归一为四类状态：

| 状态 | 含义 |
| --- | --- |
| `ALLOW_EXECUTE` | 可以执行 Tool |
| `NEED_CONFIRMATION` | 高风险写操作缺少明确确认 |
| `NEED_MORE_INFO` | 缺少 Tool 必填参数 |
| `REJECTED` | 计划不合法，不能进入执行阶段 |

非执行状态可以转换为 `AgentToolResult`，这样回复生成、上下文更新和 pending action 刷新可以复用同一条结果链路。

### 11.3 AgentToolExecutor

`AgentToolExecutor` 现在只负责执行已被 Guard 放行的 Tool：

```text
AgentExecutionGuard -> ALLOW_EXECUTE -> AgentToolExecutor -> AgentTool.execute()
```

如果决策是 `NEED_CONFIRMATION`、`NEED_MORE_INFO` 或 `REJECTED`，执行器不会调用 Tool，而是直接返回 Guard 生成的非执行结果。

### 11.4 编排流程变化

正常单 Agent 编排现在更接近：

```text
读取上下文
  -> pending action 判断
  -> 构造或生成 ToolCallPlan
  -> 指代消解
  -> AgentExecutionGuard.check()
  -> 如 LLM 计划被拒绝，回退 fallback 计划再 check()
  -> AgentToolExecutor.execute()
  -> 回复生成
  -> 上下文更新
  -> pending action 刷新
```

pending action 恢复也复用同一个 Guard：

```text
恢复 pending action
  -> 合并补参
  -> 指代消解
  -> AgentExecutionGuard.check()
  -> AgentToolExecutor.execute()
```

这样后续新增 RAG Tool、知识构建 Tool 或更多业务 Tool 时，不需要把风险确认、requiredFields 等校验散落到编排器里。

### 11.5 保留的防御层

这次重构没有删除具体 Tool 和 biz 层校验。

保留原因：

1. Guard 是编排层的统一入口，负责提前拦截和改善用户体验。
2. 具体 Tool 的校验是防御性兜底，防止未来有人绕过编排器直接调用 Tool。
3. biz 层仍然负责最终业务权限、状态机、并发和事务裁决。

因此当前边界是：

```text
LLM 负责理解和计划
Orchestrator 负责流程
ExecutionGuard 负责执行前策略
ToolExecutor 负责调用 Tool
Tool 负责业务能力封装
biz 负责最终业务裁决
```
