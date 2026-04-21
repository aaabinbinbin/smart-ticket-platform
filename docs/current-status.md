# 当前实现状态
更新日期：2026-04-21

## 已完成

### 阶段 A
- README、`docs/ticket-api.md`、`docs/current-status.md` 已与真实实现对齐
- `biz`、`agent`、`rag` 关键类的过时注释已校正
- `AgentController` 已补 `authentication == null` 和 `instanceof AuthUser` 防护
- 未认证错误统一映射为 `UNAUTHORIZED` / HTTP 401
- `/api/agent/chat` API 测试和 Agent Tool 边界测试已补齐

### 阶段 B
- `TicketService` 已拆分为 command/query/workflow/comment/queue-binding 等服务
- IntentRouter 已支持低置信度澄清和更稳的规则优先级
- CREATE_TICKET 已支持多轮补参、草稿延续和取消创建
- 创建前相似案例分流、已尝试分支和阈值控制已落地
- RAG rewrite、rerank、fallback、embedding/retrieval 日志已落地

### 阶段 C
#### C1 SLA 闭环
- 已开启 `@EnableScheduling`
- 已新增 `TicketSlaScheduler` 定时扫描
- `TicketSlaService` 已支持违约扫描、首次响应/解决时限区分、优先级升级、管理员接管占位、通知占位、审计日志
- 已新增 `SLA_BREACH`、`SLA_ESCALATE` 操作日志类型
- 手动扫描接口返回首次响应违约数、解决违约数、升级数、通知数

#### C2 自动分派主干
- 已新增队列成员模型 `ticket_queue_member`
- 已支持队列成员配置、启停和查询
- 自动分派规则已支持：
  - 指定处理人直接分派
  - 指定队列时按队列成员最小负载分派
  - 仅指定工单组时跨启用队列做最小负载分派
  - 无可用成员时回退到组负责人
  - 仍无人可分派时保留 `PENDING_ASSIGN`，仅完成组/队列绑定，等待人工认领

## 未完成
- C1 真实站内信 / 邮件 / IM 通知通道未接入
- C1 升级策略仍是固定规则，尚未配置化
- C3 多类型工单、C4 审批流、C5 多视角摘要尚未开始
- RAG 评测数据集与 PGvector 真实环境联调尚未完成

## 下一步建议
1. 做 C3：多类型工单模型和差异化处理流
2. 做 C4：最小审批流
3. 补 C1：真实通知通道和可配置升级策略

## 总结
阶段 A 和阶段 B 已完成。阶段 C 的 C1 与 C2 主干已落地，当前项目已经具备“自动发现 SLA 风险 + 基于队列负载自动分派”的主干能力，后续重点应转向更复杂的业务流。