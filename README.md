# 智能工单平台

基于 Java 17、Spring Boot 3、Spring Security、MyBatis、Redis、RabbitMQ、Spring AI、PostgreSQL + pgvector 的智能工单 MVP。项目覆盖工单创建、分派、认领、转派、状态流转、SLA、审批、操作日志、知识沉淀、RAG 检索和受控型业务 Agent。

当前项目是高完成度 MVP，不是生产级完整平台。

## 核心能力

### 业务模块

- 工单创建、查询、详情、分派、认领、转派
- 状态流转：PENDING_ASSIGN / PROCESSING / RESOLVED / CLOSED
- 幂等创建、并发认领保护
- SLA 和通知
- 审批与操作日志
- 工单关闭后触发知识沉淀

### Agent 模块

- 意图识别（LLM 分类 + 关键词回退）
- Planner 生成并推进执行计划
- SkillRegistry 统一选择可用能力，ReAct 只暴露只读工具
- ExecutionGuard 权限和风险校验
- Tool fallback：Spring AI 不可用时走确定性后端链路
- Memory：工作记忆、工单领域记忆、用户偏好记忆
- Trace & Metrics：记录 route、policy、plan、tool、fallback、degrade、耗时
- 高风险操作二次确认
- 只读检索和写操作分离
- SSE 事件级流式输出：`POST /api/agent/chat/stream`

### RAG 模块

- 工单关闭后异步知识构建
- RabbitMQ 消息驱动
- 知识准入与候选审核
- 结构化 chunk：SYMPTOM / ROOT_CAUSE / RESOLUTION / RISK_NOTE / APPLICABLE_SCOPE / FULL_TEXT
- 敏感信息脱敏
- MySQL fallback embedding
- PostgreSQL + pgvector 主向量库
- 直接 RAG 检索接口：`GET /api/rag/search`
- retrievalPath / fallbackUsed 可观测

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

RAG 主链路：

```text
关闭工单 → 创建知识构建任务 → RabbitMQ 投递消息 → 任务处理器消费
→ 生成知识 → 结构化 chunk → 敏感信息脱敏 → embedding
→ MySQL fallback → pgvector.vector_store → /api/rag/search 检索
```

检索默认使用双路召回：originalQuery + rewrittenQuery 各自检索，结果合并后去重再统一 rerank。rewrite 的安全性由规则保护（否定词、核心故障词、最小长度），不安全时降级为仅使用原始查询。

### 创建工单简化

普通用户创建工单只需要传 title + description，type/category/priority/typeProfile 由 `TicketCreateEnrichmentService` 根据规则自动补全。用户显式传入的字段不会被覆盖。Agent 创建工单也经过同一 enrichment 流程。

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

## 核心接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/agent/chat` | POST | Agent 同步对话 |
| `/api/agent/chat/stream` | POST | Agent SSE 事件级流式对话（非 token 级流式），事件：accepted、route、status、delta、final、error、done |
| `/api/rag/search` | GET | 直接 RAG 检索，返回 retrievalPath / fallbackUsed / hits |
| `/api/admin/dashboard` | GET | 管理端 Dashboard 指标（仅 ADMIN） |

## P0 验收方式

### 运行测试

```bash
mvn -pl smart-ticket-agent -am test
mvn -pl smart-ticket-biz -am test
mvn -pl smart-ticket-rag -am test
mvn -pl smart-ticket-api -am test
```

### 启动后确认 RAG 主路径

启动日志应出现：

```text
RAG 路径状态：embeddingEnabled=true, vectorStoreEnabled=true, vectorStoreReady=true, retrievalPath=PGVECTOR
```

### 运行 E2E 脚本

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\run_rag_pgvector_e2e.ps1
```

脚本成功后应看到：

```text
[OK] RAG search OK
retrievalPath=PGVECTOR
fallbackUsed=False
hits count=5
```

## 快速启动

见 [docs/quick-start.md](docs/quick-start.md)。

常用命令：

```bash
mvn test
```

```bash
mvn -pl smart-ticket-app -am spring-boot:run
```

> Dashboard retrievalPath 是配置视角展示，真实检索路径以 `/api/rag/search` 返回值和 RetrievalService 日志为准。RAG 评估当前是测试级指标计算工具，不是完整线上评估平台，覆盖 Recall@3、Recall@5、MRR。

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
- [pgvector 性能边界](docs/pgvector-performance-boundary.md) — pgvector 适用场景、性能保障和演进路线
- [Agent 稳定性验收](docs/agent-stability-acceptance.md) — 请求限流、session 锁、预算超限、降级策略、异常恢复等稳定性场景
- [压测脚本](scripts/agent-smoke-load.ps1) — Agent 接口轻量压测
- [E2E 验收脚本](scripts/run_rag_pgvector_e2e.ps1) — 完整端到端 RAG + pgvector 验收流程：创建工单 -> 关闭 -> 等待知识构建 -> Agent 检索 -> 直接 RAG 检索

### 当前完成能力

- P0 工单 CRUD、状态流转、权限控制
- P0 Agent 对话（同步 + SSE）、意图路由、确定性写命令、只读 ReAct、Session/Memory、Trace/Metrics
- P0 RAG 知识构建链（MySQL + pgvector）、query rewrite、rerank、直接检索接口
- P1 Dashboard 管理端指标、限流降级、SSE done 事件、专用线程池、历史检索关键词优化、知识指标语义修复
- P0 创建工单只需 title + description，系统自动补全 type/category/priority/typeProfile（TicketCreateEnrichmentService）
- P0 Idempotency-Key 规范：header/body 冲突返回 400
- P1 Agent 创建工单自动补全 typeProfile（CreateTicketTool 经 enrichment 流程）
- P1 RAG 双路召回：originalQuery + rewrittenQuery，安全规则保护否定词和核心故障词，不安全时降级
- P2 Agent Memory source/confidence/expiresAt 可靠性元数据
- P2 pgvector 性能边界与演进路线文档
