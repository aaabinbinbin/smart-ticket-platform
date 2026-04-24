package com.smartticket.agent.reply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import org.junit.jupiter.api.Test;

/**
 * AgentReplyRenderer 的 P1 单元测试。
 *
 * <p>这些测试只验证结构化结果到最终回复的纯渲染逻辑，确保渲染器本身不依赖数据库、
 * 不调用 tool，也不会修改任何会话状态。</p>
 */
class AgentReplyRendererTest {

    private final AgentReplyRenderer renderer = new AgentReplyRenderer();

    @Test
    void renderShouldPreferToolReplyForDeterministicSuccess() {
        String reply = renderer.render(
                route(AgentIntent.QUERY_TICKET),
                null,
                null,
                AgentExecutionSummary.builder()
                        .status(AgentTurnStatus.COMPLETED)
                        .mode(AgentExecutionMode.READ_ONLY_DETERMINISTIC)
                        .intent(AgentIntent.QUERY_TICKET)
                        .primaryResult(AgentToolResult.builder()
                                .status(AgentToolStatus.SUCCESS)
                                .toolName("queryTicket")
                                .reply("已查询工单详情。")
                                .build())
                        .build()
        );

        assertEquals("已查询工单详情。", reply);
    }

    @Test
    void renderShouldKeepModelReplyForReadOnlyReact() {
        String reply = renderer.render(
                route(AgentIntent.SEARCH_HISTORY),
                null,
                null,
                AgentExecutionSummary.builder()
                        .status(AgentTurnStatus.COMPLETED)
                        .mode(AgentExecutionMode.READ_ONLY_REACT)
                        .intent(AgentIntent.SEARCH_HISTORY)
                        .modelReply("根据检索结果，历史上常见原因是账号权限未同步。")
                        .primaryResult(AgentToolResult.builder()
                                .status(AgentToolStatus.SUCCESS)
                                .toolName("searchHistory")
                                .reply("已检索到历史经验。")
                                .build())
                        .build()
        );

        assertEquals("根据检索结果，历史上常见原因是账号权限未同步。", reply);
    }

    @Test
    void renderShouldReturnConfirmationPromptWhenNeedConfirmation() {
        String reply = renderer.render(
                route(AgentIntent.TRANSFER_TICKET),
                null,
                null,
                AgentExecutionSummary.builder()
                        .status(AgentTurnStatus.NEED_CONFIRMATION)
                        .mode(AgentExecutionMode.HIGH_RISK_CONFIRMATION)
                        .intent(AgentIntent.TRANSFER_TICKET)
                        .primaryResult(AgentToolResult.builder()
                                .status(AgentToolStatus.NEED_MORE_INFO)
                                .toolName("transferTicket")
                                .reply("高风险操作需要确认。")
                                .build())
                        .build()
        );

        assertEquals("高风险操作需要确认。", reply);
    }

    @Test
    void renderShouldReturnNeedMoreInfoFallbackWhenReplyMissing() {
        String reply = renderer.render(
                route(AgentIntent.CREATE_TICKET),
                null,
                null,
                AgentExecutionSummary.builder()
                        .status(AgentTurnStatus.NEED_MORE_INFO)
                        .mode(AgentExecutionMode.WRITE_COMMAND_DRAFT)
                        .intent(AgentIntent.CREATE_TICKET)
                        .primaryResult(AgentToolResult.builder()
                                .status(AgentToolStatus.NEED_MORE_INFO)
                                .toolName("createTicket")
                                .build())
                        .build()
        );

        assertEquals("请补充继续处理所需的信息。", reply);
    }

    @Test
    void renderShouldReturnFailureFallbackWhenToolReplyMissing() {
        String reply = renderer.render(
                route(AgentIntent.QUERY_TICKET),
                null,
                null,
                AgentExecutionSummary.builder()
                        .status(AgentTurnStatus.FAILED)
                        .mode(AgentExecutionMode.READ_ONLY_DETERMINISTIC)
                        .intent(AgentIntent.QUERY_TICKET)
                        .primaryResult(AgentToolResult.builder()
                                .status(AgentToolStatus.FAILED)
                                .toolName("queryTicket")
                                .build())
                        .build()
        );

        assertEquals("当前请求未能完成，请稍后重试。", reply);
    }

    private IntentRoute route(AgentIntent intent) {
        return IntentRoute.builder()
                .intent(intent)
                .confidence(0.95d)
                .reason("test")
                .build();
    }
}
