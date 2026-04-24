package com.smartticket.agent.command;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;

/**
 * 写操作命令处理器。
 *
 * <p>该接口位于确定性写链的最终执行层，只负责在 Guard 和确认完成之后执行对应业务命令。
 * 实现类不参与参数提取、不做权限策略决策、不创建 pendingAction，也不直接修改
 * session、memory 或 trace。</p>
 */
public interface AgentCommandHandler {

    /**
     * 当前处理器负责的命令类型。
     *
     * @return 写命令类型
     */
    AgentCommandType commandType();

    /**
     * 当前处理器对应的业务意图。
     *
     * @return 路由意图
     */
    AgentIntent supportIntent();

    /**
     * 当前处理器最终会调用的确定性工具名。
     *
     * @return 工具名
     */
    String toolName();

    /**
     * 执行已经完成 Guard 校验和必要确认的写命令。
     *
     * @param currentUser 当前登录用户
     * @param message 用户原始消息
     * @param context 当前会话上下文
     * @param route 当前路由结果
     * @param parameters 已完成提取/合并/指代解析的结构化参数
     * @return 命令执行结果；该方法允许执行写操作，但不修改 session、memory、pendingAction、trace
     */
    AgentToolResult execute(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters
    );
}
