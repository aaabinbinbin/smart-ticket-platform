# 给 Claude 的项目调整提示词：智能工单平台业务体验、幂等、RAG 与 Agent Memory 改造

> 使用方式：  
> 将本文完整复制给 Claude。  
> 要求 Claude 先重新审查当前仓库代码，再基于真实代码做修改，禁止基于旧缓存或假设直接改。

---

## 一、你的角色

你是一个资深 Java 后端工程师兼 AI 应用工程师。请你对当前仓库进行代码级审查与改造，目标是提升智能工单平台在以下方面的完整性和面试自洽性：

```text
1. 普通用户创建工单的体验
2. Idempotency-Key 设计规范性
3. Agent 创建工单链路的结构化字段补全
4. RAG 检索召回率和 query rewrite 安全性
5. Agent memory 的准确性边界和可靠性
```

请注意：

```text
不要只改文档。
不要只给建议。
需要基于当前真实代码做具体实现。
```

---

## 二、开始前必须做的事情

请先重新查看当前仓库代码，避免旧数据污染。

至少检查以下模块和文件，实际路径以仓库为准：

```text
smart-ticket-api
  - TicketController
  - CreateTicketRequestDTO
  - AgentController

smart-ticket-biz
  - TicketCommandService
  - TicketWorkflowService
  - TicketServiceSupport
  - TicketIdempotencyService
  - TicketTypeProfileService
  - TicketCreateCommandDTO

smart-ticket-agent
  - AgentFacade
  - AgentExecutionGuard
  - SkillRegistry
  - CreateTicketTool
  - SearchHistoryTool
  - AgentMemoryService
  - IntentRouter
  - AgentPlanner

smart-ticket-rag
  - RetrievalService
  - QueryRewriteService
  - RerankService
  - Knowledge build task / listener / processor 相关代码

smart-ticket-infra
  - RedisKeys
  - Redis 相关配置

docs
  - agent-architecture.md
  - rag-evaluation.md
  - p0-acceptance-record.md
  - README.md
```

要求：

```text
1. 先总结当前实现状态。
2. 标记哪些能力已经实现，哪些只是文档中提到但代码未完成。
3. 再开始修改。
```

---

## 三、总体改造目标

当前项目的方向是：

```text
工单业务系统 + 受控 Agent + RAG 知识闭环
```

本次改造不追求大拆大改，而是让项目在以下问题上更加自洽：

```text
1. 普通用户不需要手动填写复杂结构化字段。
2. 创建工单幂等设计更符合 HTTP API 规范。
3. Agent 创建工单时能够自动补全 type、category、priority、typeProfile。
4. RAG 不能只靠感觉，需要可评估、可回退、可防止 query rewrite 跑偏。
5. Agent memory 不能伪装成事实库，必须有来源、置信度、过期时间和事实校验边界。
```

---

# P0 任务：创建工单体验与幂等键规范化

## P0-1：简化普通用户创建工单输入

### 背景

当前业务内部 `TicketCreateCommandDTO` 字段较多，例如：

```text
title
description
type
typeProfile
category
priority
idempotencyKey
```

这对业务建模是合理的，但普通用户创建工单时通常只愿意写标题和描述，不会手动拆出 `typeProfile`。

### 目标

普通用户创建工单时只需要：

```json
{
  "title": "测试环境无法登录",
  "description": "登录时报 500，影响研发自测，我已经清理缓存但没用"
}
```

系统自动补全：

```text
type
category
priority
typeProfile
```

### 实现要求

请新增或完善一个服务，例如：

```java
TicketCreateEnrichmentService
```

职责：

```text
输入：
  title
  description
  可选 type
  可选 category
  可选 priority
  可选 typeProfile

输出：
  完整的 TicketCreateCommandDTO 所需字段
```

要求：

```text
1. 如果用户显式传了 type/category/priority/typeProfile，优先尊重用户输入。
2. 如果用户没有传，则通过规则自动推断。
3. 初期先用规则实现，不要强依赖 LLM。
4. 后续可以预留 LLM enrichment 扩展点，但必须有超时和降级设计。
```

### 推荐规则

#### type 推断

```text
包含 “权限 / 账号 / 开通 / 角色 / 登录账号 / 访问不了某资源”：
  ACCESS_REQUEST

包含 “变更 / 发布 / 上线 / 回滚 / 配置修改 / 数据库变更”：
  CHANGE_REQUEST

其他：
  INCIDENT
```

#### category 推断

```text
包含 “登录 / 账号 / 密码 / 权限”：
  ACCOUNT

包含 “系统 / 服务 / 接口 / 500 / 报错 / 异常”：
  SYSTEM

包含 “测试环境 / 生产环境 / 环境 / 部署”：
  ENVIRONMENT

其他：
  OTHER
```

#### priority 推断

```text
包含 “生产 / 全部用户 / 大面积 / 阻塞 / 紧急 / 无法使用 / 严重”：
  URGENT 或 HIGH

包含 “影响测试 / 影响部分用户 / 较急”：
  HIGH

默认：
  MEDIUM
```

#### typeProfile 生成

如果是 INCIDENT：

```json
{
  "symptom": "从 description 中提取，提取不到则使用 description",
  "impactScope": "从 description 中提取，提取不到则使用 待确认"
}
```

如果是 ACCESS_REQUEST：

```json
{
  "accountId": "待确认",
  "targetResource": "从 description 中提取，提取不到则使用 待确认",
  "requestedRole": "待确认",
  "justification": "使用 description"
}
```

如果是 CHANGE_REQUEST：

```json
{
  "changeTarget": "从 description 中提取，提取不到则使用 待确认",
  "changeWindow": "待确认",
  "rollbackPlan": "待确认",
  "impactScope": "从 description 中提取，提取不到则使用 待确认"
}
```

### 验收标准

```text
1. 用户只传 title + description 可以成功创建工单。
2. 创建出的工单有合理的 type/category/priority/typeProfile。
3. 原有显式传 type/category/priority/typeProfile 的能力不被破坏。
4. 原有测试不失败。
5. 新增单元测试覆盖：
   - INCIDENT 自动补全
   - ACCESS_REQUEST 自动补全
   - CHANGE_REQUEST 自动补全
   - 用户显式传字段时不被覆盖
```

---

## P0-2：规范 Idempotency-Key

### 背景

当前接口同时支持：

```text
请求头 Idempotency-Key
请求体 idempotencyKey
```

这会导致语义重复。如果两边都传且不一致，当前实现可能请求头优先，但更合理的是直接拒绝。

### 目标

推荐最终 API：

```http
POST /api/tickets
Idempotency-Key: <uuid>
Content-Type: application/json

{
  "title": "测试环境无法登录",
  "description": "登录时报 500，影响研发自测"
}
```

### 实现要求

请修改 `TicketController` 中 Idempotency-Key 解析逻辑。

推荐策略：

```text
1. header 有值，body 没值：
   使用 header。

2. header 没值，body 有值：
   兼容使用 body，但在注释或文档中标记为 deprecated。

3. header 和 body 都有值且相同：
   使用该值。

4. header 和 body 都有值但不同：
   返回 400 Bad Request。
```

可参考伪代码：

```java
private String resolveIdempotencyKey(String headerValue, String bodyValue) {
    boolean hasHeader = headerValue != null && !headerValue.isBlank();
    boolean hasBody = bodyValue != null && !bodyValue.isBlank();

    if (hasHeader && hasBody && !headerValue.equals(bodyValue)) {
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 请求头和请求体不一致");
    }

    if (hasHeader) {
        return headerValue.trim();
    }

    if (hasBody) {
        return bodyValue.trim();
    }

    return null;
}
```

### 验收标准

```text
1. 只传 header 可以创建。
2. 只传 body 仍然兼容。
3. header 和 body 相同可以创建。
4. header 和 body 不同返回 400。
5. Redis 幂等 key 仍然按 userId 隔离。
6. 不同用户使用相同 Idempotency-Key 不互相影响。
```

---

# P1 任务：Agent 创建工单链路补全 typeProfile

## P1-1：修复 Agent 创建工单没有 typeProfile 的问题

### 背景

普通 HTTP 创建工单可以传 `typeProfile`，但 Agent 的 `CreateTicketTool` 通常只抽取 title、description、type、category、priority 等字段。如果业务层要求校验 typeProfile，Agent 创建工单可能失败。

### 目标

Agent 创建工单时也必须进入统一的 enrichment 流程。

### 实现要求

请调整：

```text
CreateTicketTool
TicketCommandService
TicketCreateEnrichmentService
```

推荐链路：

```text
Agent 参数抽取
  ↓
CreateTicketTool
  ↓
TicketCreateEnrichmentService.enrich()
  ↓
TicketCommandService.createTicket()
```

要求：

```text
1. Agent 只需要提供 title + description 也能创建成功。
2. 如果 Agent 已经抽取到 type/category/priority，enrichment 不要覆盖。
3. 如果缺 typeProfile，由 enrichment 自动生成。
4. 创建前相似案例检索仍然保留。
```

### 验收标准

```text
1. Agent 输入“帮我创建一个工单，测试环境登录时报 500，影响自测”可以成功创建。
2. 创建出的工单 typeProfile 不为空。
3. 不破坏原来的相似案例检索。
4. 新增测试覆盖 Agent 创建工单最小参数场景。
```

---

# P1 任务：RAG 召回率与 query rewrite 安全性

## P1-2：RAG 改成 originalQuery + rewrittenQuery 双路召回

### 背景

当前 query rewrite 如果过度改写，可能导致检索意图变化或丢失关键信息。

### 目标

query rewrite 只用于增强检索，不替代原始 query。

推荐流程：

```text
originalQuery
  ↓
QueryRewriteService 生成 rewrittenQuery
  ↓
originalQuery 检索
  ↓
rewrittenQuery 检索
  ↓
合并结果
  ↓
去重
  ↓
rerank
```

### 实现要求

请新增结果对象，例如：

```java
public class RewrittenQuery {
    private String originalQuery;
    private String rewrittenQuery;
    private String rewriteReason;
    private double confidence;
    private boolean safeToUse;
}
```

如果不想新增类，也可以在现有返回结构上做等价改造。

### 安全规则

```text
1. rewrite 不能删除否定词：
   不、不要、不能、无法、拒绝、失败

2. rewrite 不能删除核心故障词：
   报错、异常、500、超时、登录失败、权限不足

3. 如果 rewrite 后长度少于原文 50%，认为可能过度改写，safeToUse=false。

4. 如果 safeToUse=false，只使用 originalQuery。
```

### 验收标准

```text
1. RAG 检索默认保留 originalQuery。
2. rewrittenQuery 只在 safeToUse=true 时使用。
3. 双路结果能去重。
4. rerank 在合并结果后执行。
5. 新增单元测试覆盖：
   - 正常改写
   - 否定词保护
   - 长度过短保护
   - 双路结果去重
```

---

## P1-3：补充 RAG 评估集和指标

### 背景

RAG 召回率不能靠主观感觉，需要 Recall@K 和 MRR 评估。

### 目标

完善或新增 RAG 评估相关代码和文档。

### 指标定义

```text
Recall@3：
  前 3 条结果是否包含期望知识。

Recall@5：
  前 5 条结果是否包含期望知识。

MRR：
  第一个正确命中结果的倒数排名。
```

### 实现要求

如果当前已有评估文档或测试，请补齐；如果没有，请新增：

```text
docs/rag-evaluation.md
src/test/.../RagEvaluationTest.java 或等价测试
```

评估集可以先用小规模样例：

```json
[
  {
    "query": "登录系统时报 500",
    "expectedKnowledgeIds": [101, 102]
  },
  {
    "query": "申请访问测试环境数据库",
    "expectedKnowledgeIds": [201]
  }
]
```

### 验收标准

```text
1. 可以计算 Recall@3。
2. 可以计算 Recall@5。
3. 可以计算 MRR。
4. 文档解释这些指标的意义。
5. README 或 docs 中说明如何运行评估。
```

---

# P2 任务：Agent Memory 可靠性增强

## P2-1：给记忆增加 source、confidence、expiresAt

### 背景

当前 Agent memory 更像上下文缓存和偏好记录，不能作为事实源。为了避免记忆误导 Agent，需要增加来源、置信度和过期时间。

### 目标

记忆结构建议支持：

```json
{
  "key": "commonCategory",
  "value": "SYSTEM",
  "source": "USER_EXPLICIT",
  "confidence": 0.92,
  "expiresAt": "2026-05-01T00:00:00"
}
```

### 推荐 source 类型

```text
USER_EXPLICIT：
  用户明确说的。

TOOL_RESULT：
  工具执行结果。

INFERRED：
  规则推断结果。

LLM_EXTRACTED：
  模型抽取结果。
```

优先级：

```text
USER_EXPLICIT > TOOL_RESULT > INFERRED > LLM_EXTRACTED
```

### 实现要求

请检查并改造：

```text
AgentMemoryService
AgentWorkingMemory
AgentUserPreferenceMemory
AgentTicketDomainMemory
相关 Mapper / 表结构 / Redis 结构
```

要求：

```text
1. 新增 source 字段。
2. 新增 confidence 字段。
3. 新增 expiresAt 字段或 TTL 策略。
4. 低置信度记忆只能用于推荐，不能自动执行。
5. 高风险动作不能依赖记忆直接执行。
```

### 验收标准

```text
1. 用户偏好记忆有 source/confidence/expiresAt。
2. 工单领域记忆仍然有短 TTL。
3. 过期记忆不会被使用。
4. 冲突时优先使用当前用户明确输入。
5. 涉及工单状态、处理人、审批状态时必须实时查数据库。
```

---

## P2-2：明确记忆不是事实源

### 目标

在代码和文档中明确：

```text
数据库 / Tool 实时查询结果 = 权威事实
Agent memory = 上下文缓存和偏好线索
LLM 生成内容 = 表达层，不是事实源
```

### 实现要求

请在以下位置补充说明：

```text
docs/agent-architecture.md
README.md
AgentMemoryService 类注释
关键方法注释
```

要求中文注释清晰，类、方法、字段使用合适注释。

注释规范：

```text
类和复杂方法使用 /** ... */
字段和方法内部关键逻辑使用 // ...
```

---

# P2 任务：pgvector 性能与生产边界说明

## P2-3：补充 pgvector 性能边界文档

### 背景

pgvector 适合当前项目阶段，但不能声称天然支持所有高峰场景。

### 目标

在文档中补充 pgvector 的适用边界和演进方案。

### 需要说明

```text
1. 当前阶段选择 pgvector 的原因：
   - 架构简单
   - 复用 PostgreSQL
   - 易于和业务元数据结合
   - Spring AI 集成方便

2. 性能保障手段：
   - topK 限制
   - HNSW / IVFFlat 索引
   - 元数据过滤
   - 连接池配置
   - 缓存热门查询
   - 限流与降级
   - 压测验证

3. 演进路线：
   - 阶段 1：PostgreSQL + pgvector
   - 阶段 2：pgvector + 索引 + 缓存 + 评估集
   - 阶段 3：Milvus / Qdrant / Elasticsearch vector / OpenSearch vector
```

---

# 四、测试要求

请在修改后运行项目已有测试。

如果测试无法全部运行，请至少完成：

```text
1. 与 Ticket 创建相关的单元测试。
2. Idempotency-Key 解析测试。
3. TicketCreateEnrichmentService 测试。
4. CreateTicketTool 最小参数创建测试。
5. QueryRewriteService 安全改写测试。
6. RetrievalService 双路召回去重测试。
7. AgentMemoryService source/confidence/expiresAt 相关测试。
```

如果某些测试因为环境依赖无法运行，例如 MySQL、Redis、PostgreSQL、RabbitMQ 缺失，请说明：

```text
1. 哪些测试跑了。
2. 哪些测试没跑。
3. 没跑的原因是什么。
4. 如何在本地补齐环境后运行。
```

---

# 五、文档更新要求

请同步更新：

```text
README.md
docs/agent-architecture.md
docs/rag-evaluation.md
docs/p0-acceptance-record.md
```

必须写清楚：

```text
1. 普通用户创建工单只需要 title + description。
2. 复杂字段由系统 enrichment 自动补全。
3. Idempotency-Key 推荐只放 Header。
4. header/body 冲突会返回 400。
5. RAG 使用 originalQuery + rewrittenQuery 双路召回。
6. query rewrite 不参与业务意图判断。
7. Agent memory 不是事实源。
8. pgvector 是当前阶段选择，存在性能边界和演进路线。
```

---

# 六、代码质量要求

请遵守以下要求：

```text
1. 不要破坏原有接口兼容性，除非明确说明。
2. 不要让 Controller 承担复杂业务逻辑。
3. 不要让 Agent 直接操作数据库。
4. Tool 必须调用业务 Service。
5. 业务 Service 必须保留最终权限、状态机、参数校验。
6. RAG rewrite 不能替代原始 query。
7. 记忆不能替代数据库事实。
8. 高风险动作必须确认。
9. 新增代码要有中文注释。
10. 复杂方法内部关键逻辑要有 // 注释。
```

---

# 七、请最终输出以下内容

完成后请给出：

```text
1. 修改了哪些文件。
2. 每个文件修改了什么。
3. 新增了哪些类。
4. 新增了哪些测试。
5. 已运行哪些测试，结果如何。
6. 哪些地方没有完成，原因是什么。
7. 下一步建议。
```

请不要只输出笼统总结，要基于真实代码和真实测试结果回答。
