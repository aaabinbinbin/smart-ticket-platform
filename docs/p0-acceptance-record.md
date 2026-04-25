# P0 验收记录

## 1. 单元测试

```bash
mvn -pl smart-ticket-agent -am test
mvn -pl smart-ticket-biz -am test
mvn -pl smart-ticket-rag -am test
```

说明：
- Agent 模块测试通过
- Biz 模块测试通过
- RAG 模块测试通过

## 2. RAG 主路径启动验证

启动日志：

```
RAG 路径状态：embeddingEnabled=true, vectorStoreEnabled=true, vectorStoreReady=true, retrievalPath=PGVECTOR
```

说明：
- Spring Boot 已连接 pgvector
- 当前检索主路径是 PGVECTOR

## 3. 知识构建链路验证

构建触发日志：

```
知识构建任务已发布：taskId=..., ticketId=...
工单知识任务开始：taskId=..., ticketId=...
向量构建开始：knowledgeId=..., vectorStoreEnabled=true, chunks=8
PGvector 直写完成：table=public.vector_store, documents=8, updated=8
工单知识任务完成：taskId=..., ticketId=..., knowledgeId=..., chunks=8
```

说明：
- 工单关闭后能触发知识构建
- RabbitMQ 消费成功
- EmbeddingService 执行成功
- MySQL fallback 写入成功
- pgvector 写入成功

## 4. pgvector 表数据验证

```sql
SELECT COUNT(*) FROM vector_store;
SELECT id, LEFT(content, 100), metadata, vector_dims(embedding) FROM vector_store LIMIT 5;
```

说明：
- vector_store 中存在 UUID、content、metadata、embedding
- embedding 维度为 1536

## 5. RAG 检索验证

接口调用：

```
GET /api/rag/search?query=测试环境登录token过期重新登录失败&topK=5
Authorization: Bearer <token>
```

响应：

```json
{
  "success": true,
  "data": {
    "queryText": "测试环境登录token过期重新登录失败",
    "retrievalPath": "PGVECTOR",
    "fallbackUsed": false,
    "hits": [...]
  }
}
```

说明：
- retrievalPath=PGVECTOR
- fallbackUsed=false
- hits 非空

后端日志：

```
RAG 检索路径=PGVECTOR，fallbackUsed=false
```

## 6. 当前剩余优化项

- Agent 历史检索路由已优化（关键字列表扩充，支持"类似/相似/知识库/解决方案/怎么处理"）
- 后续可补 RAG Recall@K / MRR 评估集
- 后续可补 SSE 流式 Agent 接口
- 后续可补 Dashboard 指标接口
