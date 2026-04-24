package com.smartticket.agent.tool.core;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Tool 统一请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolRequest {
    // 当前用户
    private CurrentUser currentUser;
    // 消息
    private String message;
    // 上下文
    private AgentSessionContext context;
    // route
    private IntentRoute route;
    // 参数
    private AgentToolParameters parameters;
}
