package com.smartticket.agent.command;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 写操作命令草稿。
 *
 * <p>该对象位于参数提取与最终执行之间，用于显式表达当前写操作已经识别出的命令类型、
 * 结构化参数、缺参状态和确认要求。它只承载命令语义，不直接执行数据库写入，
 * 也不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCommandDraft {
    /**
     * 当前草稿对应的写命令类型。
     */
    private AgentCommandType commandType;

    /**
     * 当前草稿来源的业务意图。
     */
    private AgentIntent intent;

    /**
     * 当前命令最终会落到哪个确定性工具名。
     */
    private String toolName;

    /**
     * 当前已经抽取并合并出的结构化参数。
     */
    private AgentToolParameters parameters;

    /**
     * 当前仍然缺少的必要字段。
     */
    @Builder.Default
    private List<AgentToolParameterField> missingFields = new ArrayList<>();

    /**
     * 当前命令是否需要二次确认。
     */
    private boolean confirmationRequired;

    /**
     * 当前命令面向用户或 trace 的简要预览。
     */
    private String previewText;

    /**
     * 判断草稿是否仍然缺少必要字段。
     *
     * @return true 表示当前只能继续补参，不能进入确定性执行
     */
    public boolean hasMissingFields() {
        return missingFields != null && !missingFields.isEmpty();
    }
}
