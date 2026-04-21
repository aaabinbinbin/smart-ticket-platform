# Spring AI 集成总览

更新时间：2026-04-20

## 为什么引入 Spring AI

当前项目已经有清晰的业务分层，但对话模型、Tool Calling、Embedding 和 VectorStore 属于外部 AI 能力接入问题，不适合在项目里手写一套模型客户端和工具编排框架。

Spring AI 的价值是把模型访问、工具调用、Embedding 和向量库适配统一到 Spring 生态里，使项目保持单一技术栈：

- 不引入 LangChain4j，不做双栈混用。
- 使用 Spring Bean 生命周期管理模型、Tool 和 VectorStore。
- 让 agent 模块专注对话入口、意图路由、上下文和 Tool 边界。
- 让 rag 模块专注知识切片、向量化和检索。
- 让 biz 模块继续掌握业务规则，不被 prompt 或模型输出替代。

## 哪些部分由 Spring AI 接管

### Chat model

Spring AI `ChatClient` 接管真实模型对话调用。`AgentFacade` 会在配置开启且 `ChatClient` 可用时，通过 Spring AI Tool Calling 触发项目内 Tool。

### Tool Calling

Spring AI 负责把模型输出映射为 Tool 调用，但 Tool 是否可以执行仍由项目代码判断：

- `IntentRouter` 先限制本轮意图。
- `AgentFacade` 只暴露该意图对应的一个 Tool。
- `AgentExecutionGuard` 校验风险、确认语义和必填参数。
- Tool 内部只能调用现有 `biz` 或 `rag` service。

### Embedding model

Spring AI `EmbeddingModel` 作为 RAG 的主 embedding 能力。当前通过 `SpringAiEmbeddingModelClient` 适配到项目内 `EmbeddingModelClient` 接口。

默认未配置模型时，保留本地 hash embedding 兜底，保证本地开发和测试链路可运行。

### VectorStore

Spring AI `VectorStore` 作为 PGvector 接入入口。启用 `smart-ticket.ai.vector-store.enabled=true` 后：

- `EmbeddingService` 会把知识切片写入 `VectorStore`。
- `RetrievalService` 会优先使用 `VectorStore` 做相似度检索。

未启用时，保留 MySQL JSON 向量 + 内存 TopK 兜底。

## 哪些部分必须保留在 biz

`biz` 是业务规则层，不能被 Spring AI 或 agent 替代：

- 工单创建、幂等、字段校验。
- 工单状态流转和关闭规则。
- 工单转派、权限判断、处理人规则。
- 工单查询的事实口径。
- 知识生产条件判断、关键评论提取、标准知识文本生成。

Tool 只是业务能力的调用入口，不拥有业务规则。任何写操作都必须通过 `biz` service，agent 不允许直接操作 repository。

## 当前模块边界

- `biz`：工单主流程与业务规则，包括 `TicketKnowledgeService` 的知识生产。
- `agent`：Spring AI 驱动的对话入口、意图路由、Tool Calling、短会话上下文。
- `rag`：知识向量化、检索和 RAG 结果组织。
- `infra`：Redis、Chat model、Embedding model、PGvector 等外部能力适配。
