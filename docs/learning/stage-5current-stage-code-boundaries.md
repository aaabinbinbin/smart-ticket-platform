# 当前阶段代码边界

本文档用于在阶段 5 结束、阶段 6 接入 Agent 前，明确当前各模块的职责边界和调用规则。

目标是保证后续 Agent 能复用工单核心能力，但不能绕过已有的认证、权限、状态流转、缓存失效和操作日志规则。

## 1. 模块职责

### 1.1 smart-ticket-auth

负责认证与当前登录用户身份。

当前职责：

```text
用户名密码登录
JWT 生成与校验
Spring Security 配置
从 token 解析当前用户
加载用户角色
```

边界要求：

```text
auth 不编排工单业务
auth 不依赖 biz 或 agent
auth 不直接读写工单数据
JWT 当前不存 Redis，Redis 不承担登录会话存储
```

### 1.2 smart-ticket-api

负责 HTTP 适配。

当前职责：

```text
Controller
请求 DTO
响应 VO
参数校验
枚举 code 解析
Authentication -> CurrentUser 转换
调用业务服务
```

边界要求：

```text
api 不写业务状态机
api 不直接访问 Mapper
api 不直接操作 Redis
api 不拼装复杂业务规则
```

Controller 只做协议转换，业务规则必须进入 `smart-ticket-biz`。

### 1.3 smart-ticket-biz

负责工单核心业务闭环。

当前职责：

```text
创建工单
分配工单
转派工单
状态流转
关闭工单
添加评论
分页查询
详情查询
权限判断
操作日志写入
缓存读写编排
创建幂等防重编排
```

边界要求：

```text
所有改变工单状态的入口必须经过 TicketService
所有工单权限判断必须在 biz 内完成
所有工单状态流转必须在 biz 内完成
所有操作日志写入必须跟随业务动作发生
所有详情缓存失效必须跟随业务写操作发生
```

后续 Agent 如果要创建、分配、评论、解决、关闭工单，也必须调用 `TicketService`，不能直接调用 Mapper 或 Repository。

### 1.4 smart-ticket-domain

负责领域数据结构和基础数据访问。

当前职责：

```text
实体类
枚举类
MyBatis Mapper
Mapper XML SQL
```

边界要求：

```text
domain 不编排业务流程
domain 不做权限判断
domain 不写操作日志策略
domain 不访问 Redis
domain 不依赖 auth / biz / agent
```

Mapper 只表达数据访问能力。是否允许执行某个动作，由 `smart-ticket-biz` 决定。

### 1.5 smart-ticket-infra

负责基础设施适配。

当前职责：

```text
Redis JSON 读写封装
Redis key 统一命名
外部存储和外部服务客户端预留
```

边界要求：

```text
infra 不理解工单业务规则
infra 不判断用户权限
infra 不决定状态流转
infra 不主动调用 biz
```

当前 `RedisJsonClient` 是通用 Redis JSON 适配器，业务含义由调用方决定。

### 1.6 smart-ticket-agent

负责后续自然语言入口与会话上下文。

当前职责：

```text
Agent session 缓存结构预留
Agent session Redis 读写服务
```

阶段 6 后将承载：

```text
自然语言输入
意图识别
参数澄清
工具调用编排
调用 TicketService 执行业务动作
结果摘要
```

边界要求：

```text
agent 可以编排对话流程
agent 可以调用 biz 暴露的业务服务
agent 不直接调用 TicketMapper
agent 不直接写 ticket / ticket_comment / ticket_operation_log 表
agent 不复制 TicketService 内的权限判断和状态机
agent 不绕过 TicketService 做工单写操作
```

Agent 是新的入口，不是新的工单业务实现。

## 2. 当前依赖方向

当前推荐依赖方向：

```text
app
  -> api
  -> auth
  -> biz
  -> agent
  -> rag
  -> infra
  -> domain
  -> common

api -> auth / biz / agent / common
agent -> biz / rag / infra / common
biz -> domain / infra / common
auth -> domain / common
domain -> common
infra -> common
```

需要避免的方向：

```text
biz -> agent
auth -> biz
domain -> biz
infra -> biz
agent -> domain mapper
api -> domain mapper
```

## 3. Agent 接入工单能力的规则

阶段 6 接入 Agent 时，推荐按下面方式做：

```text
AgentController
  -> AgentService
  -> intent router
  -> tool handler
  -> TicketService
```

例如用户说“帮我把 1 号工单转给 staff1”，Agent 可以做：

```text
识别意图 = TRANSFER_TICKET
抽取 ticketId = 1
抽取 assignee = staff1
必要时澄清参数
根据当前登录用户构造 CurrentUser
调用 TicketService.transferTicket(...)
把 TicketService 返回结果摘要给用户
```

Agent 不应该做：

```text
直接 update ticket set assignee_id = ?
直接 insert ticket_operation_log
自己判断当前用户是否是负责人
自己判断 PROCESSING 是否可以转派
自己删除 ticket:detail:{ticketId}
```

这些规则已经在 `TicketService` 内聚合，重复实现会导致权限、日志、缓存和状态流转不一致。

## 4. 缓存边界

当前缓存能力分三类：

```text
工单详情缓存 -> TicketDetailCacheService
创建幂等防重 -> TicketIdempotencyService
Agent session -> AgentSessionCacheService
```

约束：

```text
MySQL 是事实来源
Redis 是缓存和短期上下文
缓存命中不能绕过权限判断
工单写操作后必须清理详情缓存
幂等结果必须在数据库事务提交后写入 Redis
Agent session 不能作为工单事实来源
```

## 5. 认证边界

当前登录 token 是 JWT：

```text
登录成功后返回 accessToken
客户端通过 Authorization: Bearer {token} 携带
后端校验签名和过期时间
后端每次请求重新加载用户和角色
```

当前不做：

```text
token 存 Redis
服务端 session
退出登录立即失效
强制踢人
修改密码后旧 token 立即失效
```

如果后续需要这些能力，应在认证模块内扩展，例如：

```text
Redis token blacklist
用户 session version
refresh token 存储
登录设备管理
```

不要把 token 生命周期逻辑放进工单业务模块。

## 6. 阶段 6 前检查清单

进入阶段 6 前，新增 Agent 代码时检查：

```text
是否只通过 TicketService 改变工单
是否没有直接调用 TicketMapper
是否没有复制工单权限判断
是否没有复制工单状态机
是否没有绕过操作日志
是否没有忘记缓存失效
是否没有把 JWT 存储逻辑写进工单模块
是否没有把 Agent session 当作数据库事实
```

只要这几条守住，Agent 接入后核心工单流程就不容易被改乱。
