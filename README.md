# 企业智能工单协同平台

基于 Java 17、Spring Boot 3、Spring Security、Spring AI 和 MyBatis 的模块化单体项目，目标是提供一个可演示、可扩展的智能工单平台主干。

## 当前状态

当前主干已经打通：
- 工单核心流程：创建、详情、分页、分配、转派、状态流转、评论、关闭、操作日志
- 认证与权限：JWT 登录、基础 RBAC
- Agent 主链路：`POST /api/agent/chat`
- Tool Calling：`QUERY_TICKET`、`CREATE_TICKET`、`TRANSFER_TICKET`、`SEARCH_HISTORY`
- 会话上下文：Redis 短会话、最近消息、当前工单引用、`pendingAction` 草稿
- RAG：知识构建、Embedding、query rewrite、规则 rerank、fallback 路径日志
- SLA 主干：定时扫描、违约分类、优先级升级、管理员接管占位、通知占位、审计日志
- 队列主干：工单组、队列、队列成员、队列绑定、自动分派规则、真实最小负载分派

## 已完成 / 未完成 / 下一步

| 状态 | 内容 |
| --- | --- |
| 已完成 | 阶段 A：文档对齐、注释校正、AgentController 认证健壮性、Agent API 与 Tool 测试 |
| 已完成 | 阶段 B：`TicketService` 拆分、IntentRouter 低置信度澄清、CREATE_TICKET 多轮补参、创建前相似案例分流、RAG rewrite/rerank/fallback |
| 已完成 | 阶段 C1：SLA 定时扫描、违约升级、通知占位、审计日志 |
| 已完成 | 阶段 C2 主干：基于队列成员的最小负载自动分派、组内跨队列选人、组负责人回退、无人可分派时保留待认领 |
| 未完成 | C1 真实通知通道、C3 多类型工单、C4 审批流、C5 多视角摘要、后续平台化能力 |
| 下一步 | 继续阶段 C3/C4，或者补齐 C1 的真实通知通道与可配置升级策略 |

## 模块说明

```text
smart-ticket-platform
|- smart-ticket-app
|- smart-ticket-common
|- smart-ticket-auth
|- smart-ticket-domain
|- smart-ticket-biz
|- smart-ticket-agent
|- smart-ticket-rag
|- smart-ticket-infra
`- smart-ticket-api
```

- `smart-ticket-app`：启动模块，负责装配全部业务模块
- `smart-ticket-common`：公共响应、异常、通用工具
- `smart-ticket-auth`：JWT 认证和基础授权
- `smart-ticket-domain`：实体、枚举、Mapper
- `smart-ticket-biz`：工单、SLA、队列、自动分派等核心业务
- `smart-ticket-agent`：意图路由、工具执行、会话上下文
- `smart-ticket-rag`：知识构建、向量化、历史案例检索
- `smart-ticket-infra`：Redis、MySQL、PGvector、Spring AI 相关配置
- `smart-ticket-api`：Controller、DTO/VO、参数校验、异常映射

## API 范围

- 工单与 P1 配置接口：见 [docs/ticket-api.md](D:/aaaAgent/smart-ticket-platform/docs/ticket-api.md)
- Agent 对话接口：`POST /api/agent/chat`
- 当前实现状态：见 [docs/current-status.md](D:/aaaAgent/smart-ticket-platform/docs/current-status.md)