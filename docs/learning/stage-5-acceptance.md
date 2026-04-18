# 阶段 5 本地验收流程

本文档用于在进入阶段 6 之前，手动验证当前后端工单业务闭环是否真实可用。

验收目标不是测试 Agent，而是确认：

```text
登录 -> 创建工单 -> 分配 -> 评论 -> 解决 -> 关闭 -> 查询详情 -> 检查操作日志
```

这条主链路可以通过 HTTP 接口跑通。

## 1. 环境要求

本地需要：

```text
JDK 17
Maven
MySQL 127.0.0.1:3306
Redis 127.0.0.1:6379
Apifox
```

当前项目默认配置：

```text
MySQL:
  host: 127.0.0.1
  port: 3306
  database: smart_ticket_platform
  username: root
  password: 123456

Redis:
  host: 127.0.0.1
  port: 6379
  password: none
```

配置文件：

```text
smart-ticket-app/src/main/resources/application.yml
```

## 2. 初始化数据库

先执行建表脚本：

```bash
mysql -h127.0.0.1 -uroot -p123456 < docs/sql/schema.sql
```

再执行初始化数据：

```bash
mysql -h127.0.0.1 -uroot -p123456 < docs/sql/seed.sql
```

初始化后会有三个演示账号：

```text
user1 / 123456   -> USER
staff1 / 123456  -> USER + STAFF
admin1 / 123456  -> USER + STAFF + ADMIN
```

## 3. 启动 Redis

确认 Redis 运行在：

```text
127.0.0.1:6379
```

当前没有配置 Redis 密码。

如果 Redis 没启动：

```text
详情缓存会降级为查数据库
创建工单幂等会不可用
应用启动可能因为 Redis 连接配置可用但服务不可达而在首次访问 Redis 时报错
```

因此阶段 5 验收建议启动 Redis。

## 4. 启动应用

先构建：

```bash
mvn clean package
```

再启动：

```bash
mvn -pl smart-ticket-app spring-boot:run
```

启动成功后，基础地址为：

```text
http://localhost:8080
```

## 5. 导入 Apifox

导入文件：

```text
docs/apifox/auth-rbac.openapi.yaml
docs/apifox/stage-4-ticket-core-mvp.openapi.yaml
```

先用 `auth-rbac.openapi.yaml` 登录获取 token，再用 `stage-4-ticket-core-mvp.openapi.yaml` 调工单接口。

建议在 Apifox 环境中配置变量：

```text
baseUrl = http://localhost:8080
userToken =
staffToken =
adminToken =
ticketId =
```

接口请求头统一使用：

```http
Authorization: Bearer {{token}}
```

其中 `{{token}}` 按当前操作人换成：

```text
{{userToken}}
{{staffToken}}
{{adminToken}}
```

## 6. 登录三个账号

接口：

```http
POST /api/auth/login
```

### 6.1 登录 user1

请求：

```json
{
  "username": "user1",
  "password": "123456"
}
```

把响应中的：

```text
data.accessToken
```

保存为：

```text
userToken
```

### 6.2 登录 staff1

请求：

```json
{
  "username": "staff1",
  "password": "123456"
}
```

保存：

```text
staffToken
```

### 6.3 登录 admin1

请求：

```json
{
  "username": "admin1",
  "password": "123456"
}
```

保存：

```text
adminToken
```

## 7. user1 创建工单

接口：

```http
POST /api/tickets
Authorization: Bearer {{userToken}}
Idempotency-Key: accept-create-001
Content-Type: application/json
```

请求：

```json
{
  "title": "阶段 5 验收工单",
  "description": "用于验证创建、分配、评论、解决、关闭完整链路",
  "category": "SYSTEM",
  "priority": "HIGH"
}
```

预期：

```text
success = true
data.status = PENDING_ASSIGN
data.creatorId = user1 的用户 ID
data.assigneeId = null
```

保存：

```text
ticketId = data.id
```

### 7.1 验证幂等

用完全相同的 `Idempotency-Key` 再请求一次。

预期：

```text
仍然 success = true
返回的 data.id 与第一次相同
数据库中不会多出第二张相同幂等键工单
```

可用 SQL 检查：

```sql
SELECT id, ticket_no, title, status, creator_id, assignee_id, idempotency_key
FROM ticket
WHERE idempotency_key = 'accept-create-001';
```

## 8. admin1 分配工单

接口：

```http
PUT /api/tickets/{{ticketId}}/assign
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

请求：

```json
{
  "assigneeId": 2
}
```

说明：

```text
seed.sql 中 staff1 通常是 id=2。
如果本地数据不是这个 ID，请先查 sys_user。
```

查询 staff1 的 ID：

```sql
SELECT id, username, status
FROM sys_user
WHERE username = 'staff1';
```

预期：

```text
success = true
data.status = PROCESSING
data.assigneeId = staff1 的用户 ID
```

## 9. staff1 添加评论

接口：

```http
POST /api/tickets/{{ticketId}}/comments
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

请求：

```json
{
  "content": "已收到，正在排查服务日志。"
}
```

预期：

```text
success = true
data.ticketId = ticketId
data.commenterId = staff1 的用户 ID
```

## 10. staff1 解决工单

接口：

```http
PUT /api/tickets/{{ticketId}}/status
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

请求：

```json
{
  "targetStatus": "RESOLVED",
  "solutionSummary": "重启登录服务后恢复。"
}
```

预期：

```text
success = true
data.status = RESOLVED
data.solutionSummary = 重启登录服务后恢复。
```

## 11. 验证 status 接口不能关闭

接口：

```http
PUT /api/tickets/{{ticketId}}/status
Authorization: Bearer {{userToken}}
Content-Type: application/json
```

请求：

```json
{
  "targetStatus": "CLOSED"
}
```

预期：

```text
success = false
code = CLOSE_TICKET_USE_CLOSE_API
```

关闭工单必须走专门的 close 接口。

## 12. user1 关闭工单

接口：

```http
PUT /api/tickets/{{ticketId}}/close
Authorization: Bearer {{userToken}}
```

预期：

```text
success = true
data.status = CLOSED
```

## 13. user1 查询详情

接口：

```http
GET /api/tickets/{{ticketId}}
Authorization: Bearer {{userToken}}
```

预期：

```text
success = true
data.ticket.status = CLOSED
data.comments 至少有 1 条
data.operationLogs 至少包含 CREATE / ASSIGN / COMMENT / UPDATE_STATUS / CLOSE
```

第二次再查同一个详情，会优先走 Redis 详情缓存。缓存命中时仍会基于缓存中的 `creatorId / assigneeId` 做权限判断。

## 14. 数据库核对

### 14.1 查看工单主表

```sql
SELECT id, ticket_no, title, status, creator_id, assignee_id, solution_summary, idempotency_key
FROM ticket
WHERE id = {ticketId};
```

预期：

```text
status = CLOSED
creator_id = user1 的用户 ID
assignee_id = staff1 的用户 ID
solution_summary 不为空
```

### 14.2 查看评论

```sql
SELECT id, ticket_id, commenter_id, comment_type, content, created_at
FROM ticket_comment
WHERE ticket_id = {ticketId}
ORDER BY created_at ASC;
```

预期：

```text
至少有 1 条评论
comment_type = USER_REPLY
```

### 14.3 查看操作日志

```sql
SELECT id, ticket_id, operator_id, operation_type, operation_desc, before_value, after_value, created_at
FROM ticket_operation_log
WHERE ticket_id = {ticketId}
ORDER BY created_at ASC;
```

预期至少包含：

```text
CREATE
ASSIGN
COMMENT
UPDATE_STATUS
CLOSE
```

## 15. 负向验收

### 15.1 普通用户不能分配工单

用 `userToken` 调用：

```http
PUT /api/tickets/{{ticketId}}/assign
```

预期：

```text
success = false
code = ADMIN_REQUIRED
```

### 15.2 非负责人不能转派

如果准备另一个普通用户，可验证非负责人转派失败。

当前 seed 只有一个普通用户 `user1`，而 `user1` 是提单人。提单人不是当前负责人时，调用转派应失败：

```http
PUT /api/tickets/{{ticketId}}/transfer
Authorization: Bearer {{userToken}}
```

预期：

```text
success = false
code = TICKET_TRANSFER_FORBIDDEN
```

### 15.3 已关闭工单不能继续评论

关闭后继续调用：

```http
POST /api/tickets/{{ticketId}}/comments
```

预期：

```text
success = false
code = TICKET_CLOSED
```

## 16. 验收结论

如果以上链路通过，说明当前项目已经具备阶段 5 的后端业务闭环：

```text
认证可用
工单主流程可用
状态流转受控
业务权限生效
操作日志完整
幂等防重可用
Redis 详情缓存可用
SQL 条件更新能防止并发状态覆盖
```

通过后再进入阶段 6：Agent 对话入口与意图路由。

## 17. 实际验收记录

验收时间：2026-04-18

本次使用 Apifox 手动验收，后端应用、本地 MySQL、本地 Redis 均已启动。Redis 实际环境存在密码要求，已通过调整 `spring.data.redis.password` 与本地 Redis 配置保持一致。

### 17.1 登录与 token

已完成 `user1 / staff1 / admin1` 登录，并使用登录响应中的 `data.accessToken` 调用后续接口。

当前项目使用 JWT 无状态认证，token 不保存到 Redis。Apifox 需要把 token 放到请求头：

```http
Authorization: Bearer {accessToken}
```

### 17.2 创建工单

`user1` 创建工单通过。

实际返回示例：

```text
id = 1
ticketNo = INC202604181909235742
status = PENDING_ASSIGN
statusInfo = 待分配
creatorId = 1
assigneeId = null
```

数据库中已确认存在对应工单数据。

### 17.3 分配工单

`admin1` 分配工单给 `staff1` 通过。

实际返回示例：

```text
id = 1
status = PROCESSING
statusInfo = 处理中
creatorId = 1
assigneeId = 2
```

验收过程中发现 `@PathVariable` 未显式声明参数名时，在当前编译参数下会报错：

```text
Name for argument of type [java.lang.Long] not specified
```

已修复为显式声明路径变量名，例如 `@PathVariable("ticketId")`。

### 17.4 添加评论

`staff1` 添加评论通过。

实际返回示例：

```text
ticketId = 1
commenterId = 2
commentType = USER_REPLY
content = 已收到，正在排查登录服务日志。
```

验收过程中发现评论接口返回的 `createdAt` 曾为 `null`。原因是 `created_at` 由数据库默认值生成，插入后的 Java 对象不会自动回填该字段。

已调整为评论插入成功后按评论 ID 重新查询一次，再返回完整评论数据。

### 17.5 查询详情与操作日志

`user1` 查询工单详情通过。

详情中已包含：

```text
ticket
comments
operationLogs
```

操作日志已包含：

```text
CREATE
ASSIGN
COMMENT
```

后续状态流转后，详情中继续追加：

```text
UPDATE_STATUS
CLOSE
```

### 17.6 状态流转

`staff1` 将工单从 `PROCESSING` 更新为 `RESOLVED` 通过。

实际返回示例：

```text
status = RESOLVED
statusInfo = 已解决
solutionSummary = 重启登录服务后恢复
```

通用状态接口尝试直接关闭工单时，按预期失败：

```text
success = false
code = CLOSE_TICKET_USE_CLOSE_API
message = 关闭工单请使用关闭接口
```

`user1` 通过专用关闭接口关闭工单通过。

实际返回示例：

```text
status = CLOSED
statusInfo = 已关闭
```

### 17.7 负向权限与状态测试

以下负向测试均已通过：

```text
已关闭工单不能继续评论:
  code = TICKET_CLOSED

已关闭工单不能再次关闭:
  code = INVALID_TICKET_STATUS

普通用户不能分配工单:
  code = ADMIN_REQUIRED

非当前负责人或管理员不能转派工单:
  code = TICKET_TRANSFER_FORBIDDEN
```

### 17.8 分页查询

普通用户分页查询通过，员工分页查询通过。

验收时出现过“id=1 的工单为什么没查出来”的疑问，原因是当时分页查询条件筛选的是 `PROCESSING`，而 id=1 的工单已经流转到 `CLOSED`，因此不会出现在 `PROCESSING` 查询结果中。这是符合当前查询条件和状态流转结果的。

## 18. 当前已知小问题

1. Redis 密码配置需要与本机 Redis 实际配置一致。文档默认写的是无密码，但本地 Redis 如果启用了 `requirepass`，应用必须配置 `spring.data.redis.password`，否则会出现 `NOAUTH Authentication required`。

2. JWT token 当前不存 Redis，也没有退出登录后的服务端失效机制。token 只依赖签名和过期时间校验，适合当前阶段的无状态认证，但还不支持强制踢人、退出后立即失效、修改密码后旧 token 立即失效。

3. 评论接口曾出现 `createdAt = null`，已修复。后续如果其他插入接口也依赖数据库默认生成字段，需要统一检查是否也存在类似“插入返回对象字段不完整”的问题。

4. 当前 Apifox 工单接口文件仍命名为 `stage-4-ticket-core-mvp.openapi.yaml`，但它已经承载了阶段 4 和阶段 5 当前可测的工单接口。后续可以考虑补一份阶段 5 专用 OpenAPI 文件，或者重命名文档避免误解。

5. Redis 详情缓存当前主要保证功能可用，还缺少缓存命中率、缓存 key 查看、缓存失效行为的观测说明。手动验收时主要通过重复查询和业务结果确认，没有专门记录 Redis key。

6. Agent session 缓存结构已经预留，但阶段 5 尚未提供 Agent 对话入口，因此这部分当前只能算基础结构准备完成，不能算端到端验收完成。

## 19. 后续优化项

1. 增加登录登出完整能力：如果需要“退出立即失效”，可以引入 Redis token 黑名单、用户 session version，或者服务端 session/token 存储。

2. 补充阶段 5 专用 Apifox/OpenAPI 文档，把幂等请求头、Redis 相关说明、状态流转限制、关闭接口语义都明确写进去。

3. 增加 Redis 验收脚本或说明，例如查看工单详情缓存 key、幂等 key、Agent session key，方便定位缓存是否真实生效。

4. 补充并发场景自动化测试，重点覆盖管理员重复分配、负责人重复解决、关闭过程中状态被其他请求修改等情况。

5. 统一检查所有新增记录接口的返回对象，确认数据库默认生成的 `createdAt / updatedAt` 字段是否都能正确返回。

6. 已补充当前阶段代码边界说明，见 `docs/current-stage-code-boundaries.md`。阶段 6 接入 Agent 时，应继续保持认证、工单业务、缓存、Agent 预留结构的边界清晰，避免 Agent 绕过 `TicketService` 改动工单核心流程。
