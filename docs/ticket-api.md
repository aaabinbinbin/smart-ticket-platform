# 工单核心业务 MVP 接口说明

## 1. 范围

本文档说明工单核心业务 MVP 接口，覆盖：

- 创建工单
- 查询工单详情
- 分页查询工单列表
- 分配工单
- 转派工单
- 更新工单状态
- 添加工单评论
- 关闭工单
- 操作日志自动记录

暂不包含：

- 复杂审批流
- SLA 自动升级
- 子任务 / 子工单
- RAG 知识构建
- Agent 自然语言入口

## 2. 认证要求

所有工单接口都需要登录。

请求头：

```http
Authorization: Bearer <accessToken>
```

`auth` 模块只负责识别当前用户和基础角色。工单业务权限由 `biz` 模块结合工单关系判断。

## 3. 状态流转

工单只允许按下面顺序流转：

```text
PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED
```

状态含义：

- `PENDING_ASSIGN`：待分配，工单已创建但没有处理人。
- `PROCESSING`：处理中，已有处理人接手。
- `RESOLVED`：已解决，处理人认为问题已处理完成。
- `CLOSED`：已关闭，提单人或管理员确认结束。

## 4. 权限规则

### 查看工单

允许：

- 管理员 `ADMIN`
- 工单提单人
- 工单当前处理人

### 分配工单

允许：

- 管理员 `ADMIN`

要求：

- 工单当前状态必须是 `PENDING_ASSIGN`
- 目标处理人必须存在、启用，并具备 `STAFF` 角色

分配后：

```text
PENDING_ASSIGN -> PROCESSING
```

### 转派工单

允许：

- 当前处理人
- 管理员 `ADMIN`

要求：

- 工单当前状态必须是 `PROCESSING`
- 目标处理人必须存在、启用，并具备 `STAFF` 角色

转派不改变工单状态，只改变当前处理人。

### 解决工单

允许：

- 当前处理人
- 管理员 `ADMIN`

要求：

- 工单当前状态必须是 `PROCESSING`

流转：

```text
PROCESSING -> RESOLVED
```

### 关闭工单

允许：

- 提单人
- 管理员 `ADMIN`

要求：

- 工单当前状态必须是 `RESOLVED`

流转：

```text
RESOLVED -> CLOSED
```

## 5. 接口列表

### 5.1 创建工单

```http
POST /api/tickets
Content-Type: application/json
```

请求体：

```json
{
  "title": "测试环境无法登录",
  "description": "测试环境登录时报 500，影响研发自测",
  "category": "SYSTEM",
  "priority": "HIGH",
  "idempotencyKey": "create-ticket-001"
}
```

说明：

- 初始状态固定为 `PENDING_ASSIGN`。
- 自动写入 `CREATE` 操作日志。

### 5.2 查询工单详情

```http
GET /api/tickets/{ticketId}
```

返回内容包括：

- 工单主信息
- 评论列表
- 操作日志列表

### 5.3 分页查询工单列表

```http
GET /api/tickets?pageNo=1&pageSize=10&status=PROCESSING&category=SYSTEM&priority=HIGH
```

查询参数：

- `pageNo`：页码，从 1 开始。
- `pageSize`：每页大小，最大 100。
- `status`：可选，工单状态。
- `category`：可选，工单分类。
- `priority`：可选，优先级。

权限范围：

- 管理员查询全部工单。
- 普通用户只查询自己创建或当前负责的工单。

### 5.4 分配工单

```http
PUT /api/tickets/{ticketId}/assign
Content-Type: application/json
```

请求体：

```json
{
  "assigneeId": 2
}
```

说明：

- 只有管理员可以调用。
- 只允许分配 `PENDING_ASSIGN` 工单。
- 自动写入 `ASSIGN` 操作日志。

### 5.5 转派工单

```http
PUT /api/tickets/{ticketId}/transfer
Content-Type: application/json
```

请求体：

```json
{
  "assigneeId": 3
}
```

说明：

- 当前负责人或管理员可以调用。
- 只允许转派 `PROCESSING` 工单。
- 自动写入 `TRANSFER` 操作日志。

### 5.6 更新工单状态

```http
PUT /api/tickets/{ticketId}/status
Content-Type: application/json
```

请求体：

```json
{
  "targetStatus": "RESOLVED",
  "solutionSummary": "重启登录服务后恢复"
}
```

说明：

- 只允许按 `PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED` 流转。
- `PROCESSING -> RESOLVED` 需要当前负责人或管理员。
- `RESOLVED -> CLOSED` 需要提单人或管理员。
- 自动写入 `UPDATE_STATUS` 操作日志。

### 5.7 添加工单评论

```http
POST /api/tickets/{ticketId}/comments
Content-Type: application/json
```

请求体：

```json
{
  "content": "已收到，正在排查登录服务日志"
}
```

说明：

- 提单人、当前负责人或管理员可以评论。
- 已关闭工单不能继续评论。
- 自动写入 `COMMENT` 操作日志。

### 5.8 关闭工单

```http
PUT /api/tickets/{ticketId}/close
```

说明：

- 提单人或管理员可以关闭。
- 只允许关闭 `RESOLVED` 工单。
- 自动写入 `CLOSE` 操作日志。

## 6. 操作日志

关键操作会自动写入 `ticket_operation_log`：

- `ticket_id`
- `operator_id`
- `operation_type`
- `operation_desc`
- `before_value`
- `after_value`

当前 MVP 使用文本快照记录 before / after，后续可以替换为结构化 JSON。

## 7. 常见错误

未登录：

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "请先登录或提供有效令牌"
}
```

无业务权限：

```json
{
  "success": false,
  "code": "TICKET_FORBIDDEN",
  "message": "当前用户无权查看该工单"
}
```

状态流转不合法：

```json
{
  "success": false,
  "code": "INVALID_TICKET_STATUS_TRANSITION",
  "message": "状态流转不合法，只允许 PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED"
}
```
