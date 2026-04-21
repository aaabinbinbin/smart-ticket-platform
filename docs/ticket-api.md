# 工单与 Agent API 说明

## 1. 范围

本文档覆盖当前仓库已经开放的接口：
- 工单核心业务接口
- P1 配置接口
- Agent 对话入口

## 2. 认证要求

所有工单接口和 Agent 接口都需要登录。

```http
Authorization: Bearer <accessToken>
```

## 3. 工单状态流转

```text
PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED
```

## 4. 工单核心接口

```http
POST /api/tickets
GET /api/tickets/{ticketId}
GET /api/tickets?pageNo=1&pageSize=10
PUT /api/tickets/{ticketId}/assign
PUT /api/tickets/{ticketId}/transfer
PUT /api/tickets/{ticketId}/status
POST /api/tickets/{ticketId}/comments
PUT /api/tickets/{ticketId}/close
PUT /api/tickets/{ticketId}/queue
```

## 5. P1 配置接口

### 工单组
```http
POST /api/ticket-groups
PUT /api/ticket-groups/{groupId}
PATCH /api/ticket-groups/{groupId}/enabled
GET /api/ticket-groups/{groupId}
GET /api/ticket-groups?pageNo=1&pageSize=10
```

### 队列
```http
POST /api/ticket-queues
PUT /api/ticket-queues/{queueId}
PATCH /api/ticket-queues/{queueId}/enabled
GET /api/ticket-queues/{queueId}
GET /api/ticket-queues?pageNo=1&pageSize=10
```

### 队列成员
```http
POST /api/ticket-queues/{queueId}/members
PATCH /api/ticket-queues/{queueId}/members/{memberId}/enabled
GET /api/ticket-queues/{queueId}/members
```

### SLA
```http
POST /api/ticket-sla-policies
PUT /api/ticket-sla-policies/{policyId}
PATCH /api/ticket-sla-policies/{policyId}/enabled
GET /api/ticket-sla-policies/{policyId}
GET /api/ticket-sla-policies?pageNo=1&pageSize=10
GET /api/tickets/{ticketId}/sla
POST /api/ticket-sla-instances/breach-scan?limit=100
```

当前 SLA 扫描行为：
- 扫描首次响应违约和解决时限违约
- 将命中工单优先级提升到 `URGENT`
- 首次响应违约时可自动接管到管理员
- 写入 `SLA_BREACH`、`SLA_ESCALATE` 操作日志
- 返回 `markedCount`、`firstResponseBreachedCount`、`resolveBreachedCount`、`escalatedCount`、`notifiedCount`

### 自动分派规则
```http
POST /api/ticket-assignment-rules
PUT /api/ticket-assignment-rules/{ruleId}
PATCH /api/ticket-assignment-rules/{ruleId}/enabled
GET /api/ticket-assignment-rules/{ruleId}
GET /api/ticket-assignment-rules?pageNo=1&pageSize=10
GET /api/tickets/{ticketId}/assignment-preview
POST /api/tickets/{ticketId}/auto-assign
```

当前自动分派行为：
- 支持指定处理人
- 支持指定队列时按队列成员最小负载分派
- 支持指定工单组时跨启用队列最小负载分派
- 无可用成员时回退到组负责人
- 仍无人可分派时仅绑定组/队列，工单保留 `PENDING_ASSIGN`

## 6. Agent API

```http
POST /api/agent/chat
Content-Type: application/json
```

当前支持：
- `QUERY_TICKET`
- `CREATE_TICKET`
- `TRANSFER_TICKET`
- `SEARCH_HISTORY`
- CREATE_TICKET 多轮补参与 `pendingAction` 草稿续接
- 低置信度请求先澄清，不强行硬路由

## 7. RAG 边界

当前 RAG 已支持：
- query rewrite
- 轻量 rerank
- 历史案例检索
- MySQL fallback 与 PGvector 主路径预留
- retrieval path / fallback 日志

## 8. 常见错误

未登录：

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "请先登录或提供有效令牌"
}
```