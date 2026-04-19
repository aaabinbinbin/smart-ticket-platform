package com.smartticket.agent.llm.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 参数抽取结构化输出。
 *
 * <p>该对象是模型抽取的原始结构化结果，后续会合并规则抽取结果并交给 Tool 校验。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmParameterExtractionResult {
    /**
     * 工单 ID，用于查询、转派等需要定位具体工单的动作。
     */
    private Long ticketId;

    /**
     * 目标处理人 ID，用于转派类动作。
     */
    private Long assigneeId;

    /**
     * 工单标题，用于创建工单。
     */
    private String title;

    /**
     * 工单描述，用于创建工单。
     */
    private String description;

    /**
     * 工单分类编码，只允许 ACCOUNT、SYSTEM、ENVIRONMENT、OTHER。
     */
    private String category;

    /**
     * 工单优先级编码，只允许 LOW、MEDIUM、HIGH、URGENT。
     */
    private String priority;

    /**
     * 用户原文中出现的数字列表，用于辅助区分 ticketId、assigneeId 等字段。
     */
    @Builder.Default
    private List<Long> numbers = new ArrayList<>();

    /**
     * 模型认为缺失的字段名，仅作为辅助信息；最终缺参判断以 Tool 校验结果为准。
     */
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();
}
