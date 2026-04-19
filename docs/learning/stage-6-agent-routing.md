# Agent 路由设计

## 阶段定位

当前阶段六完成的是“规则路由版 Agent 对话入口”，也可以理解为 Agent-ready workflow：

```text
用户消息
  -> /api/agent/chat
  -> AgentChatService
  -> IntentRouter
  -> TicketAgentCapabilityService
  -> TicketService / AgentSessionContext
```

它的目标是先打通自然语言入口、意图路由、业务能力调用、会话上下文和日志观测这条最小闭环，为后续 Tool 调用、LLM 接入和 RAG 检索预留结构。

当前实现不等同于完整 LLM Agent。它还没有接入大模型，没有 Prompt 模板，没有模型驱动的 Tool Calling，也没有复杂多 Agent 编排。

## 已完成范围

阶段六当前已经完成：

- 提供 `POST /api/agent/chat` 对话入口。
- 支持 `sessionId + message` 输入。
- 实现 `IntentRouter`。
- 第一版只识别 4 个意图：`QUERY_TICKET`、`CREATE_TICKET`、`TRANSFER_TICKET`、`SEARCH_HISTORY`。
- 使用单 Router + 简单能力服务完成调用分发。
- 通过 `AgentSessionContext` 保存当前会话上下文。
- 打印路由结果、调用前上下文、能力调用结果、调用后上下文。
- 工单查询、创建、转派都通过 `TicketService` 进入 `biz` 层，不绕过业务规则。

## 明确不做

阶段六当前明确不做：

- 不接入 LLM。
- 不使用 MCP。
- 不做 Supervisor 或多 Agent 并行编排。
- 不默认所有请求先走 RAG。
- 不让 `QUERY_TICKET`、`CREATE_TICKET`、`TRANSFER_TICKET` 依赖历史检索片段。
- 不把 Agent 的判断当作最终业务事实，最终事实仍以 `biz` 返回结果和数据库状态为准。

## 接口

`POST /api/agent/chat`

请求：

```json
{
  "sessionId": "web-001",
  "message": "查询工单 12"
}
```

响应 `data` 包含：

- `sessionId`：当前对话会话。
- `intent`：取值为 `QUERY_TICKET`、`CREATE_TICKET`、`TRANSFER_TICKET`、`SEARCH_HISTORY` 之一。
- `route`：路由决策结果，包含置信度和原因。
- `context`：本轮处理后保存的会话上下文。
- `result`：能力调用的原始结果。
- `reply`：面向用户的简短回复。

## 意图

第一版只识别四个意图：

- `QUERY_TICKET`：消息中包含 ID 时查询工单详情，否则查询当前用户可见工单第一页。如果消息没有 ID 但会话里有 `activeTicketId`，则查询该工单。
- `CREATE_TICKET`：根据消息创建工单。第一版用简单规则提取标题、分类和优先级，然后调用 `TicketService.createTicket`。
- `TRANSFER_TICKET`：把工单转派给另一个处理人。消息中有两个数字时，第一个数字视为工单 ID，第二个数字视为目标处理人 ID；只有一个数字且会话中存在 `activeTicketId` 时，该数字视为目标处理人 ID。
- `SEARCH_HISTORY`：只返回当前 Agent 会话上下文中保存的近期消息。

## 路由规则

`IntentRouter` 采用确定性的关键词规则：

- 命中 `历史`、`刚才`、`之前`、`history` 等历史关键词时，路由到 `SEARCH_HISTORY`。
- 命中 `转派`、`转交`、`转给`、`transfer` 等转派关键词时，路由到 `TRANSFER_TICKET`。
- 命中 `创建`、`新建`、`提交`、`报修`、`create` 等创建关键词时，路由到 `CREATE_TICKET`。
- 命中 `查询`、`查看`、`详情`、`状态`、`工单`、`ticket` 等查询关键词时，路由到 `QUERY_TICKET`。
- 如果没有关键词命中，低置信度兜底到 `QUERY_TICKET`。

关键词顺序是有意设计的：历史和转派先于通用工单查询词判断，避免 `查询刚才的记录`、`工单 12 转派给 3` 这类消息被查询分支吞掉。

## 能力调用

`TicketAgentCapabilityService` 当前承载第一版能力分发：

- `QUERY_TICKET`：优先从消息中提取工单 ID；没有 ID 时复用 `activeTicketId`；仍没有 ID 时查询当前用户可见工单第一页。
- `CREATE_TICKET`：使用规则生成标题、分类、优先级和描述，调用 `TicketService.createTicket`。
- `TRANSFER_TICKET`：从消息中提取工单 ID 和目标处理人 ID；缺少工单 ID 时尝试复用 `activeTicketId`；缺少必要参数时返回补充信息提示。
- `SEARCH_HISTORY`：返回当前会话上下文中的 `recentMessages`，不访问 RAG。

所有改变工单事实状态的能力都必须调用 `biz` 层服务。Agent 只负责理解、路由、参数整理和结果包装。

## 会话上下文

`AgentSessionContext` 通过 `AgentSessionCacheService` 写入 Redis，并设置 TTL。对话流程会更新：

- `activeTicketId`：查询到工单详情、创建工单或转派工单成功时更新。
- `activeAssigneeId`：识别到转派目标处理人时更新。
- `lastIntent`：最近一次路由得到的意图。
- `recentMessages`：最近 10 条消息及其路由意图。

当前会话上下文只承担轻量记忆，不作为工单事实来源。工单事实仍来自 MySQL 和 `TicketService` 返回结果。

## 日志

`AgentChatService` 会打印：

- 路由结果。
- 能力调用前的会话上下文。
- 能力调用结果。
- 更新后的会话上下文。

这些日志用于让第一版 Agent 入口行为可观测，同时避免过早引入复杂链路追踪或多 Agent 编排。

## 与阶段七的关系

阶段六已经具备进入阶段七的前置条件：入口、意图、上下文和能力调用闭环已经存在。

阶段七建议在当前基础上推进 Tool 调用落地：

- 定义统一 `AgentTool` 接口。
- 将当前能力拆成 `QueryTicketTool`、`CreateTicketTool`、`TransferTicketTool`、`SearchHistoryTool`。
- 增加结构化参数对象，减少 Tool 内部直接解析原始 message。
- 增加 `SUCCESS`、`NEED_MORE_INFO`、`FAILED` 等能力调用状态。
- 保留当前规则路由作为 fallback，再考虑 LLM 意图识别和参数抽取。
