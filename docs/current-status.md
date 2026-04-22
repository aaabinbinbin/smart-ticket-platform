# 当前实现状态

更新时间：`2026-04-22`

## 总体判断

当前项目已经不是“仅完成工程初始化”的状态，而是一套具备完整主流程、一定复杂业务能力、并已接入 Agent 与 RAG 的高完成度 MVP。

适合的对外表述：

- 智能工单平台初版
- Agent 增强型服务台平台
- 高完成度 MVP，具备继续平台化扩展的结构基础

## 阶段 A

### 已完成

- `README.md`、`docs/ticket-api.md`、`docs/current-status.md` 已统一到当前实现阶段
- `AgentController` 已补 `authentication == null` 与 `instanceof AuthUser` 校验
- 未登录访问已统一映射为 `UNAUTHORIZED` / HTTP 401
- Agent 控制器测试已覆盖未登录、异常 principal、正常请求映射

### 仍可继续补强

- `/api/agent/chat` 端到端集成测试仍不算充分
- Tool 边界测试已有基础覆盖，但还可以继续细化异常场景和权限边界

## 阶段 B

### 已完成

- `TicketService` 已拆分为 command/query/workflow/comment/queue-binding 门面结构
- `IntentRouter` 已支持规则优先级与低置信度澄清
- `CREATE_TICKET` 已支持缺参澄清、`pendingAction` 草稿延续与取消
- 创建前相似案例检查已接入，并支持“用户已尝试过方案”的分支
- RAG 已支持 query rewrite、轻量 rerank、MySQL fallback 和检索日志
- Embedding / Retrieval 主链已有日志与主路径/兜底路径区分

### 仍可继续补强

- PGvector 主路径还需要在真实环境做一次完整演示验证
- 检索评测样本和更系统化的 Agent API 集成测试仍待继续补齐

## 阶段 C

### C1 SLA 闭环

已完成：

- `@EnableScheduling` 已开启
- `TicketSlaScheduler` 已支持定时 breach scan
- `TicketSlaService` 已支持首次响应/解决时限违约区分
- 违约后优先级升级、管理员兜底、审计日志已接入
- `SLA_BREACH`、`SLA_ESCALATE` 操作日志已落地

待补：

- 真实站内信 / 邮件 / IM 通知通道

### C2 自动分派

已完成：

- 队列成员模型 `ticket_queue_member`
- 队列成员维护接口
- 自动分派规则命中、队列内最小负载、组负责人回退
- 人工认领接口 `PUT /api/tickets/{ticketId}/claim`
- 自动分派统计接口 `GET /api/ticket-assignment-rules/stats`

### C3 多类型工单

已完成：

- `INCIDENT`、`ACCESS_REQUEST`、`ENVIRONMENT_REQUEST`、`CONSULTATION`、`CHANGE_REQUEST`
- `typeProfile` 存储、查询与返回
- 按类型默认分类、默认优先级
- 按类型差异化字段校验

### C4 最小审批流

已完成：

- 权限申请 / 变更申请接入审批要求
- 审批模板、模板步骤、审批实例、审批步骤
- 审批模板配置接口
- 提交审批、审批通过、审批驳回
- 多级审批流转
- 审批未通过前限制分配、认领、转派、状态推进与关闭

### C5 多视角摘要

已完成第一版：

- 提单人进展摘要
- 处理人“问题现象 + 最近动作”摘要
- 管理员风险摘要
- 工单详情返回 `summaries`
- 独立摘要接口 `GET /api/tickets/{ticketId}/summary`
- Agent 查询链路已支持摘要型问法

### C 阶段仍待补齐

- C1 通知通道真实实现
- C5 更强的摘要策略与更完整测试

## 当前仓库最值得展示的能力

1. 复杂业务后端：工单、SLA、自动分派、审批流、日志审计
2. Agent 应用链路：意图路由、Tool Calling、上下文继承、多轮澄清
3. RAG 工程链路：知识构建、检索主路径与 fallback 双通道

## 下一步建议

1. 继续补 Agent API 与 Tool 的集成/边界测试
2. 做一次真实 PGvector 主路径演示并固化步骤
3. 继续把 C1 通知通道从占位实现推进到真实可运行实现
