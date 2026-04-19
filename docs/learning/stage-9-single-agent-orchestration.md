# 阶段九：单 Agent 编排设计

## 1. 阶段目标

阶段九的目标是把阶段八的“LLM 增强固定工作流”升级为“单 Agent 编排”。

阶段八中，代码固定执行：

```text
意图识别 -> 参数抽取 -> 选择 Tool -> 执行 Tool -> 总结
```

阶段九开始，LLM 可以生成一个受控工具调用计划，但仍然不能直接执行工具，也不能绕过业务层。

本阶段仍然不做：

1. 多 Agent 并行。
2. MCP。
3. 默认 RAG。
4. LLM 直接写数据库。
5. LLM 决定最终业务权限。

## 2. 新增核心对象

### 2.1 TicketAgentOrchestrator

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/TicketAgentOrchestrator.java
```

职责：

1. 读取会话上下文。
2. 构造阶段八 fallback 计划。
3. 调用 LLM 生成工具调用计划。
4. 校验计划中的 `intent`、`toolName` 和参数。
5. 校验 Tool 风险等级和确认要求。
6. 通过 `AgentToolRegistry` 选择 Tool。
7. 执行 Tool。
8. 观察 Tool 执行结果。
9. 判断返回澄清问题还是最终回复。
10. 更新会话上下文。

`AgentChatService` 现在只负责把登录用户转换成 `CurrentUser`，然后委托给 `TicketAgentOrchestrator`。

阶段九整理后，`TicketAgentOrchestrator` 只保留主流程串联职责，细节能力拆到独立组件：

```text
TicketAgentOrchestrator
  -> AgentWorkflowFallbackService
  -> ToolCallPlanValidator
  -> AgentToolExecutor
  -> AgentResponseComposer
  -> AgentContextUpdater
```

这样做的目的：

1. 区分阶段八 fallback 工作流和阶段九 Agent 编排。
2. 避免编排器承担参数抽取、风险校验、Tool 执行、上下文更新等细节。
3. 为阶段十 pending action 和上下文增强预留清晰扩展点。

### 2.1.1 AgentWorkflowFallbackService

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentWorkflowFallbackService.java
```

职责：

1. 执行规则 `IntentRouter`。
2. 调用 LLM 做阶段八意图增强。
3. 执行规则参数抽取。
4. 调用 LLM 做阶段八参数增强。
5. 构造阶段八 fallback `ToolCallPlan`。

### 2.1.2 ToolCallPlanValidator

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/ToolCallPlanValidator.java
```

职责：

1. 校验 `toolName` 是否存在。
2. 校验 Tool 是否支持当前 `intent`。
3. 校验 Tool 是否声明风险等级。
4. 判断高风险写操作是否需要用户确认。

该类只校验计划是否允许进入执行阶段，不执行 Tool。

### 2.1.3 AgentToolExecutor

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentToolExecutor.java
```

职责：

1. 根据计划构造 `AgentToolRequest`。
2. 处理需要确认的高风险操作。
3. 调用 `AgentToolRequestValidator` 做必填参数校验。
4. 调用具体 `AgentTool.execute()`。

### 2.1.4 AgentResponseComposer

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentResponseComposer.java
```

职责：

1. `NEED_MORE_INFO` 时调用 LLM 生成澄清问题。
2. `SUCCESS` 或 `FAILED` 时调用 LLM 总结结果。
3. LLM 失败时保留 Tool 原始回复。

### 2.1.5 AgentContextUpdater

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentContextUpdater.java
```

职责：

1. 更新 `lastIntent`。
2. 更新 `activeTicketId`。
3. 更新 `activeAssigneeId`。
4. 维护 `recentMessages`。

后续阶段十的 pending action 可以继续在这里扩展。

### 2.2 LlmToolCallPlan

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/llm/model/LlmToolCallPlan.java
```

这是 LLM 原始输出对象，字段包括：

```text
intent
toolName
parameters
needMoreInfo
missingFields
nextAction
confidence
reason
```

注意：它只是模型建议，不是可直接执行的业务命令。

### 2.3 ToolCallPlan

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/ToolCallPlan.java
```

这是编排层归一化后的计划。

LLM 输出需要经过代码校验和参数合并后，才会转换成该对象。

### 2.4 LlmFallbackToolCallPlan

位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/llm/model/LlmFallbackToolCallPlan.java
```

这是提供给 LLM Prompt 的 fallback 计划视图。

设计原因：

```text
llm 层不应该直接依赖 orchestration 层的 ToolCallPlan。
```

因此编排器会把内部 `ToolCallPlan` 转换成 LLM 层 DTO：

```text
ToolCallPlan
  -> LlmFallbackToolCallPlan
  -> Prompt payload
```

这样依赖方向保持为：

```text
orchestration -> llm
```

而不是：

```text
llm -> orchestration
```

## 3. 当前调用链

阶段九 `/api/agent/chat` 的主流程：

```text
AgentChatController
  -> AgentChatService
     -> TicketAgentOrchestrator
        -> AgentSessionCacheService 读取上下文
        -> IntentRouter 生成规则 fallback route
        -> LlmAgentService 尝试阶段八意图增强
        -> AgentToolParameterExtractor 生成规则 fallback parameters
        -> LlmAgentService 尝试阶段八参数增强
        -> 构造阶段八 fallback ToolCallPlan
        -> LlmAgentService 生成 LlmToolCallPlan
        -> TicketAgentOrchestrator 校验计划
        -> ToolCallPlanValidator 校验计划和风险确认
        -> AgentToolExecutor 校验参数并执行 Tool
        -> 观察 AgentToolResult
        -> AgentResponseComposer 生成澄清或总结
        -> AgentContextUpdater 更新上下文
        -> AgentSessionCacheService 保存上下文
```

阶段八能力没有被删除，而是作为阶段九 fallback。

## 4. 工具调用计划 Prompt

阶段九新增 Prompt：

```text
tool-call-plan
```

位置：

```text
AgentPromptTemplateRegistry.java
```

该 Prompt 要求模型只输出 JSON：

```json
{
  "intent": "QUERY_TICKET",
  "toolName": "queryTicket",
  "parameters": {
    "ticketId": null,
    "assigneeId": null,
    "title": null,
    "description": null,
    "category": null,
    "priority": null,
    "numbers": [],
    "missingFields": []
  },
  "needMoreInfo": false,
  "missingFields": [],
  "nextAction": "EXECUTE_TOOL",
  "confidence": 0.0,
  "reason": "一句话说明计划原因"
}
```

Prompt 明确限制：

1. 只能从 `availableTools` 中选择 `toolName`。
2. 不能直接执行工具。
3. 不能声称已经写入数据库。
4. 不能绕过 Tool 和 biz 层权限。
5. 不默认调用 RAG。
6. 参数不能编造。

## 5. 计划校验策略

LLM 计划必须经过 `TicketAgentOrchestrator` 校验。

当前校验包括：

1. `intent` 不能为空。
2. `toolName` 不能为空。
3. `toolName` 必须能在 `AgentToolRegistry` 中找到。
4. Tool 必须支持计划中的 `intent`。
5. Tool 必须声明风险等级。
6. 高风险写 Tool 如果声明 `requireConfirmation=true`，用户必须明确确认。
7. Tool 执行前必须通过 `AgentToolRequestValidator` 必填参数校验。

如果 LLM 计划不合法，系统回退到阶段八 fallback 计划。

如果阶段八 fallback 计划仍然无法通过校验，编排器不会继续进入 Tool 执行器，而是返回显式失败结果：

```text
AgentToolStatus.FAILED
toolName = agentOrchestrator
reply = 当前请求无法匹配到可安全执行的工具，请调整描述后重试。
```

这样可以避免在 Tool 为空或元数据异常时继续执行，防止空指针和不明确的失败。

## 6. 风险等级和确认

当前 Tool 风险等级：

```text
READ_ONLY
LOW_RISK_WRITE
HIGH_RISK_WRITE
```

当前策略：

```text
READ_ONLY:
  可以执行

LOW_RISK_WRITE:
  可以执行，但仍由 Tool 和 biz 层校验

HIGH_RISK_WRITE + requireConfirmation=true:
  用户消息必须包含明确确认语义，否则返回 NEED_MORE_INFO
```

当前明确确认关键词包括：

```text
确认
同意
执行
confirm
yes
```

这意味着转派这类高风险写操作不会因为 LLM 计划生成成功就直接执行。即使模型计划正确，也必须经过确认和 biz 权限校验。

## 7. 缺参处理

编排器会在执行 Tool 前调用：

```text
AgentToolRequestValidator
```

如果缺少 Tool 元数据声明的必填字段：

```text
AgentToolStatus.NEED_MORE_INFO
```

随后调用：

```text
LlmAgentService.clarifyOrFallback()
```

生成更自然的澄清问题。

缺参时不会执行 Tool，也不会写数据库。

### 7.1 校验分层说明

阶段九存在多层校验，这不是为了替代 biz 层，而是为了区分不同职责：

```text
LLM 输出解析校验：
  判断模型输出是否能解析成结构化 JSON。

LLM 参数归一化：
  判断 category、priority 等字段是否能转换成系统枚举，非法值回退 fallback。

ToolCallPlanValidator：
  判断计划是否允许进入执行阶段，包括 toolName 是否存在、Tool 是否支持 intent、风险等级和确认要求。

AgentToolExecutor 参数校验：
  在执行前统一检查 Tool requiredFields，用于提前返回 NEED_MORE_INFO，并服务后续 pending action。

具体 Tool 内部校验：
  保留防御性兜底，防止未来有人绕过编排器直接调用 Tool。

biz 层校验：
  最终业务裁决，包括权限、状态机、目标处理人是否合法、并发状态等。
```

其中真正重复的是：

```text
AgentToolExecutor requiredFields 校验
具体 Tool 内部 requiredFields 校验
```

当前选择保留两层：

- `AgentToolExecutor`：用于编排层提前澄清和后续 pending action。
- 具体 Tool：用于防御性兜底。

biz 层校验不能删除，因为 Agent 层不负责最终业务权限和状态机裁决。

## 8. Tool 执行和观察

通过校验后，编排器才会执行：

```text
AgentTool.execute()
```

Tool 内部仍然调用已有 biz service。

Tool 返回：

```text
AgentToolResult
```

编排器观察：

```text
SUCCESS
NEED_MORE_INFO
FAILED
```

当前阶段九只做单轮观察：

```text
Tool 执行一次
  -> 根据结果生成澄清或总结
  -> 结束本轮响应
```

还没有做多轮自动循环，也没有做多 Tool 连续调用。

## 9. 与阶段八的区别

阶段八：

```text
固定流程 + LLM 辅助意图识别和参数抽取
```

阶段九：

```text
单 Agent 编排器 + LLM 生成受控工具调用计划 + 代码校验和执行
```

阶段九更接近 Agent，因为它开始具备：

1. 计划生成。
2. 工具选择建议。
3. 工具执行前校验。
4. 工具结果观察。
5. 根据观察结果生成澄清或最终回复。

但它仍然是受控 Agent，不是让 LLM 自由执行。

## 10. 后续演进

后续阶段可以继续增强：

1. 阶段十：pending action 和多轮补参。
2. 阶段十：更强的上下文指代消解。
3. 阶段十一：历史工单知识构建。
4. 阶段十二：只在历史经验类场景接入 RAG。
5. 后续：多 Tool 连续调用和更完整的观察循环。

这些增强都应继续保持边界：

```text
LLM 负责规划和表达
代码负责校验和调度
Tool 负责能力封装
biz 负责最终业务裁决
```

## 11. 阶段九收口修补

进入阶段十前，阶段九做了两个收口修补：

1. 解除 LLM 层对 orchestration 层的反向依赖。
   - 新增 `LlmFallbackToolCallPlan` 作为 Prompt 输入视图。
   - `LlmAgentService` 和 `AgentPromptBuilder` 不再依赖 `ToolCallPlan`。

2. 增加 fallback 二次校验失败兜底。
   - 如果 LLM 计划不合法，会回退到阶段八 fallback 计划。
   - 如果 fallback 计划仍不合法，会返回 `FAILED`，不会进入 Tool 执行器。

这两个修补保证阶段十扩展 pending action 时，编排层和 LLM 层边界更清楚，失败路径也更明确。

## 12. 阶段九完成状态

阶段九完成后，当前 Agent 能力如下：

1. `/api/agent/chat` 对外契约保持兼容。
2. `AgentChatService` 只负责认证用户转换和委托编排器。
3. `TicketAgentOrchestrator` 成为单 Agent 编排入口。
4. 阶段八 fallback 工作流被封装到 `AgentWorkflowFallbackService`。
5. LLM 可以生成受控工具调用计划 `LlmToolCallPlan`。
6. 编排层会把 LLM 原始计划归一化为 `ToolCallPlan`。
7. 计划执行前会校验 Tool 是否存在、是否支持 intent、风险等级和确认要求。
8. Tool 执行前会统一校验必填参数。
9. 高风险写操作需要用户明确确认。
10. Tool 执行后会观察 `AgentToolResult`，并生成澄清或总结。
11. 会话上下文会更新 `lastIntent`、`activeTicketId`、`activeAssigneeId` 和 `recentMessages`。
12. LLM 层不再反向依赖 orchestration 层。
13. fallback 二次校验失败时会返回显式 `FAILED`，不会继续进入 Tool 执行器。

当前仍未实现：

1. pending action。
2. 缺参后下一轮自动恢复执行。
3. 指代消解，例如“它”“刚才那个工单”。
4. 多 Tool 连续调用。
5. 多轮自主循环。
6. RAG 检索。
7. 多 Agent。

这些内容应进入后续阶段，不放在阶段九继续扩展。

## 13. 是否可以进入阶段十

可以进入阶段十。

阶段十的目标应聚焦在：

```text
会话上下文增强
pending action
多轮补参后继续执行
简单指代消解
```

阶段十建议基于当前拆分扩展：

```text
AgentSessionContext:
  增加 pending action 结构。

AgentContextUpdater:
  负责保存、清理、更新 pending action。

TicketAgentOrchestrator:
  在读取上下文后，优先判断是否存在 pending action。

AgentToolExecutor:
  缺参时返回 awaitingFields，供 pending action 保存。
```

阶段十仍然不建议做：

1. 多 Agent。
2. MCP。
3. 默认 RAG。
4. 多 Tool 自动循环。
5. 长期记忆或复杂实体图谱。
