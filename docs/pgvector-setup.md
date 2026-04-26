# PGvector Setup Update

本文档补充说明当前仓库中 `PGvector` 主路径的实际可用配置、初始化步骤和验收方法。

## 当前状态

当前版本已经验证通过以下启动信号：

```text
Initializing PGVectorStore schema for table: vector_store in schema: public
PgVectorHikariPool - Start completed.
RAG 路径状态：embeddingEnabled=true, vectorStoreEnabled=true, vectorStoreReady=true
```

出现以上日志时，表示：

- PostgreSQL 连接正常
- `pgvector` 扩展已可用
- `PgVectorStore` 已成功装配
- RAG 主检索路径已准备完成

## PostgreSQL 初始化

首次接入时需要先执行两步：

1. 创建数据库

```sql
CREATE DATABASE smart_ticket_vector;
```

也可以执行仓库中的脚本：

- [pgvector-create-database.sql](/D:/aaaAgent/smart-ticket-platform/docs/sql/pgvector-create-database.sql)

2. 连接到 `smart_ticket_vector` 后安装扩展

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

也可以执行仓库中的脚本：

- [pgvector-init.sql](/D:/aaaAgent/smart-ticket-platform/docs/sql/pgvector-init.sql)

说明：

- `vector_store` 表不需要手工创建
- 应用启动时会自动初始化向量表结构

## 当前默认模型配置

当前默认接入方式为百炼 OpenAI 兼容模式：

```yaml
spring:
  ai:
    model:
      chat: openai
      embedding: openai
    openai:
      base-url: ${MY_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      api-key: ${MY_API_KEY:}
      chat:
        options:
          model: kimi-k2.5
      embedding:
        options:
          model: text-embedding-v4
          dimensions: 1536
```

说明：

- 这里的 `openai` 表示 Spring AI 使用 OpenAI 兼容协议适配器
- 不代表必须调用 OpenAI 官方服务
- 如果实际接入的是阿里百炼，只需要配置百炼的 `base-url` 和 `api-key`

## 环境变量建议

推荐通过环境变量覆盖敏感配置：

```powershell
$env:MY_API_KEY="你的百炼 API Key"
$env:MY_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

## 模型选型建议

当前推荐：

- 聊天模型：`kimi-k2.5`
- 向量模型：`text-embedding-v4`

不建议：

- 使用 `rerank` 模型替代 embedding 模型
- 将 `qwen3-vl-rerank` 直接配置到 `spring.ai.openai.embedding.options.model`

原因：

- `rerank` 模型负责重排，不负责生成向量
- `embedding` 模型负责召回向量写入与相似度检索

如果后续需要“召回 + 重排”，建议：

1. 使用 `text-embedding-v4` 做召回
2. 额外接入单独的 rerank 模型做重排

## 如何判断当前是否真的在走 PGvector

启动后先检查：

```text
RAG 路径状态：embeddingEnabled=true, vectorStoreEnabled=true, vectorStoreReady=true
```

然后触发一次知识写入和历史检索，预期在日志中看到：

```text
retrievalPath=PGVECTOR, fallbackUsed=false
```

如果看到：

```text
retrievalPath=MYSQL_FALLBACK, fallbackUsed=true
```

说明当前仍在回退链路，需要继续检查：

- `smart_ticket_vector` 数据库是否存在
- `vector` 扩展是否安装成功
- embedding 模型是否可用
- 向量维度是否与 `pgvector` 配置一致

## 本次相关文件

- [application.yml](/D:/aaaAgent/smart-ticket-platform/smart-ticket-app/src/main/resources/application.yml)
- [VectorStoreConfig.java](/D:/aaaAgent/smart-ticket-platform/smart-ticket-infra/src/main/java/com/smartticket/infra/ai/VectorStoreConfig.java)
- [pgvector-create-database.sql](/D:/aaaAgent/smart-ticket-platform/docs/sql/pgvector-create-database.sql)
- [pgvector-init.sql](/D:/aaaAgent/smart-ticket-platform/docs/sql/pgvector-init.sql)
