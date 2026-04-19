# 阶段八：LLM 接入与 Prompt 层设计

## 1. 阶段目标

阶段八的目标是给 `smart-ticket-agent` 增加 LLM 理解能力，但不改变当前受控执行边界。

本阶段 LLM 只负责：

1. 意图识别
2. 参数抽取
3. 缺参澄清问题生成
4. Tool 调用结果总结

LLM 不负责：

1. 直接写数据库
2. 直接调用 `TicketService`
3. 绕过 `AgentToolRegistry`
4. 绕过 Tool 参数校验
5. 决定最终业务权限
6. 默认触发 RAG 检索
7. 多 Agent 编排

也就是说，阶段八仍然是“LLM 辅助工作流”，还不是完整的 Agent 编排层。

## 2. 阶段八的准确定位

阶段八接入了 LLM，但当前系统仍然是固定工作流，不是完整 Agent。

当前由代码固定控制主流程：

```text
规则路由兜底
  -> LLM 辅助识别意图
  -> 规则参数兜底
  -> LLM 辅助抽取参数
  -> 代码根据 intent 选择 Tool
  -> Tool 校验参数
  -> Tool 调用 biz 层
  -> LLM 辅助生成澄清或总结
```

LLM 当前没有承担以下职责：

1. 自主规划完整执行步骤。
2. 主动选择一个或多个 Tool。
3. 读取工具执行结果后继续决定下一步。
4. 形成“计划 -> 行动 -> 观察 -> 再决策”的循环。
5. 维护 pending action 并在多轮补参后继续执行。
6. 根据环境状态动态调整后续安排。

因此，阶段八更准确的名称是：

```text
LLM 增强的 Agent 工作流
```

而不是：

```text
单 Agent 编排
```

阶段八的价值是把自然语言理解能力接入现有受控链路，为阶段九的单 Agent 编排准备 LLM、Prompt、结构化输出和降级能力。

## 3. 当前调用链

当前 `/api/agent/chat` 的核心链路如下：

```text
AgentChatController
  -> AgentChatService
     -> IntentRouter 生成规则兜底路由
     -> LlmAgentService 尝试生成 LLM 路由
     -> AgentToolParameterExtractor 生成规则兜底参数
     -> LlmAgentService 尝试生成 LLM 参数
     -> AgentToolRegistry 按 intent 选择 Tool
     -> AgentTool.execute()
     -> LlmAgentService 生成澄清问题或结果总结
     -> AgentSessionCacheService 保存会话上下文
```

关键点：

- 规则路由和规则参数始终先生成，作为 LLM 失败时的 fallback。
- LLM 输出必须经过代码校验，不能直接进入 Tool。
- Tool 执行仍然走 `AgentToolRegistry`。
- 工单业务执行仍然走 `smart-ticket-biz`。

## 4. LLM 客户端接入方式

当前没有直接引入 Spring AI，而是先定义轻量抽象：

```text
llm/client/LlmClient.java
llm/client/OpenAiCompatibleLlmClient.java
```

这样做的原因：

1. 阶段八只需要最小可用的 Chat Completion 能力。
2. 可以兼容 OpenAI 风格网关、OneAPI、私有模型网关等。
3. 后续切换 Spring AI 时，只需要新增一个 `LlmClient` 实现，不影响 Agent 主链路。

当前配置读取：

```text
smart-ticket.agent.llm.base-url
smart-ticket.agent.llm.api-key
smart-ticket.agent.llm.model
smart-ticket.agent.llm.timeout-ms
smart-ticket.agent.llm.temperature
smart-ticket.agent.llm.max-tokens
```

本地默认兼容环境变量：

```text
MY_BASE_URL
MY_API_KEY
AGENT_LLM_MODEL
```

如果没有设置 `AGENT_LLM_MODEL`，默认模型名为：

```text
gpt-4o-mini
```

如果 `apiKey`、`baseUrl` 或 `model` 不完整，`LlmClient.isAvailable()` 会返回 `false`，主链路自动使用规则 fallback。

## 5. Prompt 模板

Prompt 模板集中在：

```text
llm/prompt/AgentPromptTemplateRegistry.java
```

当前包含四类模板。

### 5.1 intent-classification

职责：

- 判断用户消息属于哪个 Agent 意图。
- 只允许输出四个意图之一：
  - `QUERY_TICKET`
  - `CREATE_TICKET`
  - `TRANSFER_TICKET`
  - `SEARCH_HISTORY`

输出对象：

```text
LlmIntentDecision
```

输出 JSON 示例：

```json
{
  "intent": "QUERY_TICKET",
  "confidence": 0.86,
  "reason": "用户在查询工单状态"
}
```

代码校验：

- `intent` 必须能映射为 `AgentIntent`。
- `confidence` 必须存在，并被限制在 `0.0 - 1.0`。
- 置信度低于阈值时使用规则路由 fallback。

### 5.2 ticket-parameter-extraction

职责：

- 从用户原文和会话上下文中抽取结构化参数。
- 不编造不存在的信息。
- 不判断业务权限。

输出对象：

```text
LlmParameterExtractionResult
```

支持字段：

```text
ticketId
assigneeId
title
description
category
priority
numbers
missingFields
```

代码校验：

- `category` 只能映射为 `ACCOUNT / SYSTEM / ENVIRONMENT / OTHER`。
- `priority` 只能映射为 `LOW / MEDIUM / HIGH / URGENT`。
- 非法枚举值会被丢弃，并回退到规则抽取结果。
- LLM 未抽取到的字段使用规则抽取结果补齐。
- 最终仍由 Tool 的 `requiredFields` 和 `AgentToolRequestValidator` 做必填校验。

### 5.3 clarification-question

职责：

- 当 Tool 返回 `NEED_MORE_INFO` 时，基于缺失字段生成一句自然的中文追问。
- 不承诺已经执行业务操作。

输出对象：

```text
LlmClarificationResult
```

输出 JSON 示例：

```json
{
  "question": "请补充要转派的工单 ID 和目标处理人 ID。",
  "missingFields": ["ticketId", "assigneeId"]
}
```

代码校验：

- 只使用 `question` 作为回复文本。
- 如果 LLM 失败或问题为空，使用 Tool 原始缺参回复。

### 5.4 response-summary

职责：

- 根据 Tool 执行状态和结果生成简短中文回复。
- 只能总结 Tool 已经返回的事实。

输出对象：

```text
LlmResponseSummary
```

输出 JSON 示例：

```json
{
  "reply": "已创建工单，当前状态为待分配。"
}
```

代码校验：

- 只读取 `reply` 字段。
- 如果 LLM 失败或回复为空，使用 Tool 原始回复。
- `NEED_MORE_INFO` 不走结果总结，而是走缺参澄清。

## 6. 结构化输出对象

阶段八新增四个 LLM 输出对象：

```text
llm/model/LlmIntentDecision.java
llm/model/LlmParameterExtractionResult.java
llm/model/LlmClarificationResult.java
llm/model/LlmResponseSummary.java
```

这些对象只代表 LLM 的“建议结果”，不是最终业务事实。

最终是否执行 Tool，由代码决定：

```text
LLM 输出
  -> JSON 解析
  -> 枚举和字段校验
  -> fallback 合并
  -> Tool requiredFields 校验
  -> Tool execute
  -> biz 层权限和状态校验
```

## 7. 失败降级策略

阶段八采用分段降级，不因为 LLM 失败影响基础 Agent 能力。

### 7.1 LLM 未配置

如果没有配置 `MY_API_KEY`、`MY_BASE_URL` 或模型名：

```text
LlmClient.isAvailable() = false
```

系统直接使用：

```text
IntentRouter + AgentToolParameterExtractor + AgentToolRegistry
```

### 7.2 LLM 调用失败

包括：

- 网络超时
- API key 错误
- baseUrl 错误
- 模型名不可用
- 模型服务返回异常

处理方式：

- 记录 warn 日志。
- 当前步骤回退到规则结果。
- 不中断整个 `/api/agent/chat` 请求。

### 7.3 LLM 输出不是合法 JSON

处理方式：

- `LlmJsonParser` 尝试截取 JSON 对象。
- 仍无法解析时，回退到规则结果或 Tool 原始回复。

### 7.4 LLM 输出字段非法

处理方式：

- 非法意图：回退规则路由。
- 低置信度意图：回退规则路由。
- 非法分类：回退规则分类。
- 非法优先级：回退规则优先级。
- 缺失必填字段：继续交给 Tool 校验，返回 `NEED_MORE_INFO`。

## 8. 安全边界

阶段八必须遵守以下边界：

1. LLM 不能直接调用数据库。
2. LLM 不能直接调用 `TicketService`。
3. LLM 不能决定用户是否有权限转派、关闭或修改工单。
4. LLM 不能绕过 Tool 元数据和必填参数校验。
5. LLM 不能把 RAG 结果当成当前事实。
6. LLM 总结回复时不能添加 Tool 结果里不存在的事实。

写操作的最终保护仍然在：

```text
Tool 参数校验
  -> biz service 权限校验
  -> biz service 状态机校验
  -> 操作日志
```

## 9. 与后续阶段的关系

阶段八不是多 Agent，也不是完整 Tool Calling。

后续阶段建议：

1. 阶段九：抽出 `TicketAgentOrchestrator`，形成单 Agent 编排服务。
2. 阶段十：增强会话上下文和 pending action，支持多轮补参后继续执行。
3. 阶段十一：构建历史工单知识。
4. 阶段十二：只在历史经验类场景接入 RAG。

阶段八完成后，系统已经具备“LLM 理解 + 代码受控执行”的基础，但还没有让 LLM 进行多轮工具规划。

## 10. 是否可以进入阶段九

当前可以进入阶段九。

进入阶段九的前置条件已经满足：

1. `/api/agent/chat` 已经有稳定入口。
2. 四个基础意图已经存在。
3. Tool 层已经标准化，并且有 `AgentToolRegistry`。
4. LLM 客户端、Prompt、结构化输出对象已经具备。
5. LLM 失败时可以回退到规则路由和规则参数抽取。
6. Tool 执行前仍然有代码校验和 Tool 必填参数校验。
7. 写操作仍然通过 biz 层执行。

阶段九的重点不是继续堆 Prompt，而是抽出一个单 Agent 编排服务，例如：

```text
TicketAgentOrchestrator
```

阶段九应当让 LLM 开始生成受控的工具调用计划，但仍然由代码做最终校验：

```text
用户输入
  -> 读取上下文
  -> LLM 生成工具调用计划
  -> 代码校验 toolName / intent / 参数 / 风险等级
  -> Tool 执行
  -> 观察 Tool 结果
  -> LLM 决定澄清、继续或最终回复
```

阶段九仍然不要做：

1. 多 Agent 并行。
2. MCP。
3. 默认 RAG。
4. 让 LLM 直接写数据库。
5. 让 LLM 决定最终业务权限。
# 2026-04-19 Spring AI Migration Note

This historical stage document describes the pre-Spring-AI LLM adapter design.
`OpenAiCompatibleLlmClient` has been removed in the Spring AI baseline integration.
The current model adapter is `SpringAiLlmClient -> Spring AI ChatClient`.
The `LlmClient` abstraction, prompt construction, JSON parsing, Tool Guard, and biz boundary still remain.
