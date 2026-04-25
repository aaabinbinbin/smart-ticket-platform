package com.smartticket.rag.eval;

import com.smartticket.rag.model.RetrievalHit;
import com.smartticket.rag.model.RetrievalResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * RAG 评估指标计算工具。
 *
 * <p>提供 Recall@K 和 MRR（Mean Reciprocal Rank）等常用检索指标的计算，
 * 不依赖外部服务，可用于单元测试验证或本地手动评估。</p>
 */
public class RagEvaluationMetrics {

    private RagEvaluationMetrics() {}

    /**
     * 评估结果容器。
     */
    public static class EvalResult {
        private final int totalCases;
        private final int recallAtKHits;
        private final double mrr;

        public EvalResult(int totalCases, int recallAtKHits, double mrr) {
            this.totalCases = totalCases;
            this.recallAtKHits = recallAtKHits;
            this.mrr = mrr;
        }

        /** 总评估 case 数。 */
        public int getTotalCases() { return totalCases; }

        /** 命中 Recall@K 的 case 数。 */
        public int getRecallAtKHits() { return recallAtKHits; }

        /** Recall@K = 命中 case 数 / 总 case 数。 */
        public double getRecallAtK() {
            return totalCases == 0 ? 0.0 : (double) recallAtKHits / totalCases;
        }

        /** MRR = 平均倒数排名。 */
        public double getMrr() { return mrr; }
    }

    /**
     * 评估 case：一次查询的预期结果定义。
     */
    public static class EvalCase {
        private final String query;
        private final Set<Long> expectedKnowledgeIds;
        private final Set<Long> expectedTicketIds;
        private final int topK;

        public EvalCase(String query, Set<Long> expectedKnowledgeIds, Set<Long> expectedTicketIds, int topK) {
            this.query = query;
            this.expectedKnowledgeIds = expectedKnowledgeIds == null ? Set.of() : expectedKnowledgeIds;
            this.expectedTicketIds = expectedTicketIds == null ? Set.of() : expectedTicketIds;
            this.topK = Math.max(1, topK);
        }

        public String getQuery() { return query; }
        public Set<Long> getExpectedKnowledgeIds() { return expectedKnowledgeIds; }
        public Set<Long> getExpectedTicketIds() { return expectedTicketIds; }
        public int getTopK() { return topK; }

        /** 判断该 hit 是否为期望结果。 */
        public boolean isExpected(RetrievalHit hit) {
            if (hit == null) return false;
            return (expectedKnowledgeIds.contains(hit.getKnowledgeId())
                    || expectedTicketIds.contains(hit.getTicketId()));
        }
    }

    /**
     * 计算单条查询的召回结果（是否命中期望结果）。
     */
    public static boolean hitAtK(EvalCase evalCase, RetrievalResult result) {
        if (evalCase == null || result == null || result.getHits() == null) {
            return false;
        }
        List<RetrievalHit> topK = result.getHits().subList(0, Math.min(evalCase.getTopK(), result.getHits().size()));
        return topK.stream().anyMatch(evalCase::isExpected);
    }

    /**
     * 计算单条查询的倒数排名。命中返回 1/rank，未命中返回 0。
     */
    public static double reciprocalRank(EvalCase evalCase, RetrievalResult result) {
        if (evalCase == null || result == null || result.getHits() == null) {
            return 0.0;
        }
        List<RetrievalHit> topK = result.getHits().subList(0, Math.min(evalCase.getTopK(), result.getHits().size()));
        for (int i = 0; i < topK.size(); i++) {
            if (evalCase.isExpected(topK.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * 批量评估 Recall@K 和 MRR。
     *
     * @param cases 评估 case 列表
     * @param results 每个 case 对应的检索结果（顺序与 cases 一致）
     * @return 评估结果
     */
    public static EvalResult evaluate(List<EvalCase> cases, List<RetrievalResult> results) {
        if (cases == null || results == null || cases.isEmpty()) {
            return new EvalResult(0, 0, 0.0);
        }
        int minSize = Math.min(cases.size(), results.size());
        int recallHits = 0;
        double totalRr = 0.0;

        for (int i = 0; i < minSize; i++) {
            EvalCase evalCase = cases.get(i);
            RetrievalResult result = results.get(i);
            if (hitAtK(evalCase, result)) {
                recallHits++;
            }
            totalRr += reciprocalRank(evalCase, result);
        }

        return new EvalResult(minSize, recallHits, totalRr / minSize);
    }

    /**
     * 从 Mock 数据快速构造 RetrievalResult，用于单元测试指标计算逻辑。
     */
    public static RetrievalResult mockResult(List<Long> knowledgeIds, List<Long> ticketIds, List<Double> scores) {
        int size = Math.min(
                knowledgeIds == null ? 0 : knowledgeIds.size(),
                Math.min(
                        ticketIds == null ? 0 : ticketIds.size(),
                        scores == null ? 0 : scores.size()
                )
        );
        List<RetrievalHit> hits = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            hits.add(RetrievalHit.builder()
                    .knowledgeId(knowledgeIds.get(i))
                    .ticketId(ticketIds.get(i))
                    .score(scores.get(i))
                    .build());
        }
        return RetrievalResult.builder()
                .hits(hits)
                .retrievalPath("EVAL_MOCK")
                .fallbackUsed(false)
                .build();
    }
}
