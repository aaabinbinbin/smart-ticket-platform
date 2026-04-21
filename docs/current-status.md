# 当前实现状态

更新时间：2026-04-20

## 当前已完成内容

### P1 基础配置

- 工单组配置已实现：
  - `ticket_group` schema 已加入主 schema。
  - 提供创建、更新、启停、详情、分页查询接口。
  - 写操作要求 ADMIN。
- 工单队列配置已实现：
  - `ticket_queue` schema 已加入主 schema。
  - 提供创建、更新、启停、详情、分页查询接口。
  - 创建和更新队列时校验所属工单组存在且启用。
- 工单与队列绑定已实现：
  - `ticket` 主表新增 `group_id`、`queue_id`。
  - 提供 `PUT /api/tickets/{ticketId}/queue` 绑定工单组和队列。
  - 关闭工单不允许调整队列。
  - 绑定只修改队列归属，不修改处理人和状态。
- SLA 基础能力已实现：
  - `ticket_sla_policy` 和 `ticket_sla_instance` schema 已加入主 schema。
  - 提供 SLA 策略创建、更新、启停、详情、分页查询接口。
  - 提供 `GET /api/tickets/{ticketId}/sla` 查询工单 SLA 实例。
  - 工单创建后会按分类和优先级匹配启用策略并生成 SLA 实例。
  - 工单分配后会刷新 SLA 实例。
  - 当前未实现定时违约扫描、升级策略和通知。
- 自动分派 preview 已实现：
  - `ticket_assignment_rule` schema 已加入主 schema。
  - 提供自动分派规则创建、更新、启停、详情、分页查询接口。
  - 提供 `POST /api/tickets/{ticketId}/assignment-preview` 预览推荐目标。
  - preview 只返回推荐结果，不更新工单，不写操作日志，不调用真实分派。
- 真实自动分派已实现最小闭环：
  - 提供 `POST /api/tickets/{ticketId}/auto-assign`。
  - 仅支持命中规则中明确配置 `targetUserId` 的场景。
  - 真实写操作委托 `TicketService.assignTicket`，复用 ADMIN 权限、状态机、处理人校验、操作日志和 SLA 刷新。
  - 命中规则包含目标队列时，会先绑定工单队列，再执行真实分派。

### P0 主闭环

- `biz` 已保留为工单主流程和业务规则层：
  - `TicketCommandService` 负责创建工单和幂等。
  - `TicketQueryService` 负责工单事实查询。
  - `TicketAssignService` 负责转派和权限判断。
  - `TicketKnowledgeService` 负责知识生产，不直接调用向量检索，不承担工单关闭主流程。
- `agent` 已切换为 Spring AI 主链路：
  - `AgentController` 提供 `POST /api/agent/chat`。
  - `AgentFacade` 优先使用 Spring AI `ChatClient` + Tool Calling。
  - 模型不可用、模型未触发 Tool 或调用失败时，回退到同一套 Tool、Guard、参数抽取和上下文链路。
  - 旧的自定义 LLM client、prompt registry 和 `TicketAgentOrchestrator` 已从主链路删除。
- `agent` 已提供四个核心 Tool：
  - `QueryTicketTool`：调用 `TicketQueryService`，只查当前事实，不接 RAG。
  - `CreateTicketTool`：调用 `TicketCommandService`，复用业务幂等。
  - `TransferTicketTool`：调用 `TicketAssignService`，保留权限判断。
  - `SearchHistoryTool`：调用 `RetrievalService`，走 RAG 检索链路。
- `agent` 已提供短会话上下文：
  - Redis key：`agent:session:{sessionId}`。
  - 字段包括 `activeTicketId`、`activeAssigneeId`、`lastIntent`、`recentMessages`。
  - 支持最小指代消解，如“刚才那个工单”“它”“这个问题”。
- `rag` 已具备知识向量化和检索闭环：
  - `EmbeddingService` 负责知识切片和向量写入。
  - `RetrievalService` 负责历史知识检索。
  - 默认保留 MySQL JSON 向量 + 内存 TopK 兜底，保证本地无模型和无 PGvector 时可运行。
  - 启用 Spring AI PGvector 后，优先通过 `VectorStore` 写入和检索。
- `infra` 已承接外部 AI 能力适配：
  - `ChatClientConfig`
  - `EmbeddingModelConfig`
  - `VectorStoreConfig`
  - Redis、OpenAI compatible model、PGvector 配置骨架。

## 当前未完成内容

- P1/P2 复杂业务能力未实现，只保留草案：
  - SLA 违约扫描、基于队列/工单组的真实分派、升级策略。
  - 审批流、子任务、跨部门协作。
  - 知识治理、知识审核、知识版本管理。
  - 运营指标、报表、租户隔离。
- PGvector 独立数据源方案未最终落地。
- RAG query rewrite、rerank、召回评估和知识质量评分未实现。
- Agent 仍是单 Agent 入口，不做多 Agent 编排，不引入 MCP。
- Tool 参数抽取仍是第一版浅层规则 + Spring AI 填参，不做复杂实体图谱推断。

## 下一阶段计划

### P0 收尾

- 补充 Agent API 集成测试和 Tool 边界测试。
- 在可用模型环境下验证 Spring AI Tool Calling 的真实调用链路。
- 在 PostgreSQL + pgvector 环境下验证 `VectorStore` 写入、删除和检索。

### P1 草案落地前置

- 工单组和队列已经进入实现阶段。
- SLA 策略与实例基础能力已经进入实现阶段。
- 自动分派真实写入已完成最小闭环，并支持落目标队列。
- 下一步可选择补 API 集成测试，或实现 SLA 违约扫描。
- SLA 违约扫描和升级策略仍后置。

### P2 后续方向

- 在 P1 稳定后再实现知识治理、报表、租户隔离和复杂协作流程。
- 建立 RAG 召回质量评估数据集，再决定是否引入 rerank 或更复杂检索链路。
## 2026-04-20 SLA 违约扫描补充

- 已实现手动 SLA 违约扫描：`POST /api/ticket-sla-instances/breach-scan?limit=100`。
- 扫描范围：`ticket_sla_instance.breached = 0` 且 `resolve_deadline <= scanTime` 的实例。
- 扫描结果只标记 `breached = 1`，不修改工单状态，不做升级策略，不发送通知。
- 当前仍未实现定时调度、升级策略、通知策略和扫描审计记录。
