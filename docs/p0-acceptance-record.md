# P0 验收记录

## 1. 验收结论

P0 已完成，覆盖：

- Agent 安全边界与核心对话链路
- Biz 核心业务测试
- RAG 敏感信息脱敏与入库
- RabbitMQ 异步知识构建
- MySQL fallback embedding
- PostgreSQL + pgvector 主写入路径
- PostgreSQL + pgvector 主检索路径

项目处于**高完成度 MVP** 阶段，不是生产级完整平台。

## 2. 测试通过情况

| 模块 | 命令 | 状态 |
|---|---|---|
| Agent | `mvn -pl smart-ticket-agent -am test` | 通过 |
| Biz | `mvn -pl smart-ticket-biz -am test` | 通过 |
| RAG | `mvn -pl smart-ticket-rag -am test` | 通过 |
| API | `mvn -pl smart-ticket-api -am test` | 通过 |

## 3. RAG 主路径启动验证

启动日志：

```
RAG 路径状态：embeddingEnabled=true, vectorStoreEnabled=true, vectorStoreReady=true, retrievalPath=PGVECTOR
```

说明：
- Spring Boot 已连接 pgvector
- 当前检索主路径是 PGVECTOR

## 4. 知识构建链路验证

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

## 5. MySQL 验证

```sql
SELECT id, ticket_id, status, retry_count, last_error
FROM ticket_knowledge_build_task
ORDER BY id DESC
LIMIT 10;

SELECT id, ticket_id, status, LEFT(content, 100)
FROM ticket_knowledge
ORDER BY id DESC
LIMIT 10;

SELECT id, knowledge_id, chunk_index, chunk_type, source_field, LEFT(chunk_text, 100)
FROM ticket_knowledge_embedding
ORDER BY id DESC
LIMIT 20;
```

## 6. pgvector 验证

```sql
SELECT COUNT(*) FROM vector_store;

SELECT id, LEFT(content, 100), metadata, vector_dims(embedding)
FROM vector_store
LIMIT 5;
```

说明：
- vector_store 中存在 UUID、content、metadata、embedding
- embedding 维度为 1536

## 7. RAG 检索验证

执行 E2E 脚本：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\run_rag_pgvector_e2e.ps1
```

关键输出：

```text
[OK] RAG search OK
retrievalPath=PGVECTOR
fallbackUsed=False
hits count=5
```

## 8. 验收截图

截图可放置于 `docs/images/` 目录下：

- ticket_knowledge_build_task SUCCESS — 知识构建任务状态为 SUCCESS
- ticket_knowledge ACTIVE — 知识记录状态为 ACTIVE
- ticket_knowledge_embedding chunks — embedding 切片数据
- pgvector vector_store embedding — pgvector 表中 UUID、content、metadata、vector_dims(embedding) 验证

## 9. 当前完成状态

- Agent 历史检索路由已优化（强历史词 + 辅助词 + 历史上下文词三层判断）
- SSE 流式 Agent 接口已完成（含 done 事件、专用 agentExecutor 线程池）
- Dashboard 指标接口已完成（工单、RAG、Agent 三大板块，knowledgeCount=ACTIVE 知识数）
- RAG Recall@K / MRR 评估集已实现（测试级，见 RagEvaluationMetrics，覆盖 Recall@3、Recall@5、MRR）
- E2E 验收脚本已完成（scripts/run_rag_pgvector_e2e.ps1，覆盖完整链路）
- Controller 层测试：RagControllerWebMvcTest、AdminDashboardControllerTest、AgentStreamControllerTest

## 10. 后续可优化方向

- Agent 历史检索路由继续优化
- RAG 检索结果展示优化
- SSE 后续可升级为 token 级流式
- RAG Recall@K / MRR 可扩展为真实数据集评估 runner
- Dashboard 指标可继续扩展
