# API 说明

本文档描述当前仓库已经落地、可对外演示的接口范围，重点覆盖认证、工单主流程、SLA、审批、自动分派、通知和 Agent 能力。

## 1. 认证要求

除登录接口外，其余业务接口都要求携带：

```http
Authorization: Bearer <accessToken>
```

未登录时统一返回：

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "请先登录或提供有效令牌"
}
```

## 2. 认证接口

```http
POST /api/auth/login
```

请求体：

```json
{
  "username": "user1",
  "password": "123456"
}
```

## 3. 工单主流程接口

```http
POST /api/tickets
GET /api/tickets/{ticketId}
GET /api/tickets?pageNo=1&pageSize=10
GET /api/tickets/{ticketId}/summary
PUT /api/tickets/{ticketId}/assign
PUT /api/tickets/{ticketId}/claim
PUT /api/tickets/{ticketId}/transfer
PUT /api/tickets/{ticketId}/status
PUT /api/tickets/{ticketId}/queue
POST /api/tickets/{ticketId}/comments
PUT /api/tickets/{ticketId}/close
```

创建工单主要字段：

- `title`：标题，必填
- `description`：问题描述，必填
- `type`：工单类型，可选，支持 `INCIDENT`、`ACCESS_REQUEST`、`ENVIRONMENT_REQUEST`、`CONSULTATION`、`CHANGE_REQUEST`
- `typeProfile`：类型扩展字段，可选
- `category`：工单分类，可选，支持 `ACCOUNT`、`SYSTEM`、`ENVIRONMENT`、`OTHER`
- `priority`：优先级，可选，支持 `LOW`、`MEDIUM`、`HIGH`、`URGENT`
- `idempotencyKey`：幂等键，可选

列表筛选字段：

- `status`
- `type`
- `category`
- `priority`

当前工单状态流转：

```text
PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED
```

## 4. 多类型工单说明

- 未传 `type` 时默认按 `INCIDENT` 处理
- 未传 `category` 时按 `type` 兜底默认分类
- 未传 `priority` 时按 `type` 兜底默认优先级
- `typeProfile` 按工单类型做结构化字段校验

当前已支持的类型扩展字段：

- `INCIDENT`：`symptom`、`impactScope`
- `ACCESS_REQUEST`：`accountId`、`targetResource`、`requestedRole`、`justification`
- `ENVIRONMENT_REQUEST`：`environmentName`、`resourceSpec`、`purpose`
- `CONSULTATION`：`questionTopic`、`expectedOutcome`
- `CHANGE_REQUEST`：`changeTarget`、`changeWindow`、`rollbackPlan`、`impactScope`

## 5. 审批接口

```http
GET /api/tickets/{ticketId}/approval
POST /api/tickets/{ticketId}/approval/submit
POST /api/tickets/{ticketId}/approval/approve
POST /api/tickets/{ticketId}/approval/reject
```

当前审批规则：

- `ACCESS_REQUEST` 和 `CHANGE_REQUEST` 当前需要审批
- 提单人或管理员可以提交审批
- 支持审批模板和手动指定审批人
- 审批人必须具备 `STAFF` 或 `ADMIN` 角色
- 审批未通过前，不允许继续分配、认领、转派、更新状态和关闭

## 6. 摘要接口

```http
GET /api/tickets/{ticketId}/summary?view=SUBMITTER
GET /api/tickets/{ticketId}/summary?view=ASSIGNEE
GET /api/tickets/{ticketId}/summary?view=ADMIN
```

支持的摘要视角：

- `SUBMITTER`：提单人进展摘要
- `ASSIGNEE`：处理人问题与最近动作摘要
- `ADMIN`：管理员风险摘要

## 7. SLA 与自动分派配置接口

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

### 自动分派规则

```http
POST /api/ticket-assignment-rules
PUT /api/ticket-assignment-rules/{ruleId}
PATCH /api/ticket-assignment-rules/{ruleId}/enabled
GET /api/ticket-assignment-rules/{ruleId}
GET /api/ticket-assignment-rules?pageNo=1&pageSize=10
GET /api/ticket-assignment-rules/stats
POST /api/tickets/{ticketId}/assignment-preview
POST /api/tickets/{ticketId}/auto-assign
```

### 审批模板

```http
POST /api/ticket-approval-templates
PUT /api/ticket-approval-templates/{templateId}
PATCH /api/ticket-approval-templates/{templateId}/enabled
GET /api/ticket-approval-templates/{templateId}
GET /api/ticket-approval-templates?pageNo=1&pageSize=10
```

## 8. 站内通知接口

当前通知能力主要用于 SLA 违约闭环。SLA 扫描发现违约后，会给工单创建人和当前处理人写入站内通知。

```http
GET /api/notifications?pageNo=1&pageSize=10&unreadOnly=true
PATCH /api/notifications/{notificationId}/read
```

通知列表返回字段示例：

```json
{
  "id": 501,
  "ticketId": 1001,
  "receiverUserId": 1,
  "channel": "IN_APP",
  "notificationType": "SLA_BREACH",
  "title": "工单 INC202604230001 首次响应超时",
  "content": "工单【登录失败】发生 SLA 违约。违约类型：首次响应超时。系统已执行升级处理。",
  "read": false,
  "readAt": null,
  "createdAt": "2026-04-23T14:30:00",
  "updatedAt": "2026-04-23T14:30:00"
}
```

## 9. Agent 接口

```http
POST /api/agent/chat
Content-Type: application/json
```

当前已支持：

- `QUERY_TICKET`
- `CREATE_TICKET`
- `TRANSFER_TICKET`
- `SEARCH_HISTORY`
- 创建工单缺参澄清与 `pendingAction` 草稿续写
- 低置信度意图澄清
- 摘要型问法通过 `QUERY_TICKET` 链路返回工单摘要

## 10. RAG / PGvector 说明

- 当前默认可稳定运行的是 MySQL fallback 检索链路
- 代码中已保留 PGvector 主链接入方向，但默认启动不会启用 PGvector 自动装配
- 历史检索实际路径可通过日志观察 `MYSQL_FALLBACK` 或后续扩展的向量主链
- 演示顺序可参考 [demo-playbook.md](/D:/aaaAgent/smart-ticket-platform/docs/demo-playbook.md)
