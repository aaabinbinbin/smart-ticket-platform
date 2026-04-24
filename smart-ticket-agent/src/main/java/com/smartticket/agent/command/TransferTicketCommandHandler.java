package com.smartticket.agent.command;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import com.smartticket.biz.model.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 转派工单命令处理器。
 *
 * <p>该处理器只负责在高风险确认已经完成后，调用 {@link TransferTicketTool} 进行确定性转派。
 * 它不自行决定是否需要确认，不处理 pendingAction，也不直接修改 session、memory 或 trace。</p>
 */
@Component
public class TransferTicketCommandHandler implements AgentCommandHandler {

    private final TransferTicketTool transferTicketTool;

    public TransferTicketCommandHandler(TransferTicketTool transferTicketTool) {
        this.transferTicketTool = transferTicketTool;
    }

    @Override
    public AgentCommandType commandType() {
        return AgentCommandType.TRANSFER_TICKET;
    }

    @Override
    public AgentIntent supportIntent() {
        return AgentIntent.TRANSFER_TICKET;
    }

    @Override
    public String toolName() {
        return transferTicketTool.name();
    }

    /**
     * 执行已经完成 Guard 与确认校验的转派命令。
     */
    @Override
    public AgentToolResult execute(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters
    ) {
        return transferTicketTool.execute(com.smartticket.agent.tool.core.AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(parameters)
                .build());
    }
}
