# Redis 设计说明

本文档说明阶段 5 引入 Redis 后的 key 设计和使用边界。

当前本地 Redis 配置：

```text
host: 127.0.0.1
port: 6379
password: 123456
database: 0
```

对应配置位于：

```text
smart-ticket-app/src/main/resources/application.yml
```

## 1. Redis 在当前阶段负责什么

阶段 5 只让 Redis 承担三类能力：

```text
工单详情缓存
创建工单幂等防重
Agent 会话上下文结构预留
```

当前不做：

```text
多级缓存
缓存预热
复杂分布式锁框架
Redis 替代数据库事实查询
Agent 真实意图路由
```

MySQL 仍然是业务事实来源。Redis 只是提升读取效率、降低重复提交风险，并为后续 Agent 上下文做准备。

## 2. Redis 技术封装

Redis 通用访问封装位于：

```text
smart-ticket-infra/src/main/java/com/smartticket/infra/redis
```

核心类：

```text
RedisJsonClient
RedisKeys
```

`RedisJsonClient` 负责 JSON 序列化、反序列化、按 TTL 写入、`setIfAbsent` 和删除。

`RedisKeys` 负责统一维护 key 规则，避免业务代码到处拼字符串。

## 3. 工单详情缓存

key：

```text
ticket:detail:{ticketId}
```

缓存内容：

```text
TicketDetailDTO
```

包含：

```text
ticket
comments
operationLogs
```

TTL：

```text
10 分钟
```

实现位置：

```text
TicketDetailCacheService
TicketService.getDetail()
```

查询详情流程：

```text
TicketService.getDetail()
  -> TicketDetailCacheService.get()
  -> 缓存命中则使用 CurrentUser + cached.ticket 做内存可见性判断
  -> 可见则直接返回缓存
  -> 不可见则返回 TICKET_NOT_FOUND
  -> 缓存未命中则走 requireVisibleTicket() 查询数据库
  -> 查询评论和日志
  -> TicketDetailCacheService.put()
```

这里先读缓存，但不会绕过权限判断。缓存命中后必须基于缓存中的 `ticket.creatorId` 和 `ticket.assigneeId` 判断当前用户是否可见。

这样做的收益是：

```text
缓存命中时不再查询数据库
权限判断仍然保留
```

## 4. 缓存失效时机

只要会影响详情页展示内容，就删除：

```text
ticket:detail:{ticketId}
```

当前失效动作包括：

```text
分配工单
转派工单
更新状态
添加评论
关闭工单
```

创建工单后暂时不主动写详情缓存，等第一次查询详情时再缓存。

## 5. 创建工单幂等防重

创建工单支持幂等键。推荐前端通过请求头传递：

```http
Idempotency-Key: create-ticket-20260418-001
```

为了兼容当前请求 DTO，也可以继续在请求体里传：

```json
{
  "idempotencyKey": "create-ticket-20260418-001"
}
```

如果请求头和请求体都传，以请求头为准。

幂等键限制：

```text
长度不能超过 128
不能包含控制字符
```

结果 key：

```text
ticket:idempotency:{userId}:{idempotencyKey}
```

保存内容：

```text
ticketId
```

TTL：

```text
24 小时
```

加锁 key：

```text
ticket:idempotency:{userId}:{idempotencyKey}:lock
```

TTL：

```text
30 秒
```

key 中带 `userId` 的原因是避免不同用户使用相同幂等键时互相影响。

幂等处理流程：

```text
没有幂等键
  -> 正常创建

有幂等键
  -> 查询 ticket:idempotency:{userId}:{idempotencyKey}
  -> 已存在 ticketId，直接返回该工单
  -> 不存在 ticketId，尝试写入 lock key
  -> 获取 lock 成功，创建工单
  -> 数据库事务提交成功后保存 ticketId
  -> 事务完成后删除 lock
  -> 获取 lock 失败，返回 IDEMPOTENT_REQUEST_PROCESSING
```

当前幂等防重解决的是重复点击、网络重试和同一个客户端重复发送，不替代数据库唯一约束，也不用于业务去重判断。

幂等结果要在事务提交后写入 Redis，避免出现：

```text
Redis 已保存 ticketId
但数据库事务最终回滚
```

## 6. Agent 会话上下文预留

key：

```text
agent:session:{sessionId}
```

结构：

```json
{
  "activeTicketId": 100,
  "activeAssigneeId": 2,
  "lastIntent": "QUERY_TICKET",
  "recentMessages": [
    "帮我查一下 INC202604170001",
    "把它转给张三"
  ]
}
```

TTL：

```text
2 小时
```

实现位置：

```text
smart-ticket-agent/src/main/java/com/smartticket/agent/model/AgentSessionContext.java
smart-ticket-agent/src/main/java/com/smartticket/agent/service/AgentSessionCacheService.java
```

当前阶段只预留结构和读写能力，不实现 Agent 意图识别和多轮对话。

## 7. 设计边界

需要记住：

1. Redis 不是当前工单事实来源，MySQL 才是。
2. 缓存命中也必须先过权限校验。
3. 工单状态变化、负责人变化、评论变化后必须清详情缓存。
4. 幂等键只约束同一个用户的同一次创建请求。
5. Agent session 当前只是后续阶段的上下文存储基础。
