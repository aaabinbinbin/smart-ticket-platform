package com.smartticket.agent.tool.support;

import com.smartticket.agent.tool.core.AgentToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Spring AI 工具调用状态记录器。
 *
 * <p>记录一次对话中 LLM 发起的全部工具调用历史，支持多步 ReAct 循环追踪。
 * 每次工具调用通过 {@link #record(String, AgentToolResult)} 追加记录，
 * 对话结束后可通过 {@link #getAllCalls()} 获取完整调用链。</p>
 */
@Getter
public class SpringAiToolCallState {

    /** 单次对话中允许的最大工具调用次数，防止 LLM 无限循环。 */
    private static final int MAX_TOOL_CALLS = 15;

    private final List<AgentToolCallRecord> calls = new ArrayList<>();

    /**
     * 记录一次工具调用。
     *
     * @return true 记录成功；false 已达最大调用次数限制，应阻止后续执行
     */
    public boolean record(String toolName, AgentToolResult result) {
        if (calls.size() >= MAX_TOOL_CALLS) {
            return false;
        }
        calls.add(new AgentToolCallRecord(toolName, result));
        return true;
    }

    /**
     * 获取全部工具调用记录（不可变视图）。
     */
    public List<AgentToolCallRecord> getAllCalls() {
        return Collections.unmodifiableList(calls);
    }

    /**
     * 获取最近一次工具调用记录。
     */
    public AgentToolCallRecord getLastCall() {
        if (calls.isEmpty()) {
            return null;
        }
        return calls.get(calls.size() - 1);
    }

    /**
     * 获取最近一次工具调用结果。
     */
    public AgentToolResult getResult() {
        AgentToolCallRecord last = getLastCall();
        return last != null ? last.getResult() : null;
    }

    /**
     * 获取当前已调用的工具名称列表。
     */
    public List<String> getInvokedToolNames() {
        return calls.stream()
                .map(AgentToolCallRecord::getToolName)
                .toList();
    }

    /**
     * 判断是否还有可用调用次数。
     */
    public boolean canContinue() {
        return calls.size() < MAX_TOOL_CALLS;
    }

    /**
     * 工具调用记录条目。
     */
    @Getter
    public static class AgentToolCallRecord {
        private final String toolName;
        private final AgentToolResult result;

        public AgentToolCallRecord(String toolName, AgentToolResult result) {
            this.toolName = toolName;
            this.result = result;
        }
    }
}
