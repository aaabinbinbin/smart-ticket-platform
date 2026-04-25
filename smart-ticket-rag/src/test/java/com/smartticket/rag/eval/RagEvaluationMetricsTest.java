package com.smartticket.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartticket.rag.eval.RagEvaluationMetrics.EvalCase;
import com.smartticket.rag.eval.RagEvaluationMetrics.EvalResult;
import com.smartticket.rag.model.RetrievalResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * RAG 评估指标计算测试。
 *
 * <p>覆盖 Recall@3、Recall@5、MRR、无命中场景。</p>
 */
class RagEvaluationMetricsTest {

    @Test
    void shouldCalculateRecallAt3WhenHitWithinTop3() {
        // top3 内命中 knowledgeId=1
        RetrievalResult result = RagEvaluationMetrics.mockResult(
                List.of(1L, 2L, 3L, 4L, 5L),
                List.of(10L, 20L, 30L, 40L, 50L),
                List.of(0.95, 0.90, 0.85, 0.80, 0.75)
        );
        EvalCase evalCase = new EvalCase("查询文本", Set.of(1L), null, 3);

        boolean hit = RagEvaluationMetrics.hitAtK(evalCase, result);
        double rr = RagEvaluationMetrics.reciprocalRank(evalCase, result);

        assertTrue(hit, "knowledgeId=1 在 top3 内，应命中 Recall@3");
        assertEquals(1.0, rr, 0.001, "在 rank 1 命中，MRR 贡献应为 1.0");
    }

    @Test
    void shouldCalculateRecallAt3WhenHitAtRank3() {
        RetrievalResult result = RagEvaluationMetrics.mockResult(
                List.of(1L, 2L, 3L),
                List.of(10L, 20L, 30L),
                List.of(0.90, 0.85, 0.80)
        );
        EvalCase evalCase = new EvalCase("查询文本", Set.of(3L), null, 3);

        boolean hit = RagEvaluationMetrics.hitAtK(evalCase, result);
        double rr = RagEvaluationMetrics.reciprocalRank(evalCase, result);

        assertTrue(hit, "knowledgeId=3 在 top3 内，应命中 Recall@3");
        assertEquals(1.0 / 3.0, rr, 0.001, "在 rank 3 命中，MRR 贡献应为 1/3");
    }

    @Test
    void shouldCalculateRecallAt5WhenHitWithinTop5() {
        RetrievalResult result = RagEvaluationMetrics.mockResult(
                List.of(1L, 2L, 3L, 4L, 5L),
                List.of(10L, 20L, 30L, 40L, 50L),
                List.of(0.95, 0.90, 0.85, 0.80, 0.75)
        );
        EvalCase evalCase = new EvalCase("查询文本", null, Set.of(40L), 5);

        boolean hitAt5 = RagEvaluationMetrics.hitAtK(evalCase, result);
        boolean hitAt3 = RagEvaluationMetrics.hitAtK(
                new EvalCase("查询文本", null, Set.of(40L), 3), result);

        assertTrue(hitAt5, "ticketId=40 在 top5 内，应命中 Recall@5");
        // ticketId=40 在 top3 之外（rank 4）
        assertEquals(1.0 / 4.0, RagEvaluationMetrics.reciprocalRank(evalCase, result), 0.001);
    }

    @Test
    void shouldReturnZeroRecallWhenNoHit() {
        RetrievalResult result = RagEvaluationMetrics.mockResult(
                List.of(1L, 2L, 3L),
                List.of(10L, 20L, 30L),
                List.of(0.90, 0.85, 0.80)
        );
        // 期望 knowledgeId=99，不在结果中
        EvalCase evalCase = new EvalCase("查询文本", Set.of(99L), null, 3);

        assertEquals(0.0, RagEvaluationMetrics.reciprocalRank(evalCase, result), 0.001);
    }

    @Test
    void shouldReturnZeroWhenEmptyResult() {
        RetrievalResult empty = RetrievalResult.builder().hits(List.of()).build();
        EvalCase evalCase = new EvalCase("查询文本", Set.of(1L), null, 5);

        assertEquals(0.0, RagEvaluationMetrics.reciprocalRank(evalCase, empty), 0.001);
    }

    @Test
    void shouldCalculateMrrAcrossMultipleCases() {
        // Case 1: 在 rank 2 命中 → RR = 1/2
        RetrievalResult r1 = RagEvaluationMetrics.mockResult(
                List.of(10L, 20L), List.of(100L, 200L), List.of(0.80, 0.75));
        // Case 2: 在 rank 1 命中 → RR = 1
        RetrievalResult r2 = RagEvaluationMetrics.mockResult(
                List.of(30L, 40L), List.of(300L, 400L), List.of(0.90, 0.85));
        // Case 3: 未命中 → RR = 0
        RetrievalResult r3 = RagEvaluationMetrics.mockResult(
                List.of(50L, 60L), List.of(500L, 600L), List.of(0.70, 0.65));

        List<EvalCase> cases = List.of(
                new EvalCase("q1", Set.of(20L), null, 3),
                new EvalCase("q2", Set.of(30L), null, 3),
                new EvalCase("q3", Set.of(99L), null, 3)
        );

        EvalResult evalResult = RagEvaluationMetrics.evaluate(cases, List.of(r1, r2, r3));

        assertEquals(3, evalResult.getTotalCases());
        // Recall: case1 命中, case2 命中, case3 未命中 → 2/3
        assertEquals(2, evalResult.getRecallAtKHits());
        assertEquals(2.0 / 3.0, evalResult.getRecallAtK(), 0.001);
        // MRR: (0.5 + 1.0 + 0.0) / 3 = 0.5
        assertEquals(0.5, evalResult.getMrr(), 0.001);
    }
}
