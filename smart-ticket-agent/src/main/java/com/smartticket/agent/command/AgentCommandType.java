package com.smartticket.agent.command;

import com.smartticket.agent.model.AgentIntent;

/**
 * Agent 写操作命令类型。
 *
 * <p>该枚举位于写命令主链的语义层，用于把“创建工单”“转派工单”这类会落库的业务动作
 * 从泛化的 Tool / fallback 概念中剥离出来。它本身不执行写操作，也不会修改
 * session、memory、pendingAction 或 trace。</p>
 */
public enum AgentCommandType {
    /**
     * 创建工单命令。
     */
    CREATE_TICKET,

    /**
     * 转派工单命令。
     */
    TRANSFER_TICKET;

    /**
     * 根据当前意图解析命令类型。
     *
     * @param intent 当前路由识别出的业务意图
     * @return 对应写命令类型；只支持写意图
     */
    public static AgentCommandType fromIntent(AgentIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("写命令类型不能从空意图解析");
        }
        return switch (intent) {
            case CREATE_TICKET -> CREATE_TICKET;
            case TRANSFER_TICKET -> TRANSFER_TICKET;
            default -> throw new IllegalArgumentException("当前意图不属于写操作命令: " + intent);
        };
    }
}
