# 阶段十二：RAG 检索闭环设计

## 1. 阶段目标

阶段十二在阶段十一“知识构建与向量化入库”的基础上，实现历史工单经验检索闭环。

本阶段目标：

1. 用户可以通过历史经验类意图检索相似历史工单。
2. 创建工单前可以做相似案例检查，给处理人提供参考。
3. RAG 输出只作为参考，不裁决当前工单事实，也不阻止用户创建工单。

本阶段不做：

1. 不引入 Elasticsearch。
2. 不做复杂 rerank。
3. 不把当前工单状态查询混入 RAG。
4. 不让相似案例强制阻止创建工单。
5. 不让 QUERY_TICKET 默认走 RAG。

## 2. 知识构建流程

阶段十一已经完成知识构建：

```text
TicketService.closeTicket()
  -> 主事务提交后发布 TicketClosedEvent
  -> TicketKnowledgeBuildListener 异步消费
  -> TicketKnowledgeService.buildKnowledge(ticketId)
  -> 写入 ticket_knowledge
```

`TicketKnowledgeService` 位于 `smart-ticket-biz`，职责是：

1. 判断工单是否具备知识沉淀条件。
2. 读取 `ticket` 和 `ticket_comment`。
3. 提取关键评论。
4. 生成标准化知识文本。
5. 生成摘要。
6. 写入或更新 `ticket_knowledge`。

它不做向量化，也不做检索。

## 3. Embedding 流程

阶段十一已经完成 embedding 入库：

```text
TicketKnowledgeBuildListener
  -> EmbeddingService.embedKnowledge(knowledge)
  -> splitText(content)
  -> EmbeddingModelClient.embed(chunk)
  -> 写入 ticket_knowledge_embedding
```

当前第一版实现：

```text
EmbeddingModelClient = LocalHashEmbeddingModelClient
```

该实现用文本哈希生成稳定向量，目的是先打通链路。

后续可以替换为：

1. OpenAI 兼容 embedding API。
2. 本地 embedding 模型。
3. 企业内部向量服务。
4. pgvector 向量字段。

当前向量字段：

```text
ticket_knowledge_embedding.embedding_vector
```

第一版以 JSON 数组文本保存。

## 4. 检索流程

新增：

```text
smart-ticket-rag/src/main/java/com/smartticket/rag/service/RetrievalService.java
```

职责：

1. 接收查询文本。
2. 可选轻量 query rewrite。
3. 生成查询向量。
4. 读取已入库的知识切片向量。
5. 计算余弦相似度。
6. 返回 TopK 结构化结果。

调用链：

```text
RetrievalService.retrieve(request)
  -> normalize / rewrite query
  -> EmbeddingModelClient.embed(query)
  -> TicketKnowledgeEmbeddingRepository.findAll()
  -> TicketKnowledgeReadRepository.findActive()
  -> cosineSimilarity(queryVector, chunkVector)
  -> TopK RetrievalHit
  -> RetrievalResult
```

结构化对象：

```text
RetrievalRequest
RetrievalResult
RetrievalHit
```

`RetrievalHit` 包含：

```text
knowledgeId
ticketId
embeddingId
chunkIndex
score
contentSummary
chunkText
```

第一版检索是在应用内完成的内存 TopK，适合最小可用链路。

后续数据量增加后再演进：

1. pgvector SQL 相似度检索。
2. 向量数据库。
3. Elasticsearch + 向量混合检索。
4. rerank 模型。

## 5. SearchHistoryTool 历史经验检索

`SearchHistoryTool` 现在从“只查会话历史”扩展为“历史经验检索 + 会话近期消息补充”。

调用链：

```text
Agent 意图 = SEARCH_HISTORY
  -> AgentToolRegistry 选择 SearchHistoryTool
  -> SearchHistoryTool.execute()
  -> RetrievalService.retrieve()
  -> 返回历史案例和 recentMessages
```

注意：

```text
只有 SEARCH_HISTORY 这类历史经验意图才会调用 RAG Tool。
QUERY_TICKET 不走 RAG，仍然查询当前工单事实数据。
```

返回结果包括：

```text
retrieval: RetrievalResult
recentMessages: 当前会话近期消息
```

## 6. 创建前相似案例检查

`CreateTicketTool` 在真正创建工单前会调用：

```text
RetrievalService.checkSimilarCasesBeforeCreate(title, description, topK)
```

用途：

1. 查找相似历史问题。
2. 给处理人提供参考。
3. 在响应数据中返回 `similarCases`。

关键边界：

```text
相似案例命中后，不强制禁止创建工单。
```

也就是说：

```text
历史案例 = 参考
当前创建 = 继续执行
业务校验 = biz 层裁决
```

当前流程：

```text
CreateTicketTool
  -> requiredFields 已通过 Guard 校验
  -> RetrievalService.checkSimilarCasesBeforeCreate()
  -> TicketService.createTicket()
  -> 返回 ticket + similarCases
```

如果没有命中相似案例：

```text
正常创建工单
```

如果命中相似案例：

```text
仍然创建工单
同时把相似案例放入响应结果，供后续处理参考
```

## 7. 参考而非裁决原则

RAG 在当前系统中的定位是：

```text
帮助用户和处理人复用历史经验
```

不是：

```text
裁决当前工单事实
替代当前工单查询
替代权限判断
阻止用户创建工单
自动执行写操作
```

具体原则：

1. 当前工单状态查询必须走 `QueryTicketTool -> TicketService`。
2. 创建工单必须走 `CreateTicketTool -> TicketService`。
3. 转派工单必须走 `TransferTicketTool -> TicketService`。
4. 历史案例只能作为提示和参考。
5. 命中历史相似案例不能强制拦截创建。
6. RAG 结果不能覆盖当前工单事实数据。

## 8. 当前完成状态

已完成：

1. `RetrievalService`。
2. `RetrievalRequest`、`RetrievalResult`、`RetrievalHit`。
3. 查询向量生成。
4. 已入库切片向量读取。
5. 应用内余弦相似度 TopK。
6. `SearchHistoryTool` 接入历史经验检索。
7. `CreateTicketTool` 创建前相似案例检查。

当前限制：

1. 第一版向量是本地哈希向量，不是真实语义 embedding。
2. 第一版 TopK 在应用内扫描，不适合大规模数据。
3. 第一版不做复杂 rerank。
4. 第一版不做当前工单事实查询与 RAG 混合。

## 9. 后续演进

后续可以继续增强：

1. 替换真实 embedding 模型。
2. 使用 pgvector 做数据库侧向量检索。
3. 给 RetrievalService 增加分类、优先级、时间范围过滤。
4. 做轻量 rerank。
5. 给 SearchHistoryTool 增加更自然的总结回复。
6. 在 Agent 编排中加入受控多步检索与工具调用，但仍需经过 Guard。
