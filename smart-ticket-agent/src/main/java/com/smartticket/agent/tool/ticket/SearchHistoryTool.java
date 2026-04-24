package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.rag.model.RetrievalRequest;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 查询历史经验 Tool。
 *
 * <p>该 Tool 只服务 SEARCH_HISTORY 意图，调用 rag 检索链路。检索结果只作参考，
 * 不替代当前工单事实查询，也不改变任何工单状态。</p>
 */
@Component
public class SearchHistoryTool implements AgentTool {
    // NAME
    private static final String NAME = "searchHistory";

    /**
     * 历史知识检索服务。
     */
    private final RetrievalService retrievalService;

    /**
     * Spring AI Tool Calling 适配支持。
     */
    private final SpringAiToolSupport springAiToolSupport;

    /**
     * 构造搜索历史工具。
     */
    public SearchHistoryTool(RetrievalService retrievalService, @Lazy SpringAiToolSupport springAiToolSupport) {
        this.retrievalService = retrievalService;
        this.springAiToolSupport = springAiToolSupport;
    }

    /**
     * 处理名称。
     */
    @Override
    public String name() {
        return NAME;
    }

    /**
     * 处理支撑。
     */
    @Override
    public boolean support(AgentIntent intent) {
        return intent == AgentIntent.SEARCH_HISTORY;
    }

    /**
     * 处理元数据。
     */
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

    /**
     * 执行操作。
     */
    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        AgentSessionContext context = request.getContext();
        List<String> messages = context == null || context.getRecentMessages() == null
                ? List.of()
                : context.getRecentMessages();
        String queryText = hasText(request.getParameters().getDescription())
                ? request.getParameters().getDescription()
                : request.getMessage();
        RetrievalResult retrievalResult = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText(queryText)
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

    /**
     * Spring AI Tool Calling 入口。
     *
     * @param queryText 检索文本；为空时使用用户原始消息
     * @param toolContext Spring AI Tool 上下文
     * @return Tool 执行结果
     */
    @Tool(name = NAME, description = "检索历史工单经验和相似案例。结果只作参考，不作为当前工单事实裁决。")
    public AgentToolResult searchHistory(
            @ToolParam(required = false, description = "历史经验检索文本，为空时使用用户原始消息") String queryText,
            ToolContext toolContext
    ) {
        return springAiToolSupport.execute(
                this,
                toolContext,
                AgentIntent.SEARCH_HISTORY,
                AgentToolParameters.builder()
                        .description(queryText)
                        .build()
        );
    }

    /**
     * 判断字符串是否有有效内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
