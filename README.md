# 智能工单平台

基于 `Java 17`、`Spring Boot 3`、`Spring Security`、`MyBatis`、`Spring AI` 的模块化单体项目，目标是实现一套可演示、可扩展、同时具备后端工程性和 Agent 应用特征的智能工单平台。

## 当前状态

截至 `2026-04-22`，项目已完成高完成度 MVP，并进入平台能力增强阶段。

- 工单主流程已打通：创建、分页查询、详情、分配、认领、转派、状态流转、评论、关闭、操作日志
- 认证与权限已打通：JWT 登录、RBAC、Agent 接口鉴权校验
- Agent 主链已打通：`/api/agent/chat`、意图路由、Tool Calling、会话上下文、待创建草稿、多轮澄清
- RAG 主链已打通：知识构建、Embedding、query rewrite、轻量 rerank、MySQL fallback、PGvector 主路径开关
- P1/P2 业务能力已接入：SLA 定时扫描、自动分派、队列成员、工单类型扩展、审批流、多视角摘要

## 阶段完成度

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| A | 基本完成 | 文档已对齐到当前阶段，认证健壮性已补，Agent/Tool 测试已具备基础覆盖，仍可继续补更完整集成测试 |
| B | 基本完成 | `TicketService` 已拆分，IntentRouter 已升级，多轮澄清创建、RAG rewrite/rerank/fallback 已落地 |
| C1 | 已完成第一版 | SLA 定时扫描、违约判断、升级占位、审计日志已落地，真实通知通道仍待补 |
| C2 | 已完成 | 组/队列/成员、自动分派、认领、统计接口已落地 |
| C3 | 已完成 | 多类型工单、`typeProfile`、差异化校验、默认分类和优先级已落地 |
| C4 | 已完成第一版 | 审批模板、审批步骤、提交/通过/驳回、多级审批与审批前限制已落地 |
| C5 | 已完成第一版 | 提单人/处理人/管理员多视角摘要已接入 biz、API 与 Agent 查询链路 |

## 面试可讲亮点

- 模块化单体拆分：`common/domain/infra/auth/biz/rag/agent/api/app`
- 工单领域建模：主流程、审批、SLA、队列、自动分派、操作审计
- Agent 应用设计：规则路由、低置信度澄清、Tool 执行边界、上下文恢复、结构化参数抽取
- RAG 工程化：知识构建、主检索与 fallback 双路径、检索日志、主路径切换能力
- 可扩展平台方向：多类型工单、审批流、多视角摘要

## 模块说明

```text
smart-ticket-platform
|- smart-ticket-app
|- smart-ticket-common
|- smart-ticket-domain
|- smart-ticket-infra
|- smart-ticket-auth
|- smart-ticket-biz
|- smart-ticket-rag
|- smart-ticket-agent
`- smart-ticket-api
```

- `smart-ticket-app`：应用启动与配置装配
- `smart-ticket-common`：统一响应、异常、通用工具
- `smart-ticket-domain`：实体、枚举、Mapper 定义
- `smart-ticket-infra`：Redis、Spring AI、向量存储等基础设施适配
- `smart-ticket-auth`：JWT、认证过滤器、Spring Security 配置
- `smart-ticket-biz`：工单、SLA、自动分派、审批、摘要等业务能力
- `smart-ticket-rag`：知识构建、Embedding、检索、重排
- `smart-ticket-agent`：意图路由、会话上下文、Tool 编排
- `smart-ticket-api`：Controller、DTO/VO、接口协议

## 关键接口

- 工单业务接口：见 [docs/ticket-api.md](/D:/aaaAgent/smart-ticket-platform/docs/ticket-api.md)
- Agent 对话接口：`POST /api/agent/chat`
- 当前阶段说明：见 [docs/current-status.md](/D:/aaaAgent/smart-ticket-platform/docs/current-status.md)
- 演示脚本：见 [docs/demo-playbook.md](/D:/aaaAgent/smart-ticket-platform/docs/demo-playbook.md)

## 当前已知缺口

- Agent API 端到端集成测试仍可继续加强
- C1 的站内信 / 邮件 / IM 通知仍是预留位
- PGvector 主路径已有配置与代码入口，但仍需在真实环境做一次完整演示验证
- 多视角摘要属于第一版规则摘要，后续可继续引入更强的总结策略
