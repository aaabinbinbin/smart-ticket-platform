# 项目概览

## 1. 项目简介

智能工单平台是一个基于 `Java 17`、`Spring Boot 3`、`Spring Security`、`MyBatis`、`Spring AI` 的模块化单体后端项目。

项目目标：

- 展示复杂业务后端能力
- 展示 Agent 接入和 Tool Calling 能力
- 展示 RAG 检索与工程化兜底链路

当前项目定位为：

- 简历和面试展示型项目
- 高完成度 MVP
- 非生产级完整平台

## 2. 当前已实现能力

### 工单主流程

- 创建工单
- 工单详情与分页查询
- 分配、认领、转派
- 状态流转与关闭
- 评论与操作日志

### 认证与权限

- JWT 登录
- RBAC 角色控制
- Agent 接口登录校验

### Agent 能力

- `POST /api/agent/chat`
- 意图路由
- Tool Calling
- 会话上下文
- 创建工单缺参澄清
- 待创建草稿续写

### RAG 能力

- 知识构建
- Embedding
- query rewrite
- 轻量 rerank
- MySQL fallback
- PGvector 主路径开关

### 业务增强能力

- SLA 定时扫描与升级
- 自动分派
- 队列与队列成员
- 多类型工单
- 审批流
- 多视角摘要

## 3. 模块结构

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

## 4. 当前边界

以下内容可以直接作为已实现能力介绍：

- 工单主流程
- JWT + RBAC
- Agent 查询 / 创建 / 转派 / 历史检索链路
- RAG 的 rewrite / rerank / fallback
- SLA 扫描、升级、审计
- 自动分派、审批流、多视角摘要

以下内容建议明确说明为第一版或待补强：

- 自动化测试覆盖仍在继续补齐
- SLA 真实通知通道仍为预留位
- PGvector 主链路需要真实环境演示
- Agent 端到端集成验证仍可继续增强
- 摘要策略仍是第一版
