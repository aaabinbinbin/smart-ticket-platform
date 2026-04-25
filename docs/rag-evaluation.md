# RAG 评估集

## 概述

本文档描述如何使用项目内置的 RAG 评估指标（Recall@K、MRR）来量化检索效果。

## 指标说明

### Recall@K

对于每个评估 case，取检索结果的前 K 条，如果其中包含任意期望的知识记录（按 knowledgeId 或 ticketId 匹配），则该 case 为命中。

```
Recall@K = 命中 case 数 / 总 case 数
```

例如 Recall@3 = 0.8 表示 80% 的查询在前 3 条结果中命中了期望知识。

### MRR (Mean Reciprocal Rank)

对于每个评估 case，找到第一个命中结果的排序位置 rank，得分 = 1/rank。若未命中则得分为 0。

```
MRR = sum(1/rank) / 总 case 数
```

MRR 能区分"第一次命中是在第 1 位还是第 3 位"，比 Recall@K 更精细。

## 评估集格式

评估 case 定义在 `smart-ticket-rag/src/test/resources/rag/eval_cases.json`（可选手动使用），或直接在测试代码中构造。

```json
[
  {
    "query": "测试环境登录 token 过期重新登录失败",
    "expectedKnowledgeIds": [1, 2],
    "expectedTicketIds": [10],
    "topK": 5
  }
]
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| query | 是 | 检索查询文本 |
| expectedKnowledgeIds | 否 | 期望命中的知识 ID 列表 |
| expectedTicketIds | 否 | 期望命中的工单 ID 列表 |
| topK | 是 | 截断的 K 值 |

> 注意：`expectedKnowledgeIds` 和 `expectedTicketIds` 至少填一个，满足任一即视为命中。

## 如何运行

### 单元测试版

```bash
mvn -pl smart-ticket-rag -am test -Dtest="RagEvaluationMetricsTest"
```

该测试使用 mock 数据验证指标计算逻辑，不依赖外部服务。

### 手动评估版

准备评估 case 后，调用已有 `/api/rag/search` 接口获取真实检索结果，再导入 `RagEvaluationMetrics.evaluate()` 计算指标。

```java
// 从 JSON 加载评估 case
List<EvalCase> cases = loadCases();

// 对每个 case 调用真实检索
List<RetrievalResult> results = cases.stream()
    .map(c -> retrievalService.retrieve(RetrievalRequest.builder()
        .queryText(c.getQuery())
        .topK(c.getTopK())
        .rewrite(true)
        .build()))
    .toList();

// 计算指标
EvalResult evalResult = RagEvaluationMetrics.evaluate(cases, results);
System.out.println("Recall@K: " + evalResult.getRecallAtK());
System.out.println("MRR: " + evalResult.getMrr());
```

## 如何解读结果

| Recall@3 | MRR | 含义 |
|---|---|---|
| ≥ 0.8 | ≥ 0.6 | 检索效果良好 |
| 0.5 ~ 0.8 | 0.3 ~ 0.6 | 有改进空间，可优化 query rewrite 或 embedding |
| < 0.5 | < 0.3 | 需要检查数据质量、向量索引或检索策略 |

## 当前检索策略

RAG 检索默认使用 **双路召回**：

1. `originalQuery`（用户原始输入）和 `rewrittenQuery`（改写后）各自检索
2. 合并两个结果集，按 knowledgeId 去重（保留分数更高的）
3. 统一对合并结果做 rerank

rewrite 安全性保障：

- 禁止删除否定词（不、不要、不能、无法、拒绝、失败）
- 禁止删除核心故障词（报错、异常、500、超时、登录失败、权限不足）
- 改写后长度少于原文 50% 时标记为不安全
- 不安全时降级为仅使用 originalQuery 单路检索

> **query rewrite 不参与业务意图判断**，只用于增强检索召回率。

## 后续优化方向

- 补充更多高质量评估 case（覆盖不同工单类型和场景）
- 引入 NDCG（归一化折损累计增益）
- 使用真实向量库（pgvector）和生产数据做定期回归
- 在双路召回基础上引入更多改写策略（如同义词扩展、中英文翻译）
