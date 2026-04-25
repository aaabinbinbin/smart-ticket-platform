```
# 智能工单平台代码修复任务：创建工单体验、Agent Tool、RAG 与幂等策略收口

你现在要基于当前本地仓库代码进行修复，不要使用旧缓存，不要只看文档。请先重新审查代码，再修改。项目是 smart-ticket-platform，用于 Java 后端 / Agent 开发实习简历项目，要求业务逻辑自洽、代码能编译、核心链路可测试。

---

## 一、先做代码审查，确认当前真实状态

请先检查以下文件，确认当前实现，不要直接动手改：

```text
smart-ticket-api/src/main/java/com/smartticket/api/controller/ticket/TicketController.java
smart-ticket-api/src/main/java/com/smartticket/api/dto/ticket/CreateTicketRequestDTO.java

smart-ticket-biz/src/main/java/com/smartticket/biz/service/ticket/TicketCommandService.java
smart-ticket-biz/src/main/java/com/smartticket/biz/service/ticket/TicketWorkflowService.java
smart-ticket-biz/src/main/java/com/smartticket/biz/dto/ticket/TicketCreateCommandDTO.java
smart-ticket-biz/src/main/java/com/smartticket/biz/service/type/TicketTypeProfileService.java
smart-ticket-biz/src/main/java/com/smartticket/biz/service/ticket/TicketIdempotencyService.java
smart-ticket-biz/src/main/java/com/smartticket/biz/service/ticket/TicketServiceSupport.java

smart-ticket-agent/src/main/java/com/smartticket/agent/tool/ticket/CreateTicketTool.java
smart-ticket-agent/src/main/java/com/smartticket/agent/tool/ticket/QueryTicketTool.java
smart-ticket-agent/src/main/java/com/smartticket/agent/tool/ticket/TransferTicketTool.java
smart-ticket-agent/src/main/java/com/smartticket/agent/memory/AgentMemoryService.java
smart-ticket-agent/src/main/java/com/smartticket/agent/service/AgentFacade.java
smart-ticket-agent/src/main/java/com/smartticket/agent/execution/AgentExecutionGuard.java

smart-ticket-rag/src/main/java/com/smartticket/rag/service/RetrievalService.java
smart-ticket-rag/src/main/java/com/smartticket/rag/service/QueryRewriteService.java
smart-ticket-rag/src/main/java/com/smartticket/rag/service/RerankService.java

smart-ticket-infra/src/main/java/com/smartticket/infra/redis/RedisKeys.java

README.md
docs/agent-architecture.md
docs/rag-evaluation.md
docs/p0-acceptance-record.md
```

先输出一段当前状态总结，重点确认：

```
1. CreateTicketRequestDTO 里面是否还保留 idempotencyKey。
2. TicketController 当前 Idempotency-Key 是否是 Header 推荐 + body 兼容。
3. Header 和 body 都传且不一致时是否已经返回错误。
4. TicketCommandService 创建工单时是否仍然强校验 typeProfile。
5. 用户只传 title + description 是否可以创建成功。
6. CreateTicketTool 是否没有传 typeProfile。
7. CreateTicketTool 的 requiredFields 是否仍包含 CATEGORY / PRIORITY。
8. smart-ticket-agent 里面是否存在错误 import，例如：
   com.smartticket.biz.dto.*
   com.smartticket.biz.service.*
   但真实路径是：
   com.smartticket.biz.dto.ticket.*
   com.smartticket.biz.service.ticket.*
9. RAG 是否仍然只使用 rewrittenQuery，而不是 originalQuery + rewrittenQuery 双路召回。
10. AgentMemoryService 是否已经有 source / confidence / expiresAt。
```

------

# 二、P0：优先修复会影响编译和创建工单主链的问题

## P0-1：修复 smart-ticket-agent 中可能错误的 import 路径

重点检查：

```
CreateTicketTool
QueryTicketTool
TransferTicketTool
其他 smart-ticket-agent 下导入 biz dto / service 的类
```

如果发现类似：

```
import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.service.TicketCommandService;
```

请改成当前项目真实存在的路径，例如：

```
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.service.ticket.TicketCommandService;
```

注意不要凭空猜包名，要以当前代码真实路径为准。

验收标准：

```
1. smart-ticket-agent 模块可以编译。
2. 不再存在导入不存在包路径的情况。
3. 不破坏原有 Agent 主链结构。
```

------

## P0-2：新增 TicketCreateEnrichmentService，解决用户只传 title + description 创建失败的问题

### 背景

当前用户侧创建工单虽然只强制要求 title 和 description，但业务层可能仍然会因为 typeProfile 缺失而失败。

当前问题可能是：

```
用户只传 title + description
  ↓
type 为空，业务层默认 INCIDENT
  ↓
TicketTypeProfileService.validate() 校验 INCIDENT 必填字段
  ↓
typeProfile 为空
  ↓
创建失败
```

这会导致普通用户体验不合理。

### 目标

普通用户创建工单时只需要：

```
{
  "title": "测试环境无法登录",
  "description": "登录时报 500，影响研发自测，我已经清理缓存但没用"
}
```

系统自动补全：

```
type
category
priority
typeProfile
```

### 实现要求

新增一个业务服务，例如：

```
TicketCreateEnrichmentService
```

建议放在：

```
smart-ticket-biz/src/main/java/com/smartticket/biz/service/ticket/
```

职责：

```
输入：
  TicketCreateCommandDTO 或 title / description / 可选 type / category / priority / typeProfile

输出：
  补全后的 TicketCreateCommandDTO 或补全后的字段对象
```

必须满足：

```
1. 用户显式传了 type/category/priority/typeProfile 时，不要覆盖用户输入。
2. 用户没传时，通过规则自动推断。
3. 先用规则实现，不要强依赖 LLM。
4. 后续可以预留 LLM enrichment 扩展点，但当前不要引入不稳定外部调用。
5. HTTP 创建工单和 Agent 创建工单都要走同一套 enrichment 逻辑。
```

### 推荐推断规则

#### type 推断

```
包含 “权限 / 账号 / 开通 / 角色 / 访问 / 授权 / 申请权限”：
  ACCESS_REQUEST

包含 “变更 / 发布 / 上线 / 回滚 / 配置修改 / 数据库变更”：
  CHANGE_REQUEST

其他：
  INCIDENT
```

#### category 推断

请根据当前项目已有枚举取值来写，不要创造不存在的枚举。

大致规则：

```
包含 “登录 / 账号 / 密码 / 权限”：
  ACCOUNT 或当前项目中对应的账号类分类

包含 “系统 / 服务 / 接口 / 500 / 报错 / 异常”：
  SYSTEM 或当前项目中对应的系统类分类

包含 “测试环境 / 生产环境 / 环境 / 部署”：
  ENVIRONMENT 或当前项目中对应的环境类分类

其他：
  OTHER 或当前项目默认分类
```

#### priority 推断

请根据当前项目已有优先级枚举取值来写，不要创造不存在的枚举。

大致规则：

```
包含 “生产 / 全部用户 / 大面积 / 阻塞 / 紧急 / 无法使用 / 严重”：
  URGENT 或 HIGH

包含 “影响测试 / 影响部分用户 / 较急”：
  HIGH

默认：
  MEDIUM
```

#### typeProfile 自动生成

请以 `TicketTypeProfileService.validate()` 当前真实要求的字段为准，不要凭空造字段。

一般思路：

如果是 INCIDENT：

```
{
  "symptom": "优先从 description 提取，提取不到就使用 description",
  "impactScope": "优先从 description 提取，提取不到就使用 待确认"
}
```

如果是 ACCESS_REQUEST：

```
{
  "accountId": "待确认",
  "targetResource": "优先从 description 提取，提取不到就使用 待确认",
  "requestedRole": "待确认",
  "justification": "使用 description"
}
```

如果是 CHANGE_REQUEST：

```
{
  "changeTarget": "优先从 description 提取，提取不到就使用 待确认",
  "changeWindow": "待确认",
  "rollbackPlan": "待确认",
  "impactScope": "优先从 description 提取，提取不到就使用 待确认"
}
```

如果当前项目还有其他类型，例如 ENVIRONMENT_REQUEST / CONSULTATION 等，也请按照 `TicketTypeProfileService.validate()` 的真实字段补齐默认值。

### 接入点

请在创建工单主链接入 enrichment。

推荐方式：

```
TicketController / CreateTicketTool
  ↓
构造 TicketCreateCommandDTO
  ↓
TicketCommandService.createTicket()
  ↓
TicketCommandService 内部调用 TicketCreateEnrichmentService
  ↓
再执行 validate / insert / save profile
```

更推荐在 `TicketCommandService.createTicket()` 内部统一调用 enrichment，这样所有入口都能受益。

验收标准：

```
1. HTTP 创建工单只传 title + description 可以成功。
2. Agent 创建工单只传 title + description 可以成功。
3. 自动生成的 typeProfile 能通过 TicketTypeProfileService.validate()。
4. 用户显式传入的 type/category/priority/typeProfile 不被覆盖。
5. 原有完整字段创建工单能力不被破坏。
```

------

## P0-3：修复 CreateTicketTool 的必填字段和 typeProfile 闭环

### 背景

当前 CreateTicketTool 可能存在两个问题：

```
1. requiredFields 包含 CATEGORY / PRIORITY，导致 Agent 会追问用户不想填写的字段。
2. 创建 TicketCreateCommandDTO 时没有传 typeProfile，导致业务层校验失败。
```

### 目标

Agent 创建工单时，用户自然语言里只要有标题和描述即可创建。

### 修改要求

请检查 `CreateTicketTool.metadata()`，把必填字段改成：

```
TITLE
DESCRIPTION
```

不要再把以下字段作为必填：

```
CATEGORY
PRIORITY
TYPE
TYPE_PROFILE
```

这些字段应该由 enrichment 自动推断和补全。

CreateTicketTool 执行链路应保留：

```
1. 参数抽取 title / description。
2. 创建前调用 RAG 相似案例检索。
3. 调用 TicketCommandService 创建工单。
4. 返回创建结果和相似案例摘要。
```

验收标准：

```
1. 用户说“帮我创建一个工单，测试环境登录时报 500，影响自测”时，不需要追问 category / priority。
2. Tool 最终可以成功创建工单。
3. 创建出的工单 typeProfile 不为空。
4. 创建前相似案例检索仍保留。
```

------

# 三、P0：Idempotency-Key 策略收口，不要误删兼容字段

## 当前要求

我本地代码里 `CreateTicketRequestDTO` 仍然保留了 body 里的 `idempotencyKey` 字段。

这次不要强行删除 body 字段，先按兼容方案收口：

```
1. 推荐使用 Header: Idempotency-Key。
2. body.idempotencyKey 暂时保留，作为 deprecated 兼容字段。
3. Header 和 body 都存在且相同：允许。
4. Header 和 body 都存在但不同：返回 400。
5. Header 存在，body 不存在：使用 Header。
6. Header 不存在，body 存在：兼容使用 body。
7. 两者都不存在：允许创建，但不启用幂等。
```

### 需要检查和修正的地方

```
TicketController.resolveIdempotencyKey()
CreateTicketRequestDTO.idempotencyKey 注释
Swagger Schema 描述
README / docs 说明
相关测试
```

### 文档表述必须统一

不要写“body 里已经删除 Idempotency-Key”。

应统一写成：

```
Idempotency-Key 推荐通过 HTTP Header 传递。
请求体中的 idempotencyKey 字段仅作为历史兼容字段保留，后续可废弃。
如果 Header 和 body 同时传入且不一致，接口返回 400。
```

### 验收标准

```
1. 只传 Header 可以创建。
2. 只传 body 可以创建，但文档说明这是兼容方式。
3. Header 和 body 相同可以创建。
4. Header 和 body 不同返回 400。
5. 不同用户使用相同 Idempotency-Key 不互相影响。
6. Redis 幂等 key 仍然按 userId 隔离。
```

------

# 四、P1：RAG query rewrite 改成安全双路召回

## 背景

当前 RAG query rewrite 如果直接替换原始 query，可能导致意图跑偏或丢失关键信息。

例如：

```
用户：不要创建工单，我只是想查类似问题
```

如果 rewrite 删除了“不 / 不要”等词，可能导致语义变化。

## 目标

query rewrite 只用于增强检索，不替代原始 query。

推荐流程：

```
originalQuery
  ↓
QueryRewriteService 生成 rewrittenQuery
  ↓
判断 rewrittenQuery 是否安全
  ↓
originalQuery 检索
  ↓
如果 rewrittenQuery 安全，再用 rewrittenQuery 检索
  ↓
合并结果
  ↓
去重
  ↓
rerank
```

## 实现要求

新增或调整一个对象，例如：

```
public class RewrittenQuery {
    private String originalQuery;
    private String rewrittenQuery;
    private String rewriteReason;
    private double confidence;
    private boolean safeToUse;
}
```

如果项目里不适合新增这个类，也可以采用等价实现，但必须保留：

```
originalQuery
rewrittenQuery
safeToUse
confidence 或 reason
```

## 安全规则

至少实现以下保护：

```
1. rewrite 不能删除否定词：
   不、不要、不能、无法、拒绝、失败

2. rewrite 不能删除核心故障词：
   报错、异常、500、超时、登录失败、权限不足

3. 如果 rewrite 后长度少于原文 50%，认为可能过度改写，safeToUse=false。

4. 如果 rewrittenQuery 和 originalQuery 基本相同，可以只检索一次，避免重复查询。

5. 如果 safeToUse=false，只使用 originalQuery。
```

## RetrievalService 改造要求

请检查当前 `RetrievalService.retrieve()`。

目标是从：

```
只用 rewrittenQuery 检索
```

改成：

```
originalQuery + rewrittenQuery 双路召回
```

注意：

```
1. pgvector 路径和 fallback 路径都要考虑。
2. 合并结果要去重。
3. 去重后再 rerank。
4. topK 策略要合理，不能无限放大检索量。
```

建议：

```
internalTopK = min(max(topK * 2, topK), 20)
displayTopK = topK
```

如果当前结构不方便一次性做完整双路召回，至少先做到：

```
1. 保留 originalQuery。
2. safeToUse=false 时回退 originalQuery。
3. 文档注明下一步会做双路合并。
```

但优先尽量完整实现。

## 验收标准

```
1. 原始 query 不会被 rewrite 完全替代。
2. rewrite 不安全时只用 originalQuery。
3. rewrite 安全时可以 original + rewritten 双路召回。
4. 双路结果会去重。
5. rerank 在合并之后执行。
6. 增加 QueryRewriteService 测试。
7. 增加 RetrievalService 双路召回测试，必要时用 mock。
```

------

# 五、P1：补齐 RAG 评估集和指标

## 目标

RAG 召回率不能靠主观感觉，需要可量化评估。

请检查并完善：

```
docs/rag-evaluation.md
相关测试或工具类
```

至少说明并尽可能实现以下指标：

```
Recall@3
Recall@5
MRR
```

定义：

```
Recall@K：
  前 K 条结果中是否包含期望知识。

MRR：
  第一个正确命中结果排名的倒数。
```

可以先用小规模样例评估集，不要求一开始连接真实数据库。

示例：

```
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

验收标准：

```
1. 文档说明 Recall@3 / Recall@5 / MRR。
2. 有样例评估集。
3. 有可运行的指标计算测试或工具。
4. README 或 docs 说明如何运行。
```

------

# 六、P1/P2：Agent Memory 可靠性检查与文档收口

## 当前目标

如果当前代码已经实现了：

```
source
confidence
expiresAt
MemorySource
```

请不要重复大改，只做检查、补测试和补文档。

请确认：

```
1. AgentMemoryService 注释是否明确：memory 不是事实源。
2. AgentUserPreferenceMemory 是否有 source/confidence/expiresAt。
3. AgentTicketDomainMemory 是否有 source/confidence/expiresAt 或 TTL。
4. MemorySource 是否包含：
   USER_EXPLICIT
   TOOL_RESULT
   INFERRED
   LLM_EXTRACTED
5. 高风险动作是否不会仅凭 memory 自动执行。
6. 工单状态、处理人、审批状态等事实是否仍以数据库 / Tool 实时查询为准。
```

## 文档统一表述

请在 `docs/agent-architecture.md` 和 README 中明确：

```
数据库 / Tool 实时查询结果 = 权威事实
Agent memory = 上下文缓存和偏好线索
LLM 生成内容 = 表达层，不是事实源
```

## 可选优化

如果当前 `AgentMemoryService.mergePreference()` 把所有来源都写成 `TOOL_RESULT`，请评估是否需要细化：

```
用户当前明确输入：
  USER_EXPLICIT

工具执行结果：
  TOOL_RESULT

规则推断：
  INFERRED

LLM 抽取：
  LLM_EXTRACTED
```

如果改动风险较大，可以先只补 TODO 和文档，不强行大改。

------

# 七、文档同步更新

请同步更新：

```
README.md
docs/agent-architecture.md
docs/rag-evaluation.md
docs/p0-acceptance-record.md
```

必须统一说明：

```
1. 普通用户创建工单最小输入是 title + description。
2. type/category/priority/typeProfile 由系统 enrichment 自动补全。
3. Idempotency-Key 推荐放在 Header。
4. body.idempotencyKey 暂时作为 deprecated 兼容字段保留。
5. Header 和 body 冲突会返回 400。
6. Agent 创建工单不要求用户手动提供 category / priority / typeProfile。
7. RAG query rewrite 不参与业务意图判断。
8. RAG 检索保留 originalQuery，并在安全时使用 rewrittenQuery 增强召回。
9. Agent memory 不是事实源。
10. pgvector 是当前阶段的向量检索方案，但需要索引、限流、缓存和压测保证高峰表现。
```

------

# 八、测试要求

请至少新增或更新以下测试：

```
1. TicketCreateEnrichmentServiceTest
   - INCIDENT 自动补全
   - ACCESS_REQUEST 自动补全
   - CHANGE_REQUEST 自动补全
   - 用户显式字段不被覆盖

2. TicketControllerIdempotencyKeyTest 或等价测试
   - 只传 Header
   - 只传 body
   - Header 和 body 相同
   - Header 和 body 不同返回 400

3. CreateTicketToolTest
   - 只提供 title + description 可以创建
   - 不要求 category / priority
   - typeProfile 最终不为空

4. QueryRewriteServiceTest
   - 正常 rewrite
   - 否定词保护
   - 故障关键词保护
   - rewrite 过短时 safeToUse=false

5. RetrievalServiceTest
   - originalQuery 保留
   - rewrittenQuery 安全时双路召回
   - 双路结果去重
   - rewrite 不安全时只用 originalQuery

6. AgentMemoryServiceTest
   - 如果当前已有 source/confidence/expiresAt，则补充基本测试
```

如果某些测试因为数据库、Redis、PostgreSQL、RabbitMQ 或 API Key 缺失无法运行，请说明：

```
1. 哪些测试已运行。
2. 哪些测试没运行。
3. 没运行的原因。
4. 本地如何补齐环境后运行。
```

------

# 九、代码质量要求

请遵守：

```
1. 不要让 Controller 写复杂业务逻辑。
2. 不要让 Agent 直接操作数据库。
3. Tool 必须调用业务 Service。
4. TicketCommandService 必须保留最终校验和事务边界。
5. Enrichment 不能覆盖用户显式输入。
6. Idempotency-Key 目前不要强行删除 body 字段，先保留兼容。
7. RAG rewrite 不能替代 originalQuery。
8. Agent memory 不能替代数据库事实。
9. 高风险写操作必须保留确认机制。
10. 新增代码使用中文注释：
    - 类、方法用 /** ... */
    - 字段和复杂方法内部关键逻辑用 //
```

------

# 十、最终请输出结果

完成修改后，请输出：

```
1. 修改了哪些文件。
2. 每个文件具体改了什么。
3. 新增了哪些类。
4. 新增了哪些测试。
5. 已运行哪些测试，结果如何。
6. 哪些测试没运行，原因是什么。
7. 当前项目创建工单链路是否已经支持 title + description 最小输入。
8. 当前 Agent 创建工单是否已经支持最小输入。
9. 当前 Idempotency-Key 策略最终是什么。
10. 当前 RAG query rewrite 是否已经支持安全回退或双路召回。
11. 还有哪些遗留问题。
```
