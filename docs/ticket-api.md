# 当前 API 范围说明

本文档只描述当前仓库已经落地并可对外展示的接口范围。

## 1. 认证要求

所有工单接口和 Agent 接口都要求登录：

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

## 2. 工单主流程接口

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

### 创建工单字段

- `title`：标题，必填
- `description`：问题描述，必填
- `type`：工单类型，可选，支持 `INCIDENT`、`ACCESS_REQUEST`、`ENVIRONMENT_REQUEST`、`CONSULTATION`、`CHANGE_REQUEST`
- `typeProfile`：类型扩展字段，可选
- `category`：工单分类，可选，支持 `ACCOUNT`、`SYSTEM`、`ENVIRONMENT`、`OTHER`
- `priority`：优先级，可选，支持 `LOW`、`MEDIUM`、`HIGH`、`URGENT`
- `idempotencyKey`：幂等键，可选

### 列表筛选字段

- `status`
- `type`
- `category`
- `priority`

### 工单状态流转

```text
PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED
```

## 3. 多类型工单说明

- 未传 `type` 时默认按 `INCIDENT` 处理
- 未传 `category` 时按 `type` 兜底默认分类
- 未传 `priority` 时按 `type` 兜底默认优先级
- `typeProfile` 当前支持按类型校验

当前已支持的类型资料校验：

- `INCIDENT`：`symptom`、`impactScope`
- `ACCESS_REQUEST`：`accountId`、`targetResource`、`requestedRole`、`justification`
- `ENVIRONMENT_REQUEST`：`environmentName`、`resourceSpec`、`purpose`
- `CONSULTATION`：`questionTopic`、`expectedOutcome`
- `CHANGE_REQUEST`：`changeTarget`、`changeWindow`、`rollbackPlan`、`impactScope`

## 4. 审批接口

```http
GET /api/tickets/{ticketId}/approval
POST /api/tickets/{ticketId}/approval/submit
POST /api/tickets/{ticketId}/approval/approve
POST /api/tickets/{ticketId}/approval/reject
```

### 当前审批规则

- `ACCESS_REQUEST` 与 `CHANGE_REQUEST` 当前需要审批
- 提单人或管理员可以提交审批
- 支持模板审批与手动指定审批人
- 审批人必须具备 `STAFF` 或 `ADMIN` 角色
- 审批未通过前，不允许继续分配、认领、转派、更新状态和关闭

## 5. 摘要接口

```http
GET /api/tickets/{ticketId}/summary?view=SUBMITTER
GET /api/tickets/{ticketId}/summary?view=ASSIGNEE
GET /api/tickets/{ticketId}/summary?view=ADMIN
```

### 当前支持的摘要视角

- `SUBMITTER`：提单人进展摘要
- `ASSIGNEE`：处理人问题与最近动作摘要
- `ADMIN`：管理员风险摘要

如果未显式传 `view`，服务会根据当前用户角色和工单关系自动选择默认视角。

## 6. P1/P2 配置接口

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

## 7. Agent API

```http
POST /api/agent/chat
Content-Type: application/json
```

当前已支持：

- `QUERY_TICKET`
- `CREATE_TICKET`
- `TRANSFER_TICKET`
- `SEARCH_HISTORY`
- 创建工单缺参澄清与 `pendingAction` 草稿延续
- 低置信度澄清分支
- 摘要型查询问法，通过 `QUERY_TICKET` 链路返回工单摘要

## 8. 与 PGvector 相关的说明

- 当前代码已提供 `PGvector` 主检索路径配置
- 当 `SMART_TICKET_AI_VECTOR_STORE_ENABLED=true` 且 PGvector 可用时，历史检索优先走向量主路径
- 当主路径不可用时，`RetrievalService` 会退回 `MYSQL_FALLBACK`
- 详细演示步骤见 [docs/demo-playbook.md](/D:/aaaAgent/smart-ticket-platform/docs/demo-playbook.md)
