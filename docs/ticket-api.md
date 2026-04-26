# 接口说明

大而全的 API 列表看起来很无聊。这篇换个方式：从一个用户的实际操作出发，看看每一步调什么接口。

---

## 1. 先登录

```http
POST /api/auth/login
Content-Type: application/json

{"username":"user1","password":"123456"}
```

返回 `accessToken`，后面所有请求都带这个：

```http
Authorization: Bearer <accessToken>
```

---

## 2. 建一张工单

```http
POST /api/tickets
Content-Type: application/json
Idempotency-Key: my-unique-key-001

{
  "title": "测试环境登录报 500",
  "description": "登录时接口返回 500，影响研发自测"
}
```

**只需要标题和描述**。系统会自动推断工单类型（INCIDENT）、分类（SYSTEM）、优先级（MEDIUM），并补全故障现象和影响范围。

如果你传了 type/category/priority，就尊重你的选择，不覆盖。

`Idempotency-Key` 是**必填**的请求头。用同一个 key 重复请求，不会创建重复工单，直接返回第一次的结果。这解决了"网络超时客户端重试导致重复建单"的问题。

---

## 3. 查工单

```http
GET /api/tickets/1001                     # 详情
GET /api/tickets?pageNo=1&pageSize=10      # 列表
GET /api/tickets?status=PROCESSING&type=INCIDENT  # 筛选
```

列表筛选参数：`status`、`type`、`category`、`priority`。都是可选的。

---

## 4. 分派 → 处理 → 关闭

```http
PUT /api/tickets/1001/assign     ← {"assigneeId":2}   # 管理员分派给 staff1
PUT /api/tickets/1001/claim                               # staff1 自己认领
PUT /api/tickets/1001/transfer   ← {"assigneeId":3}   # 转给别人
PUT /api/tickets/1001/status     ← {"targetStatus":"RESOLVED","solutionSummary":"..."}  # 标记解决
PUT /api/tickets/1001/close                               # 提单人或管理员关闭
```

状态只能按规定流转：`PENDING_ASSIGN → PROCESSING → RESOLVED → CLOSED`。不能跳，不能往回走。审批型工单在审批通过前不能推进。

---

## 5. 评论

```http
POST /api/tickets/1001/comments
{"content":"已检查网关日志，确认是数据库连接池耗尽导致"}
```

---

## 6. 审批

ACCESS_REQUEST 和 CHANGE_REQUEST 需要审批：

```http
POST /api/tickets/1001/approval/submit  ← {"templateId":1, "submitComment":"申请测试环境只读权限"}
POST /api/tickets/1001/approval/approve ← {"decisionComment":"同意"}
POST /api/tickets/1001/approval/reject  ← {"decisionComment":"请补充申请理由"}
```

审批未通过，工单不能分配、认领、转派、推进状态、关闭。

---

## 7. 摘要

同一张工单，不同角色看到不同的重点：

```http
GET /api/tickets/1001/summary?view=SUBMITTER  # 提单人：进度、处理人、下一步
GET /api/tickets/1001/summary?view=ASSIGNEE   # 处理人：问题现象、最近动作
GET /api/tickets/1001/summary?view=ADMIN      # 管理员：风险、阻塞点
```

---

## 8. SLA

```http
GET /api/tickets/1001/sla                                    # 查这张单的 SLA 状态
POST /api/ticket-sla-instances/breach-scan?limit=100         # 手动扫描违约
```

系统每分钟自动扫描一次 SLA 违约，违约后会自动升级优先级 + 通知相关人员。

---

## 9. Agent 对话

```http
POST /api/agent/chat
Content-Type: application/json
Authorization: Bearer <token>

{"message":"登录失败，帮我建个工单","sessionId":"session-001"}
```

Agent 支持四个意图：
- `QUERY_TICKET` — "帮我查下 1001"、"我有哪些待处理的工单"
- `CREATE_TICKET` — "登录报 500，帮我建工单"
- `TRANSFER_TICKET` — "把 1001 转给张三"（高风险，需二次确认）
- `SEARCH_HISTORY` — "以前有没有类似的登录失败问题"

SSE 流式接口：

```http
POST /api/agent/chat/stream
```

事件顺序：`accepted → route → status → final → done`

---

## 10. 管理配置

工单组、队列、SLA 策略、分派规则、审批模板都有完整的 CRUD 接口：

```http
# 工单组
POST /api/ticket-groups
GET  /api/ticket-groups?pageNo=1&pageSize=10

# 队列成员
POST /api/ticket-queues/1/members  ← {"userId":2}

# SLA 策略
POST /api/ticket-sla-policies
GET  /api/ticket-sla-policies?pageNo=1&pageSize=10

# 分派规则
POST /api/ticket-assignment-rules
POST /api/tickets/1001/auto-assign   # 手动触发自动分派

# 审批模板
POST /api/ticket-approval-templates
```

---

## 11. RAG 直接检索

绕过 Agent，直接搜知识库：

```http
GET /api/rag/search?query=登录失败&topK=5
```

返回 `retrievalPath`（PGVECTOR 或 MYSQL_FALLBACK）、`hits`（命中列表）。适合验证向量库是否正常工作。

---

## 12. 管理仪表盘

```http
GET /api/admin/dashboard
```

返回工单数量分布、RAG 知识统计、Agent 调用指标。需 ADMIN 角色。

---

## 工单类型怎么工作

系统支持 5 种工单类型，每种有不同的必填字段：

| 类型 | 必填字段 | 适用场景 |
|------|---------|---------|
| INCIDENT | symptom, impactScope | 报故障：登录失败、接口报错 |
| ACCESS_REQUEST | accountId, targetResource, requestedRole, justification | 申权限：新增账号、申请角色 |
| CHANGE_REQUEST | changeTarget, changeWindow, rollbackPlan, impactScope | 变更：发布、配置修改、数据库变更 |
| ENVIRONMENT_REQUEST | environmentName, resourceSpec, purpose | 环境申请：测试环境、容器资源 |
| CONSULTATION | questionTopic, expectedOutcome | 咨询：技术问题、流程咨询 |

用户不需要手动填这些——`TicketCreateEnrichmentService` 会根据标题和描述自动推断并补全。
