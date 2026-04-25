# pgvector 性能边界与演进路线

## 当前阶段选择 pgvector 的原因

| 原因 | 说明 |
|---|---|
| 架构简单 | 不引入额外向量数据库组件，复用已有 PostgreSQL |
| 复用 PostgreSQL | 知识、切片、工单等业务元数据与向量同库，易于 JOIN 过滤 |
| 易于和业务元数据结合 | 检索时可按 knowledgeId、ticketId、chunkType 等字段做元数据过滤 |
| Spring AI 集成方便 | `spring-ai-pgvector-store` 提供开箱即用的 VectorStore 实现 |

## 性能保障手段

| 手段 | 说明 |
|---|---|
| topK 限制 | 检索 topK 限制在 [1, 10] 范围，默认 3，避免一次检索返回大量结果 |
| HNSW / IVFFlat 索引 | pgvector 支持 IVFFlat 和 HNSW 索引，可显著加速 ANN 检索 |
| 元数据过滤 | 检索时可通过 metadata 过滤（如 chunkType、sourceField），减少检索范围 |
| 连接池配置 | PostgreSQL 连接池需合理配置，避免向量检索占用大量连接 |
| 缓存热门查询 | 高频相似查询的检索结果可做短时间缓存（如 30 秒），减少重复检索 |
| 限流与降级 | 检索接口配置限流，超时或失败时降级返回空结果或走 MySQL fallback |
| 压测验证 | 需在预期数据规模下做压测，验证响应时间 P99 满足 SLI 要求 |

## 演进路线

### 阶段 1：PostgreSQL + pgvector（当前）

- 知识双写：MySQL（JSON 向量） + PostgreSQL（pgvector）
- 检索默认走 pgvector，失败时 Fallback 到 MySQL 内存余弦相似度
- 适用于小规模数据（< 10 万条切片）

### 阶段 2：pgvector + 索引 + 缓存 + 评估集

- 为 pgvector 创建 HNSW/IVFFlat 索引
- 热门查询结果缓存
- 检索接口限流和超时降级
- RAG 评估集回归（Recall@K / MRR）
- 适用于中等规模数据（10 万 - 100 万条切片）

### 阶段 3：专用向量数据库

当数据规模超过 pgvector 的性能拐点（通常 > 100 万条切片或 QPS > 1000）时，可考虑迁移到专用向量数据库：

| 方案 | 优势 | 劣势 |
|---|---|---|
| Milvus | 高可用、分布式、多索引、GPU 加速 | 运维成本高，需要独立集群 |
| Qdrant | Rust 实现、单机性能好、Filter 能力强 | 社区相对较小 |
| Elasticsearch vector | 复用已有 ES 集群、生态成熟 | 向量性能不如专用库 |
| OpenSearch vector | 与 ES 兼容、开源 | 功能更新相对滞后 |

迁移原则：
- 保持 MySQL 作为可信数据源，向量数据库作为查询加速层
- 检索失败时降级回 MySQL 兜底
- 通过双写保证数据一致性

## 注意事项

- pgvector 的 IVFFlat 索引构建需要数据预聚类，数据变动较大时需要重建
- HNSW 索引在 pgvector 0.5.0+ 支持，构建较慢但查询性能更好
- 向量维度越高，索引性能下降越明显（当前使用 1536 维 embedding）
- 建议定期监控 pgvector 查询响应时间，到达性能拐点前规划迁移
