package com.smartticket.agent.model;

import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 会话中等待用户补充的信息。
 *
 * <p>当 Tool 因缺少必要参数或需要用户确认而返回 NEED_MORE_INFO 时，编排层会把本次待执行动作
 * 存入 Redis 会话上下文。用户下一轮消息会优先尝试补齐该动作，而不是重新开始一次完整路由。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingAction {
    /** 待继续执行的业务意图。 */
    private AgentIntent pendingIntent;

    /** 待继续调用的 Tool 名称。 */
    private String pendingToolName;

    /** 已经抽取到的结构化参数；后续用户补充内容会合并到这里。 */
    private AgentToolParameters pendingParameters;

    /** 当前仍然等待用户补充的字段。 */
    @Builder.Default
    private List<AgentToolParameterField> awaitingFields = new ArrayList<>();

    /** 最近一次 Tool 观察结果，用于排查和后续回复生成，不作为业务事实来源。 */
    private AgentToolResult lastToolResult;
}
