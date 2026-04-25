package com.smartticket.agent.resilience;

import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.support.AgentToolResults;
import org.springframework.stereotype.Service;

/**
 * Agent 降级策略服务。
 *
 * <p>该服务位于异常保护与回复渲染之间，用于把限流、session busy、预算超限、LLM/RAG 不可用等
 * 工程态转换为结构化 summary。它不执行业务 Tool，不允许写库，也不会直接修改
 * session、memory、pendingAction 或 trace。</p>
 */
@Service
public class AgentDegradePolicyService {

    /**
     * 判断指定意图是否允许在增强能力失败后走确定性降级。
     *
     * @param route 当前意图路由
     * @return true 表示可以尝试用非 LLM 路径完成核心能力
     */
    public boolean canDegradeToDeterministic(IntentRoute route) {
        if (route == null || route.getIntent() == null) {
            return false;
        }
        return route.getIntent() == AgentIntent.QUERY_TICKET
                || route.getIntent() == AgentIntent.SEARCH_HISTORY;
    }

    /**
     * 给已通过降级路径完成的 summary 标记降级事实。
     *
     * @param summary 已完成的执行摘要
     * @param errorCode 触发降级的工程错误码
     * @param reason 降级原因
     * @return 被标记后的同一个 summary
     */
    public AgentExecutionSummary markDegraded(
            AgentExecutionSummary summary,
            AgentErrorCode errorCode,
            String reason
    ) {
        if (summary == null) {
            return null;
        }
        summary.setStatus(AgentTurnStatus.DEGRADED);
        summary.setDegraded(true);
        summary.setFailureCode(errorCode == null ? AgentErrorCode.AGENT_DEGRADED.name() : errorCode.name());
        summary.setFailureReason(reason);
        return summary;
    }

    /**
     * 构造无法进入主链时的失败摘要。
     *
     * @param errorCode 工程错误码
     * @param reason 可展示给用户的最小失败原因
     * @return 兼容现有 reply 渲染器的失败 summary
     */
    public AgentExecutionSummary failure(AgentErrorCode errorCode, String reason) {
        String reply = switch (errorCode) {
            case AGENT_RATE_LIMITED -> "当前请求过于频繁，请稍后再试。";
            case AGENT_SESSION_BUSY -> "当前会话已有一条消息正在处理中，请稍后再试。";
            case AGENT_TIMEOUT -> "当前请求处理超时，请稍后重试。";
            case AGENT_BUDGET_EXCEEDED -> "当前请求已超过单轮处理预算，请收敛问题后重试。";
            case AGENT_DEGRADED -> "当前增强能力不可用，已尝试降级处理。";
        };
        AgentToolResult result = AgentToolResults.failed("agentResilience", reply, errorCode.name());
        return AgentExecutionSummary.builder()
                .status(AgentTurnStatus.FAILED)
                .mode(AgentExecutionMode.CLARIFICATION)
                .primaryResult(result)
                .failureCode(errorCode.name())
                .failureReason(reason)
                .build();
    }
}
