package com.smartticket.agent.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 会话上下文。
 *
 * <p>该对象以 {@code agent:session:{sessionId}} 为 key 存入 Redis，用于保存短期多轮对话状态。
 * 这里不保存工单事实数据，工单事实仍然以 MySQL 和 biz 层查询结果为准。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionContext {
    /** 当前对话最近操作或查询到的工单 ID。 */
    private Long activeTicketId;

    /** 当前对话最近操作或确认过的处理人 ID。 */
    private Long activeAssigneeId;

    /** 最近一次识别出的 Agent 意图编码。 */
    private String lastIntent;

    /** 最近几轮用户消息摘要，用于短期会话历史查询和 LLM Prompt 上下文。 */
    @Builder.Default
    private List<String> recentMessages = new ArrayList<>();

    /** 当前等待用户补充或确认的动作；为空表示下一轮走正常单 Agent 编排流程。 */
    private AgentPendingAction pendingAction;
}
