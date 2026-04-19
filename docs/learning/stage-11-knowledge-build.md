# 阶段十一：知识构建与向量化入库

## 1. 阶段目标

阶段十一的目标是把已关闭工单沉淀为可检索知识，并完成第一版向量化入库链路。

本阶段只做知识构建和向量入库，不把 RAG 接入 Agent 主链路。

明确不做：

1. 不让 `QUERY_TICKET` 默认走 RAG。
2. 不让 `CREATE_TICKET`、`TRANSFER_TICKET` 依赖历史知识。
3. 不把 embedding 流程塞进关闭工单主事务。
4. 不做复杂 MQ。
5. 不做向量检索和召回排序。

## 2. 整体链路

关闭工单成功后的链路：

```text
TicketService.closeTicket()
  -> 更新 ticket.status = CLOSED
  -> 写 CLOSE 操作日志
  -> 清理工单详情缓存
  -> 主事务提交后发布 TicketClosedEvent
  -> TicketKnowledgeBuildListener 异步消费事件
  -> TicketKnowledgeService.buildKnowledge(ticketId)
  -> EmbeddingService.embedKnowledge(knowledge)
```

关键点：

```text
关闭工单主事务只负责业务事实变更。
知识构建和 embedding 在事务提交后异步执行。
异步失败不影响工单关闭结果。
```

## 3. TicketKnowledgeService 职责

位置：

```text
smart-ticket-biz/src/main/java/com/smartticket/biz/service/TicketKnowledgeService.java
```

职责：

1. 判断工单是否具备知识沉淀条件。
2. 读取 `ticket` 当前事实数据。
3. 读取 `ticket_comment` 协作过程数据。
4. 提取关键评论。
5. 生成标准化知识文本。
6. 生成知识摘要。
7. 写入或更新 `ticket_knowledge`。

不负责：

1. 不生成向量。
2. 不写 `ticket_knowledge_embedding`。
3. 不做向量检索。
4. 不参与 Agent Tool 执行。

### 3.1 知识沉淀条件

第一版只沉淀满足以下条件的工单：

```text
ticket.status == CLOSED
并且
solutionSummary 非空 或 存在关键评论
```

这样可以避免把尚未完成、没有解决经验的工单写入知识库。

### 3.2 关键评论提取

当前按评论类型做保守提取：

```text
SOLUTION 优先
PROCESS_LOG 其次
USER_REPLY 作为补充
```

第一版最多保留 8 条关键评论。

后续可以增强为：

1. LLM 摘要关键处理步骤。
2. 按操作日志和评论时间线生成解决过程。
3. 过滤重复评论和低价值评论。

### 3.3 标准化知识文本

知识正文写入：

```text
ticket_knowledge.content
```

结构包括：

```text
基本信息
问题描述
解决摘要
关键评论
```

摘要写入：

```text
ticket_knowledge.content_summary
```

摘要用于后续列表展示和 RAG 召回结果展示。

## 4. EmbeddingService 职责

位置：

```text
smart-ticket-rag/src/main/java/com/smartticket/rag/service/EmbeddingService.java
```

职责：

1. 对 `ticket_knowledge.content` 做文本切片。
2. 调用 `EmbeddingModelClient` 生成向量。
3. 将切片文本和向量写入 `ticket_knowledge_embedding`。
4. 重新构建时先删除旧切片，保证第一版简单幂等。

不负责：

1. 不判断工单是否能沉淀知识。
2. 不读取工单业务状态。
3. 不做向量检索。
4. 不参与关闭工单事务。

### 4.1 文本切片

第一版切片策略：

```text
chunkSize = 500 字符
overlap = 80 字符
```

这是最小可用实现。

后续可以升级为：

1. 按 Markdown 标题切片。
2. 按段落切片。
3. 按 token 数切片。
4. 按“问题 / 原因 / 解决方案”语义切片。

### 4.2 Embedding 模型客户端

当前定义了模型客户端抽象：

```text
EmbeddingModelClient
```

第一版实现：

```text
LocalHashEmbeddingModelClient
```

该实现使用文本哈希生成稳定向量，目的是先打通知识构建、切片、向量入库链路。

生产或后续阶段应替换为：

```text
OpenAI 兼容 embedding API
本地 embedding 模型
企业内部向量服务
```

### 4.3 向量入库

当前表：

```text
ticket_knowledge_embedding
```

字段：

| 字段 | 说明 |
| --- | --- |
| `knowledge_id` | 所属知识 ID |
| `chunk_index` | 切片序号 |
| `chunk_text` | 切片文本 |
| `embedding_vector` | 向量 JSON 文本 |

第一版先用 MySQL `TEXT` 保存向量 JSON，后续接入 pgvector 时可以迁移为专用向量字段。

## 5. 异步入库时机

`TicketService.closeTicket()` 中只在关闭成功后注册事务提交回调：

```text
afterCommit -> publish TicketClosedEvent
```

事件监听器：

```text
TicketKnowledgeBuildListener
```

监听方式：

```text
@EventListener
@Async("knowledgeAsyncExecutor")
```

线程池配置：

```text
RagAsyncConfig
```

这保证：

```text
关闭工单主事务提交成功后，才开始知识构建。
知识构建和 embedding 不阻塞关闭接口。
异步失败不会回滚关闭工单。
```

## 6. 失败补偿思路

第一版失败处理：

```text
异步监听器 catch RuntimeException
记录 ticketId 和失败原因日志
```

后续补偿可以按以下方向演进：

1. 增加轻量任务表，例如 `ticket_knowledge_build_task`。
2. 关闭工单后写入任务状态 `PENDING`。
3. 异步任务成功后更新为 `SUCCESS`。
4. 失败后记录 `FAILED`、失败原因和重试次数。
5. 定时任务扫描 `FAILED` 或超时 `PENDING` 任务重试。
6. 提供按 `ticketId` 手动重建知识和 embedding 的管理接口。

当前没有引入 MQ，是为了保持阶段十一最小可用。

## 7. 模块边界

当前模块边界：

```text
smart-ticket-biz:
  TicketKnowledgeService
  判断沉淀条件
  构造知识文本
  写 ticket_knowledge

smart-ticket-rag:
  TicketKnowledgeBuildListener
  EmbeddingService
  文本切片
  调 embedding 模型
  写 ticket_knowledge_embedding

smart-ticket-domain:
  TicketKnowledge
  TicketKnowledgeEmbedding
  Mapper
```

注意：

```text
TicketKnowledgeService 不直接做向量检索。
EmbeddingService 不修改工单主表。
Agent 当前不默认调用 RAG。
```

## 8. 当前完成状态

已完成：

1. 关闭工单后发布 `TicketClosedEvent`。
2. 事件在事务提交后发布。
3. RAG 模块异步监听关闭事件。
4. `TicketKnowledgeService` 生成并 upsert `ticket_knowledge`。
5. `EmbeddingService` 切片、生成向量并写入 `ticket_knowledge_embedding`。
6. `ticket_knowledge_embedding` 增加 `embedding_vector` 字段。

可以进入下一阶段：

```text
阶段十二：受控 RAG 检索闭环
```

但阶段十二仍应保持：

```text
RAG 只服务历史经验检索，不替代当前工单事实查询。
```
