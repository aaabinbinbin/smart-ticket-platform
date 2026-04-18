# 阶段 4：工单核心业务 MVP 实现说明

这份文档用于理解阶段 4 的实现思路和当前代码结构。它不是接口调用手册；接口调试可以导入：

```text
docs/apifox/stage-4-ticket-core-mvp.openapi.yaml
```

阶段 4 的目标是完成工单系统最重要的业务闭环：

```text
创建工单 -> 分配处理人 -> 处理中协作 -> 解决工单 -> 关闭工单
```

这个阶段不是简单 CRUD。工单系统的核心不是“能不能增删改查”，而是：

```text
状态流转 + 责任人变化 + 权限关系 + 过程留痕
```

所以阶段 4 的重点，是把一条工单记录变成一个可控制、可审计、可协作的业务流程。

## 1. 阶段 4 解决什么问题

阶段 4 主要解决：

1. 工单如何创建。
2. 工单如何查询详情。
3. 工单列表如何分页查询，并按用户权限限制可见范围。
4. 工单如何分配给处理人。
5. 工单如何在处理人之间转派。
6. 工单状态如何按状态机流转。
7. 工单评论如何记录协作过程。
8. 工单关闭后如何结束流程。
9. 每个关键动作如何留下操作日志。

如果只对 `ticket` 表做增删改查，业务价值很弱。阶段 4 真正落地的是“工单处理流程”。

## 2. 涉及模块

阶段 4 主要涉及四个模块：

```text
smart-ticket-api
smart-ticket-biz
smart-ticket-domain
smart-ticket-common
```

整体依赖方向保持为：

```text
api -> biz -> domain
```

不能反过来，例如 `biz` 不应该依赖 `api`。

## 3. api 模块职责

`smart-ticket-api` 负责 HTTP 协议适配。

关键类：

```text
TicketController
TicketAssembler
CreateTicketRequestDTO
AssignTicketRequestDTO
UpdateTicketStatusRequestDTO
AddTicketCommentRequestDTO
TicketVO
TicketDetailVO
TicketCommentVO
TicketOperationLogVO
```

api 模块负责：

```text
接收 HTTP 请求
做参数校验
把请求 DTO 转成 biz 入参
调用 biz 服务
把结果组装成 VO
返回统一响应
```

api 模块不负责：

```text
判断能不能转派
判断状态能不能流转
判断谁能关闭
直接写操作日志
```

这些都交给 `smart-ticket-biz`。

## 4. biz 模块职责

`smart-ticket-biz` 是阶段 4 的核心。

关键类：

```text
TicketService
TicketPermissionService
TicketRepository
TicketCommentRepository
TicketOperationLogRepository
CurrentUser
TicketCreateCommandDTO
TicketPageQueryDTO
TicketUpdateStatusCommandDTO
TicketDetailDTO
```

biz 模块负责：

```text
工单核心业务流程
状态流转判断
工单动作权限判断
操作日志记录
调用 repository 完成数据读写
```

当前没有为 `TicketService` 拆接口和实现类。原因是阶段 4 只有一个实现，提前拆 `TicketService` / `TicketServiceImpl` 会增加样板代码。后续如果出现多实现、插件化、跨模块稳定契约等需求，再拆接口更合适。

## 5. domain 模块职责

`smart-ticket-domain` 负责实体、枚举和 MyBatis Mapper。

关键类：

```text
Ticket
TicketComment
TicketOperationLog
TicketStatusEnum
TicketPriorityEnum
TicketCategoryEnum
OperationTypeEnum
CodeInfoEnum
TicketMapper
TicketCommentMapper
TicketOperationLogMapper
```

domain 只表达数据结构和基础数据访问，不判断业务规则。

例如 domain 不判断：

```text
谁能关闭工单
状态能不能流转
什么操作要写日志
```

这些规则放在 biz。

## 6. common 模块职责

`smart-ticket-common` 放通用基础能力。

关键类：

```text
ApiResponse
PageResult
BusinessException
BusinessErrorCode
```

`BusinessErrorCode` 是当前统一业务错误码枚举，避免在业务代码里到处写：

```java
throw new BusinessException("ASSIGNEE_NOT_FOUND", "目标处理人不存在或已禁用");
```

现在统一写成：

```java
throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_FOUND);
```

带参数的错误消息：

```java
throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, code);
```

## 7. DTO、VO、CommandDTO 的边界

阶段 4 使用了三类对象，避免混用。

### API DTO

API DTO 是 HTTP 请求对象，位于 `smart-ticket-api`。

例如：

```text
CreateTicketRequestDTO
AssignTicketRequestDTO
UpdateTicketStatusRequestDTO
AddTicketCommentRequestDTO
```

它们服务于 Controller，通常带参数校验注解，例如：

```java
@NotBlank
@NotNull
@Size
```

### VO

VO 是 HTTP 响应对象，也位于 `smart-ticket-api`。

例如：

```text
TicketVO
TicketDetailVO
TicketCommentVO
TicketOperationLogVO
```

VO 的作用是避免直接把数据库实体暴露给前端。

### biz CommandDTO / QueryDTO

biz 层 DTO 是业务服务的入参或聚合返回对象，位于 `smart-ticket-biz`。

例如：

```text
TicketCreateCommandDTO
TicketPageQueryDTO
TicketUpdateStatusCommandDTO
TicketDetailDTO
```

这样做的好处是：

```text
HTTP 请求格式变化，不直接影响业务服务签名
业务服务不依赖 api 模块
```

## 8. 枚举 code/info 规范

当前 domain 枚举不再是裸枚举，而是统一实现 `CodeInfoEnum`。

例如工单状态：

```java
PROCESSING("PROCESSING", "处理中")
```

约定是：

```text
code：系统稳定标识，用于前后端交互和数据库存储
info：给人看的描述，用于页面展示
```

前后端交互规则：

```text
请求传 code
响应返回 code + info
业务内部使用 enum
SQL 存 code
```

例如响应：

```json
{
  "status": "PROCESSING",
  "statusInfo": "处理中"
}
```

Controller 中会把字符串 code 转成 enum：

```java
TicketStatusEnum.fromCode(code)
```

Mapper XML 写入 enum 字段时显式取 `.code`：

```xml
#{status.code}
```

当前 code 和 enum 名称保持一致。后续如果出现 `code != enum.name()`，需要再补 MyBatis TypeHandler。

## 9. 当前工单状态机

阶段 4 明确约束工单只能按下面顺序流转：

```text
PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED
```

状态含义：

```text
PENDING_ASSIGN：待分配，工单已创建，但还没有明确当前处理人
PROCESSING：处理中，工单已有处理人，正在推进处理
RESOLVED：已解决，处理人认为问题已解决，等待确认或关闭
CLOSED：已关闭，工单流程正式结束
```

状态流转校验在：

```text
TicketService.validateStatusTransition()
```

它保证：

```text
PENDING_ASSIGN 只能到 PROCESSING
PROCESSING 只能到 RESOLVED
RESOLVED 只能到 CLOSED
CLOSED 不能继续流转
```

这样可以避免：

```text
刚创建的工单直接 CLOSED
已关闭工单又变回 PROCESSING
没有处理人的工单被标记为 RESOLVED
```

## 10. 权限设计

阶段 4 的权限分两类。

### 可见性权限

可见性权限回答：

```text
当前用户能不能看到这张工单？
```

允许查看：

```text
管理员
工单创建人
工单当前处理人
```

当前实现已经把可见性权限下推到 SQL。

普通用户查询单张工单时使用：

```sql
WHERE id = #{id}
  AND (creator_id = #{userId} OR assignee_id = #{userId})
```

对应方法：

```text
TicketMapper.findVisibleById()
TicketRepository.findVisibleById()
TicketService.requireVisibleTicket()
```

应用场景：

```text
查询详情
添加评论前的可见性校验
普通用户分页查询
```

普通用户访问不可见工单时，对外返回 `TICKET_NOT_FOUND`，这样可以避免通过接口探测某个工单 ID 是否真实存在。

### 动作权限

动作权限回答：

```text
当前用户能不能执行某个动作？
```

例如：

```text
能不能分配
能不能转派
能不能解决
能不能关闭
```

这些没有全部下推到 SQL，而是保留在 `TicketPermissionService` 和 `TicketService` 中。

原因是动作权限不仅依赖用户关系，还依赖：

```text
角色
工单状态
目标处理人是否 STAFF
不同错误码语义
```

保留在业务层更清晰。

## 11. 当前权限规则

### 查询详情

允许：

```text
管理员
工单创建人
工单当前处理人
```

普通用户通过 SQL 可见性条件过滤。

### 分页查询

管理员：

```text
查询全部工单
```

普通用户：

```text
只查询自己创建或当前负责的工单
```

### 分配工单

允许：

```text
管理员
```

要求：

```text
工单状态是 PENDING_ASSIGN
目标处理人存在
目标处理人启用
目标处理人具备 STAFF 角色
```

分配后：

```text
PENDING_ASSIGN -> PROCESSING
```

### 转派工单

允许：

```text
当前处理人
管理员
```

要求：

```text
工单状态是 PROCESSING
新处理人必须具备 STAFF 角色
```

转派只改变：

```text
assignee_id
```

不改变状态。

### 解决工单

允许：

```text
当前处理人
管理员
```

要求：

```text
工单状态是 PROCESSING
```

流转：

```text
PROCESSING -> RESOLVED
```

### 关闭工单

允许：

```text
提单人
管理员
```

要求：

```text
工单状态是 RESOLVED
```

流转：

```text
RESOLVED -> CLOSED
```

## 12. CurrentUser 的作用

`CurrentUser` 位于 biz 模块。

它保存：

```text
当前用户 ID
当前用户名
当前用户角色
```

为什么不直接在 biz 中使用 `AuthUser`？

因为 `AuthUser` 属于 auth 模块，是 Spring Security 认证对象。如果 biz 直接依赖 `AuthUser`，业务层会和认证框架绑定过深。

当前做法是：

```text
api 从 Spring Security 中取 AuthUser
api 转成 CurrentUser
biz 只识别 CurrentUser
```

这样模块边界更清楚。

## 13. Repository 的作用

阶段 4 在 biz 模块中新增了 repository 封装：

```text
TicketRepository
TicketCommentRepository
TicketOperationLogRepository
```

它们只封装 mapper 调用，不写业务规则。

为什么不直接在 `TicketService` 里调 mapper？

当前项目规模还小，直接调也能跑。但加一层 repository 有两个好处：

1. `TicketService` 更专注业务流程。
2. 以后数据访问方式变化时，影响面更小。

这里不是重 DDD，只是轻量数据访问封装。

## 14. Mapper XML

阶段 4 后续已经把 mapper 中的 SQL 从注解迁移到了 XML。

mapper 接口只定义方法：

```text
TicketMapper
TicketCommentMapper
TicketOperationLogMapper
```

SQL 放在：

```text
smart-ticket-domain/src/main/resources/mapper
```

这样复杂查询、动态条件和权限过滤更清晰。

例如普通用户可见工单分页：

```sql
WHERE (creator_id = #{userId} OR assignee_id = #{userId})
```

## 15. 操作日志

工单系统不仅关心当前状态，还关心：

```text
它是怎么变成当前状态的
谁做了什么
操作前是什么
操作后是什么
```

所以阶段 4 对关键动作自动写入：

```text
ticket_operation_log
```

记录字段包括：

```text
ticket_id
operator_id
operation_type
operation_desc
before_value
after_value
```

当前支持的操作类型：

```text
CREATE
ASSIGN
TRANSFER
UPDATE_STATUS
COMMENT
CLOSE
```

目前 `before_value / after_value` 使用文本快照，例如：

```text
id=100, ticketNo=INC202604180001, status=PROCESSING, assigneeId=2
```

这是 MVP 取舍。第一版先保证关键动作有记录，后续可以升级为 JSON。

当前没有用 AOP 自动写操作日志。原因是工单操作日志是业务审计日志，不是普通技术日志，它强依赖操作类型、before、after、事务时机和特殊场景。现阶段显式写在业务流程里更清晰。后续如果重复明显，可以优先抽 `TicketAuditLogService`，再考虑注解 + AOP。

## 16. 创建工单链路

接口：

```http
POST /api/tickets
```

代码链路：

```text
TicketController.createTicket()
  -> TicketService.createTicket()
  -> TicketRepository.insert()
  -> TicketOperationLogRepository.insert()
```

业务规则：

```text
创建人是当前登录用户
初始状态固定为 PENDING_ASSIGN
来源固定为 MANUAL
自动生成工单号
自动记录 CREATE 日志
```

创建后不直接进入 `PROCESSING`，因为还没有明确处理人。

## 17. 查询详情链路

接口：

```http
GET /api/tickets/{ticketId}
```

代码链路：

```text
TicketController.getTicketDetail()
  -> TicketService.getDetail()
  -> TicketService.requireVisibleTicket()
  -> TicketRepository.findVisibleById() 或 findById()
  -> TicketCommentRepository.findByTicketId()
  -> TicketOperationLogRepository.findByTicketId()
```

返回内容：

```text
工单主信息
评论列表
操作日志列表
```

普通用户的详情查询权限已经下推到 SQL。

## 18. 分页查询链路

接口：

```http
GET /api/tickets?pageNo=1&pageSize=10
```

支持过滤：

```text
status
category
priority
```

代码链路：

```text
TicketController.pageTickets()
  -> TicketService.pageTickets()
  -> TicketRepository.pageAll() 或 TicketRepository.pageVisible()
```

权限规则：

```text
管理员查全部
普通用户只查自己创建或当前负责的工单
```

## 19. 分配工单链路

接口：

```http
PUT /api/tickets/{ticketId}/assign
```

代码链路：

```text
TicketController.assignTicket()
  -> TicketService.assignTicket()
  -> TicketPermissionService.requireAdmin()
  -> requireStatus(PENDING_ASSIGN)
  -> requireStaffUser()
  -> TicketRepository.updateAssigneeAndStatus()
  -> TicketOperationLogRepository.insert()
```

业务规则：

```text
只有管理员可以分配
只能分配待分配工单
目标处理人必须具备 STAFF
分配后状态变为 PROCESSING
自动记录 ASSIGN 日志
```

## 20. 转派工单链路

接口：

```http
PUT /api/tickets/{ticketId}/transfer
```

代码链路：

```text
TicketController.transferTicket()
  -> TicketService.transferTicket()
  -> TicketPermissionService.requireTransfer()
  -> requireStatus(PROCESSING)
  -> requireStaffUser()
  -> TicketRepository.updateAssignee()
  -> TicketOperationLogRepository.insert()
```

业务规则：

```text
当前负责人可以转派
管理员可以转派
只能转派处理中的工单
目标处理人必须具备 STAFF
转派不改变状态
自动记录 TRANSFER 日志
```

## 21. 更新状态链路

接口：

```http
PUT /api/tickets/{ticketId}/status
```

代码链路：

```text
TicketController.updateStatus()
  -> TicketService.updateStatus()
  -> validateStatusTransition()
  -> TicketRepository.updateStatus()
  -> TicketOperationLogRepository.insert()
```

状态规则：

```text
PENDING_ASSIGN -> PROCESSING
PROCESSING -> RESOLVED
```

通用状态接口不再负责关闭工单。关闭工单必须走专门接口：

```http
PUT /api/tickets/{ticketId}/close
```

这样可以保证关闭动作统一写入 `CLOSE` 操作日志，而不是被记录成普通 `UPDATE_STATUS`。

分配、转派、更新状态、关闭都会在 SQL 更新时带上当前期望状态。如果影响行数为 0，说明工单已经被其他请求修改，业务层返回 `TICKET_STATE_CHANGED`。

## 22. 添加评论链路

接口：

```http
POST /api/tickets/{ticketId}/comments
```

代码链路：

```text
TicketController.addComment()
  -> TicketService.addComment()
  -> TicketService.requireVisibleTicket()
  -> TicketCommentRepository.insert()
  -> TicketOperationLogRepository.insert()
```

业务规则：

```text
能查看工单的人才能评论
已关闭工单不能继续评论
自动记录 COMMENT 日志
```

评论是过程数据，回答“这张工单处理过程中发生了什么沟通”。

## 23. 关闭工单链路

接口：

```http
PUT /api/tickets/{ticketId}/close
```

代码链路：

```text
TicketController.closeTicket()
  -> TicketService.closeTicket()
  -> TicketPermissionService.requireClose()
  -> requireStatus(RESOLVED)
  -> TicketRepository.updateStatus(CLOSED)
  -> TicketOperationLogRepository.insert()
```

业务规则：

```text
提单人可以关闭
管理员可以关闭
只能关闭已解决工单
自动记录 CLOSE 日志
```

关闭后，工单流程结束。从系统角度看，关闭后的工单才开始具备知识沉淀价值。

## 24. REST 风格

阶段 4 接口按资源语义设计：

```text
POST /api/tickets                      创建工单
GET  /api/tickets/{ticketId}           查询详情
GET  /api/tickets                      分页列表
PUT  /api/tickets/{ticketId}/assign    分配
PUT  /api/tickets/{ticketId}/transfer  转派
PUT  /api/tickets/{ticketId}/status    更新状态
POST /api/tickets/{ticketId}/comments  添加评论
PUT  /api/tickets/{ticketId}/close     关闭
```

分配、转派、关闭都具有明确业务语义，所以使用动作路径表达更清晰。

## 25. Apifox 调试文件

已生成 Apifox 可导入的 OpenAPI 文件：

```text
docs/apifox/stage-4-ticket-core-mvp.openapi.yaml
```

导入 Apifox 后设置环境：

```text
http://localhost:8080
```

所有接口需要 Bearer Token：

```text
Authorization: Bearer <accessToken>
```

导入后可以直接测试阶段 4 的 8 个核心接口。

## 26. 当前已完成

阶段 4 当前已经完成：

```text
工单创建
工单详情查询
工单分页查询
管理员分配
当前负责人或管理员转派
状态更新
评论
关闭
操作日志
参数校验
OpenAPI / Apifox 文件
枚举 code/info
业务异常枚举化
Mapper XML 化
可见性权限 SQL 下推
```

## 27. 暂不处理

当前阶段暂不处理：

```text
复杂审批流
SLA 自动升级
子任务系统
附件真实文件存储
工单详情缓存
完整幂等防重
RAG 知识构建
Agent 自然语言入口
```

这些不是不重要，而是应该在核心业务闭环稳定后再加。

## 28. 核心结论

你需要记住：

1. 工单系统的核心不是 CRUD，而是流程闭环。
2. 状态流转必须受控，否则流程不可信。
3. 权限不仅看角色，还要看用户和工单的业务关系。
4. 可见性权限可以下推 SQL，动作权限保留在业务层更清晰。
5. `auth` 只负责身份和基础角色，工单业务权限放在 `biz`。
6. 操作日志是工单系统的审计基础。
7. DTO / VO / Entity / CommandDTO 各有边界，不要混用。
8. Controller 不写业务规则，Service 不处理 HTTP 协议，Mapper 不判断业务。
9. 第一版先跑通闭环，不急着加入审批流、SLA、子任务和 RAG。
