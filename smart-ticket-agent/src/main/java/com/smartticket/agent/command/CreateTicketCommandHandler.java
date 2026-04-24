package com.smartticket.agent.command;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import com.smartticket.biz.model.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 创建工单命令处理器。
 *
 * <p>该处理器位于确定性写命令链的最终执行层，专门负责在参数和风险校验通过后调用
 * {@link CreateTicketTool} 执行创建。它不参与补参状态机、不决定权限和确认策略，
 * 也不会直接修改 session、memory、pendingAction 或 trace。</p>
 */
@Component
public class CreateTicketCommandHandler implements AgentCommandHandler {

    private final CreateTicketTool createTicketTool;

    public CreateTicketCommandHandler(CreateTicketTool createTicketTool) {
        this.createTicketTool = createTicketTool;
    }

    @Override
    public AgentCommandType commandType() {
        return AgentCommandType.CREATE_TICKET;
    }

    @Override
    public AgentIntent supportIntent() {
        return AgentIntent.CREATE_TICKET;
    }

    @Override
    public String toolName() {
        return createTicketTool.name();
    }

    /**
     * 执行已经完成前置校验的创建工单命令。
     */
    @Override
    public AgentToolResult execute(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters
    ) {
        return createTicketTool.execute(com.smartticket.agent.tool.core.AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(parameters)
                .build());
    }
}
