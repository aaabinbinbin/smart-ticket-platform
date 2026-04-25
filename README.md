# 智能工单平台

基于 Java 17、Spring Boot 3、Spring Security、MyBatis、Spring AI 的模块化单体项目。项目定位是用于展示 Java 后端工程能力、复杂业务建模能力，以及受控型业务 Agent 的工程化落地能力。

当前项目是高完成度 MVP，不是生产级完整平台。

## 项目亮点

- 工单主流程：创建、查询、详情、分配、认领、转派、状态流转、评论、关闭、操作日志。
- 认证与权限：JWT 登录、RBAC、接口鉴权。
- Agent 主链：`route -> policy -> plan -> pending/command/react -> reply -> memory/context/trace -> response`。
- 受控型 Agent：支持意图路由、执行策略、Skill 注册、Tool 白名单、确定性写命令、高风险操作二次确认。
- Agent 稳定性：支持 session lock、rate limit、单轮预算、降级策略、同步/SSE 双接口和 trace metrics。
- RAG 工程化：知识构建、Embedding、query rewrite、轻量 rerank、MySQL fallback、pgvector 可选主路径。
- 知识闭环：工单关闭后通过 RabbitMQ 异步触发知识构建，并使用数据库 task 保证失败可补偿。
- 人工审核：知识候选支持管理员审核，通过后进入正式知识构建链路。
- 可观测性：Agent trace 持久化，接口返回 `traceId`，并提供最近调用指标统计。

## 技术栈

- Java 17
- Spring Boot 3.4.x
- Spring Security
- MyBatis
- MySQL
- Redis
- RabbitMQ
- Spring AI
- PostgreSQL + pgvector，可选

## 模块结构

```text
smart-ticket-platform
|- smart-ticket-app      # 应用启动与配置装配
|- smart-ticket-common   # 通用响应、异常、工具类
|- smart-ticket-domain   # 实体、枚举、Mapper
|- smart-ticket-infra    # Redis、基础设施适配
|- smart-ticket-auth     # JWT、认证过滤器、Security 配置
|- smart-ticket-biz      # 工单、SLA、自动分派、审批、知识任务
|- smart-ticket-rag      # 知识构建、检索、反馈、审核、异步入库
|- smart-ticket-agent    # Agent 主链、Planner、Skill、Tool、Memory、Trace
`- smart-ticket-api      # Controller、DTO、接口协议
```

## Agent 能力

当前 Agent 属于受控型业务 Agent，而不是完全开放式自治 Agent。它允许模型参与理解和生成，但关键业务动作由后端规则、权限、参数校验和风险确认控制。

主链能力：

- `POST /api/agent/chat`
- `POST /api/agent/chat/stream`
- 意图路由：查询工单、创建工单、转派工单、检索历史案例
- Planner：生成并推进当前执行计划
- ExecutionPolicy：按 intent、风险、权限和预算决定执行模式
- SkillRegistry：统一选择可用能力，ReAct 只暴露只读工具
- DeterministicCommandExecutor：创建、转派等写操作走确定性链路
- PendingActionCoordinator：统一处理补参、确认、取消
- Tool fallback：Spring AI 不可用时，查询类场景走确定性后端链路
- Memory：工作记忆、工单领域记忆、用户偏好记忆
- Trace & Metrics：记录 route、policy、plan、skill/tool、fallback、degrade、最终结果和耗时

更多说明见 [docs/agent-architecture.md](docs/agent-architecture.md)。

稳定性验收见 [docs/agent-stability-acceptance.md](docs/agent-stability-acceptance.md)，压测脚本见 [scripts/agent-smoke-load.ps1](scripts/agent-smoke-load.ps1)。

简历表达参考：

```text
设计并重构智能工单平台中的受控型业务 Agent 模块，构建 IntentRouter -> ExecutionPolicy -> PendingAction/CommandExecutor/ReadOnlyReact -> ReplyRenderer -> TraceMetrics 的可审计执行链路；将创建、转派等写操作从 LLM Tool Calling 中解耦为确定性 Command 执行，支持多轮补槽、高风险确认、只读 ReAct 检索、工具白名单、Session/Memory 单次提交、同步/SSE 双接口、会话互斥、限流、预算、降级和压测验收。
```

## RAG 与知识闭环

- 工单关闭后，在事务内创建 `ticket_knowledge_build_task`。
- 事务提交后发布关闭事件。
- RabbitMQ 消息触发知识构建。
- 定时补偿任务会扫描未投递或失败任务，避免消息丢失导致知识无法入库。
- 知识准入会生成候选知识；需要人工判断的内容进入候选审核。
- 管理员审核通过后，会强制进入正式知识构建和 embedding 流程。

### RAG 直接检索

`GET /api/rag/search` 绕过 Agent 路由，直接调用 RetrievalService 执行向量检索，方便验证 PGVECTOR 路径是否正常工作：

- 参数：`query`（检索文本）、`topK`（返回数，默认 5，最大 10）
- 返回：`retrievalPath`、`fallbackUsed`、`hits`（命中列表）
- 路径：pgvector → 命中返回 PGVECTOR；pgvector 不可用或未启用时回退 MySQL 关键词检索，返回 MYSQL_FALLBACK

### Dashboard 管理端接口

`GET /api/admin/dashboard` 提供工单平台和 RAG/Agent 核心运行指标，仅 ADMIN 角色可访问：

- **工单板块**：待分配、处理中、已解决、已关闭数量，今日创建数
- **RAG 板块**：ACTIVE 知识数、知识构建成功/失败数、embedding 切片数、当前检索路径
- **Agent 板块**：近 7 天调用次数、成功次数、平均耗时

## 接口与文档

- `/api/agent/chat` — Agent 对话（同步接口）
- `/api/agent/chat/stream` — Agent 流式对话（SSE，事件包括 accepted、route、status、delta、final、error、done）

## 快速启动

见 [docs/quick-start.md](docs/quick-start.md)。

常用命令：

```bash
mvn test
```

```bash
mvn -pl smart-ticket-app -am spring-boot:run
```

## 文档入口

- [项目概览](docs/project-overview.md)
- [快速启动](docs/quick-start.md)
- [Agent 架构说明](docs/agent-architecture.md)
- [Agent 稳定性验收](docs/agent-stability-acceptance.md)
- [详细设计说明](docs/project-deep-dive.md)
- [接口说明](docs/ticket-api.md)
- [演示脚本](docs/demo-playbook.md)
- [数据库初始化脚本](docs/sql/schema.sql)
- [P0 验收记录](docs/p0-acceptance-record.md)
- [RAG 评估说明](docs/rag-evaluation.md)

### 验收与测试

- [P0 验收记录](docs/p0-acceptance-record.md) — 包含单元测试、RAG 主路径、知识构建链路、pgvector 表数据验证、RAG 检索验证
- [RAG 评估说明](docs/rag-evaluation.md) — 当前 RAG 评估集用于本地测试和指标计算验证，覆盖 Recall@3、Recall@5、MRR
- [Agent 稳定性验收](docs/agent-stability-acceptance.md) — 请求限流、session 锁、预算超限、降级策略、异常恢复等稳定性场景
- [压测脚本](scripts/agent-smoke-load.ps1) — Agent 接口轻量压测
- [E2E 验收脚本](scripts/run_rag_pgvector_e2e.ps1) — 完整端到端 RAG + pgvector 验收流程：创建工单 -> 关闭 -> 等待知识构建 -> Agent 检索 -> 直接 RAG 检索

### 当前完成能力

- P0 工单 CRUD、状态流转、权限控制
- P0 Agent 对话（同步 + SSE）、意图路由、确定性写命令、只读 ReAct、Session/Memory、Trace/Metrics
- P0 RAG 知识构建链（MySQL + pgvector）、query rewrite、rerank、直接检索接口
- P1 Dashboard 管理端指标、限流降级、SSE done 事件、专用线程池、历史检索关键词优化、知识指标语义修复
