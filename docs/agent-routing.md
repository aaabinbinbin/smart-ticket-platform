# Agent 路由设计

## 范围

第一版可用 Agent 入口刻意保持为单路由流程：

`/api/agent/chat` -> `AgentChatService` -> `IntentRouter` -> `TicketAgentCapabilityService`

当前版本不使用多 Agent 并行编排，不引入 MCP，也不默认走 RAG 检索。工单查询、创建、转派意图直接调用工单业务服务，不依赖历史检索片段。

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

## 会话上下文

`AgentSessionContext` 通过 `AgentSessionCacheService` 写入 Redis，并设置 TTL。对话流程会更新：

- `activeTicketId`：查询到工单详情、创建工单或转派工单成功时更新。
- `activeAssigneeId`：识别到转派目标处理人时更新。
- `lastIntent`：最近一次路由得到的意图。
- `recentMessages`：最近 10 条消息及其路由意图。

## 日志

`AgentChatService` 会打印：

- 路由结果
- 能力调用前的会话上下文
- 能力调用结果
- 更新后的会话上下文

这些日志用于让第一版 Agent 行为可观测，同时避免过早引入复杂链路追踪或多 Agent 编排。
