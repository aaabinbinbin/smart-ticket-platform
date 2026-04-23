# 智能工单平台

基于 `Java 17`、`Spring Boot 3`、`Spring Security`、`MyBatis`、`Spring AI` 的模块化单体项目，目标是实现一套可演示、可扩展，同时具备后端工程性和 Agent 应用特征的智能工单平台。

## 项目定位

- 重点展示复杂业务后端、Agent 接入、RAG 工程化三条能力线
- 当前阶段是高完成度 MVP，不是生产级完整平台

## 当前状态


- 工单主流程已打通：创建、分页查询、详情、分配、认领、转派、状态流转、评论、关闭、操作日志
- 认证与权限已打通：JWT 登录、RBAC、Agent 接口鉴权校验
- Agent 主链已打通：`POST /api/agent/chat`、意图路由、Tool Calling、会话上下文、待创建草稿、多轮澄清
- RAG 主链已打通：知识构建、Embedding、query rewrite、轻量 rerank、MySQL fallback、PGvector 主路径开关
- P1/P2 业务能力已接入：SLA 定时扫描、自动分派、队列成员、工单类型扩展、审批流、多视角摘要

当前仓库的主代码可打包验证：

```bash
mvn "-Dmaven.test.skip=true" package
```

说明：

- 当前轮重构后的测试仍待统一修复与补齐，下一轮单独处理
- 本地运行默认依赖本机或局域网资源，配置写在 `application.yml` 中，当前不强制环境变量化

## 技术栈

- Java 17
- Spring Boot 3.4.x
- Spring Security
- MyBatis
- Redis
- MySQL
- Spring AI
- PostgreSQL + pgvector（可选主链路）

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

## 可看点

- 模块化单体拆分：`common/domain/infra/auth/biz/rag/agent/api/app`
- 工单领域建模：主流程、审批、SLA、队列、自动分派、操作审计
- Agent 应用设计：规则路由、低置信度澄清、Tool 执行边界、上下文恢复、结构化参数抽取
- RAG 工程化：知识构建、主检索与 fallback 双路径、检索日志、主路径切换能力
- 可扩展平台方向：多类型工单、审批流、多视角摘要

## 文档入口

- 项目概览：[docs/project-overview.md](/D:/aaaAgent/smart-ticket-platform/docs/project-overview.md)
- 详细说明：[docs/project-deep-dive.md](/D:/aaaAgent/smart-ticket-platform/docs/project-deep-dive.md)
- 快速启动：[docs/quick-start.md](/D:/aaaAgent/smart-ticket-platform/docs/quick-start.md)
- 工单与 Agent API：[docs/ticket-api.md](/D:/aaaAgent/smart-ticket-platform/docs/ticket-api.md)
- 演示脚本：[docs/demo-playbook.md](/D:/aaaAgent/smart-ticket-platform/docs/demo-playbook.md)
- 数据库初始化脚本：[docs/sql/schema.sql](/D:/aaaAgent/smart-ticket-platform/docs/sql/schema.sql)、[docs/sql/seed.sql](/D:/aaaAgent/smart-ticket-platform/docs/sql/seed.sql)
