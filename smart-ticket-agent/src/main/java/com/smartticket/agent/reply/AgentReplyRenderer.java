package com.smartticket.agent.reply;

import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.tool.core.AgentToolResult;
import org.springframework.stereotype.Component;

/**
 * Agent 最终回复渲染器。
 *
 * <p>该类位于 Agent 主链的输出层，只负责把结构化执行结果转换成面向用户的回复文本。
 * 它不允许查询数据库、不允许调用 tool，也不允许修改 session、memory、pendingAction 或 trace。
 * P1 阶段的主要目标是先把 AgentFacade 中分散的 reply 选择逻辑收敛到这里，为后续主链瘦身做准备。</p>
 */
@Component
public class AgentReplyRenderer {

    /**
     * 根据结构化执行结果渲染最终回复。
     *
     * @param route 当前轮路由结果，只用于必要的兜底文案判断
     * @param plan 当前轮计划状态，P1 阶段仅用于保留方法签名的主链位置
     * @param context 当前会话上下文，P1 阶段不直接读取或修改业务状态
     * @param summary 本轮结构化执行结果，不允许为空时返回空字符串
     * @return 最终面向用户的回复文本；该方法不执行写操作，也不会修改 session、memory、pendingAction、trace
     */
    public String render(
            IntentRoute route,
            AgentPlan plan,
            AgentSessionContext context,
            AgentExecutionSummary summary
    ) {
        if (summary == null) {
            return "";
        }

        AgentTurnStatus status = summary.getStatus();
        if (status == AgentTurnStatus.NEED_CONFIRMATION) {
            return renderNeedConfirmation(summary);
        }
        if (status == AgentTurnStatus.NEED_MORE_INFO) {
            return renderNeedMoreInfo(summary);
        }
        if (status == AgentTurnStatus.CANCELLED) {
            return renderCancelled(summary);
        }
        if (status == AgentTurnStatus.FAILED) {
            return renderFailure(route, summary);
        }
        if (summary.getMode() == AgentExecutionMode.READ_ONLY_REACT && hasText(summary.getModelReply())) {
            // P1 阶段先保持现有只读 ReAct 行为，避免统一渲染时把现网回复内容改掉。
            return summary.getModelReply();
        }
        return renderToolReply(route, summary);
    }

    /**
     * 优先使用主结果中的 Tool 回复。
     *
     * @param route 当前轮路由结果
     * @param summary 本轮结果摘要
     * @return Tool 回复或按意图给出的最小兜底文案
     */
    private String renderToolReply(IntentRoute route, AgentExecutionSummary summary) {
        AgentToolResult primaryResult = summary.getPrimaryResult();
        if (primaryResult != null && hasText(primaryResult.getReply())) {
            return primaryResult.getReply();
        }
        if (route == null || route.getIntent() == null) {
            return "本轮处理已完成。";
        }
        return switch (route.getIntent()) {
            case QUERY_TICKET -> "已完成工单查询。";
            case SEARCH_HISTORY -> "已完成历史案例检索。";
            case CREATE_TICKET -> "已完成工单创建处理。";
            case TRANSFER_TICKET -> "已完成工单转派处理。";
        };
    }

    /**
     * 渲染补参态回复。
     *
     * @param summary 本轮结果摘要
     * @return 优先返回已有 Tool 提示，否则返回通用补参文案
     */
    private String renderNeedMoreInfo(AgentExecutionSummary summary) {
        AgentToolResult primaryResult = summary.getPrimaryResult();
        if (primaryResult != null && hasText(primaryResult.getReply())) {
            return primaryResult.getReply();
        }
        return "请补充继续处理所需的信息。";
    }

    /**
     * 渲染确认态回复。
     *
     * @param summary 本轮结果摘要
     * @return 优先返回已有确认提示，否则返回通用确认文案
     */
    private String renderNeedConfirmation(AgentExecutionSummary summary) {
        AgentToolResult primaryResult = summary.getPrimaryResult();
        if (primaryResult != null && hasText(primaryResult.getReply())) {
            return primaryResult.getReply();
        }
        return "该操作需要确认后才能继续执行。";
    }

    /**
     * 渲染取消态回复。
     *
     * @param summary 本轮结果摘要
     * @return 优先返回当前主结果中的取消提示
     */
    private String renderCancelled(AgentExecutionSummary summary) {
        AgentToolResult primaryResult = summary.getPrimaryResult();
        if (primaryResult != null && hasText(primaryResult.getReply())) {
            return primaryResult.getReply();
        }
        return "已取消当前待处理操作。";
    }

    /**
     * 渲染失败态回复。
     *
     * @param route 当前轮路由结果
     * @param summary 本轮结果摘要
     * @return 优先使用已有失败文案，否则返回最小失败提示
     */
    private String renderFailure(IntentRoute route, AgentExecutionSummary summary) {
        AgentToolResult primaryResult = summary.getPrimaryResult();
        if (primaryResult != null && hasText(primaryResult.getReply())) {
            return primaryResult.getReply();
        }
        if (route != null && route.getIntent() != null && route.getIntent().name().contains("TRANSFER")) {
            return "当前转派请求未能完成，请调整信息后重试。";
        }
        return "当前请求未能完成，请稍后重试。";
    }

    /**
     * 判断文本是否包含有效内容。
     *
     * @param value 待判断文本
     * @return true 表示该文本可直接用于回复
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
