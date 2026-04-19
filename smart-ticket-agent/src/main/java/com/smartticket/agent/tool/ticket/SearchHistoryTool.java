package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.rag.model.RetrievalRequest;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 查询历史经验 Tool。
 *
 * <p>该 Tool 只服务 SEARCH_HISTORY 意图，用于检索已沉淀的历史工单知识；当前会话消息作为补充信息返回，
 * 不替代当前工单事实查询。</p>
 */
@Component
public class SearchHistoryTool implements AgentTool {
    private static final String NAME = "searchHistory";

    /** 历史知识检索服务。 */
    private final RetrievalService retrievalService;

    public SearchHistoryTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean support(AgentIntent intent) {
        return intent == AgentIntent.SEARCH_HISTORY;
    }

    @Override
    public AgentToolMetadata metadata() {
        return AgentToolMetadata.builder()
                .name(NAME)
                .description("检索历史工单经验，并返回当前会话近期消息作为补充参考")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .requireConfirmation(false)
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        AgentSessionContext context = request.getContext();
        List<String> messages = context == null || context.getRecentMessages() == null
                ? List.of()
                : context.getRecentMessages();
        RetrievalResult retrievalResult = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText(request.getMessage())
                .topK(3)
                .rewrite(true)
                .build());
        return AgentToolResults.success(
                NAME,
                retrievalResult.getHits().isEmpty()
                        ? "未检索到高相关历史经验，以下仅供继续描述问题时参考。"
                        : "已检索到相似历史经验，结果仅供参考，不作为当前工单事实裁决。",
                Map.of(
                        "retrieval", retrievalResult,
                        "recentMessages", messages
                )
        );
    }
}
