# 项目概览

智能工单平台是一个面向简历和面试展示的 Java 后端项目。它不是简单 CRUD，而是把工单业务、权限控制、Agent 编排、RAG 知识库和异步可靠任务放在同一个可运行工程中。

## 项目定位

- 展示 Java 后端工程能力：模块化拆分、认证鉴权、事务、异步任务、测试。
- 展示复杂业务建模能力：工单流转、SLA、自动分派、审批、操作日志。
- 展示 Agent 工程化能力：受控执行、Tool Calling、Planner、Skill、Memory、Trace。
- 展示 RAG 工程化能力：知识准入、检索重排、反馈闭环、人工审核、可靠入库。

当前阶段：高完成度 MVP。

## 已实现能力

### 工单业务

- 工单创建、详情、分页查询
- 分配、认领、转派
- 状态流转、关闭
- 评论与操作日志
- SLA 扫描与升级
- 自动分派与队列成员
- 审批流
- 多视角摘要

### 认证权限

- JWT 登录
- RBAC 角色控制
- 当前用户解析
- Agent / RAG 管理接口鉴权

### Agent

- 意图路由
- Planner 执行计划
- SkillRegistry 能力注册
- ExecutionGuard 执行前校验
- Tool 调用与确定性 fallback
- 高风险操作二次确认
- 短期上下文与三层记忆
- Trace 持久化和指标接口

### RAG

- 知识构建
- 结构化切片
- Embedding
- query rewrite
- 轻量 rerank
- MySQL fallback
- pgvector 可选路径
- RAG 反馈
- 知识候选人工审核
- 工单关闭后异步可靠入知识库

## 核心链路

### Agent 主链

```text
load session
-> hydrate memory
-> route intent
-> build/load plan
-> select skill
-> guard
-> execute tool or Spring AI tool calling
-> fallback if needed
-> update context and memory
-> write trace
-> return reply + plan + traceId
```

### 工单关闭入知识库

```text
close ticket transaction
-> create ticket_knowledge_build_task
-> after commit publish event
-> RabbitMQ publish
-> RabbitMQ consume
-> build knowledge candidate or formal knowledge
-> update task status
-> scheduled relay retries unfinished task
```

### 知识候选审核

```text
knowledge admission uncertain
-> create candidate
-> admin review
-> approve: create build task and force build
-> reject: record reviewer and comment
```

## 当前边界

- 项目是 MVP，部分能力用于展示工程设计，不等同生产级平台。
- Spring AI 和向量库默认关闭，可以通过配置打开。
- pgvector 需要外部 PostgreSQL 环境。
- RabbitMQ 用于工单关闭后的知识构建消息链路。
- 前端页面不是当前重点，后端 API 和工程结构是主要展示面。
