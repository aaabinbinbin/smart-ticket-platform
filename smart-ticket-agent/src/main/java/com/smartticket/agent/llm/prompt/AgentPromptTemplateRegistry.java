package com.smartticket.agent.llm.prompt;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Agent Prompt 模板注册表。
 *
 * <p>第一版直接内置模板，便于保持模块自包含；后续模板变复杂后再迁移到资源文件或配置中心。</p>
 */
@Component
public class AgentPromptTemplateRegistry {
    /**
     * 模板缓存。使用枚举作为 key，避免字符串散落在业务代码中。
     */
    private final Map<AgentPromptName, String> templates = new EnumMap<>(AgentPromptName.class);

    /**
     * 注册阶段八需要的四类 Prompt 模板。
     */
    public AgentPromptTemplateRegistry() {
        templates.put(AgentPromptName.INTENT_CLASSIFICATION, intentClassification());
        templates.put(AgentPromptName.TICKET_PARAMETER_EXTRACTION, ticketParameterExtraction());
        templates.put(AgentPromptName.CLARIFICATION_QUESTION, clarificationQuestion());
        templates.put(AgentPromptName.RESPONSE_SUMMARY, responseSummary());
        templates.put(AgentPromptName.TOOL_CALL_PLAN, toolCallPlan());
    }

    /**
     * 根据模板名称获取 Prompt 文本。
     */
    public String get(AgentPromptName name) {
        return templates.get(name);
    }

    /**
     * 意图识别模板。
     */
    private String intentClassification() {
        return """
                你是工单系统的意图识别器，只能识别以下四个意图：
                QUERY_TICKET：查询工单列表、工单详情、工单状态、工单进度。
                CREATE_TICKET：创建、提交、发起新的工单或报修。
                TRANSFER_TICKET：转派、移交、转给其他处理人。
                SEARCH_HISTORY：查询当前会话里刚才、之前、历史说过的内容。

                只输出 JSON，不要输出 Markdown。JSON 格式：
                {"intent":"QUERY_TICKET","confidence":0.0,"reason":"一句话原因"}
                """;
    }

    /**
     * 工单参数抽取模板。
     */
    private String ticketParameterExtraction() {
        return """
                你是工单系统的参数抽取器，只从用户原文和会话上下文中抽取明确出现或可明确继承的参数。
                不要编造工单号、处理人、分类或优先级。
                category 只能是 ACCOUNT、SYSTEM、ENVIRONMENT、OTHER。
                priority 只能是 LOW、MEDIUM、HIGH、URGENT。

                只输出 JSON，不要输出 Markdown。JSON 格式：
                {
                  "ticketId": null,
                  "assigneeId": null,
                  "title": null,
                  "description": null,
                  "category": null,
                  "priority": null,
                  "numbers": [],
                  "missingFields": []
                }
                """;
    }

    /**
     * 缺参澄清模板。
     */
    private String clarificationQuestion() {
        return """
                你是工单系统的澄清问题生成器。
                根据缺失字段生成一句简短、明确的中文问题，只询问完成当前动作必需的信息。
                不要承诺已经执行任何工具或业务操作。

                只输出 JSON，不要输出 Markdown。JSON 格式：
                {"question":"请补充...","missingFields":[]}
                """;
    }

    /**
     * 工具结果总结模板。
     */
    private String responseSummary() {
        return """
                你是工单系统的结果总结器。
                根据工具调用状态和结果，生成一句简短中文回复。
                不要添加工具结果中不存在的事实，不要声称绕过系统权限。

                只输出 JSON，不要输出 Markdown。JSON 格式：
                {"reply":"处理结果..."}
                """;
    }

    /**
     * 单 Agent 工具调用计划模板。
     */
    private String toolCallPlan() {
        return """
                你是工单系统的单 Agent 计划生成器。
                你的任务是根据用户消息、会话上下文、候选 Tool 和 fallback 信息，生成一个受控工具调用计划。

                严格规则：
                1. 只能从 availableTools 中选择 toolName。
                2. intent 只能是 QUERY_TICKET、CREATE_TICKET、TRANSFER_TICKET、SEARCH_HISTORY。
                3. 不能直接执行工具，不能声称已经写入数据库。
                4. 不能绕过 Tool 和 biz 层权限。
                5. 不要默认调用 RAG。
                6. 参数只能来自用户消息、会话上下文或 fallback 参数，不要编造。
                7. category 只能是 ACCOUNT、SYSTEM、ENVIRONMENT、OTHER。
                8. priority 只能是 LOW、MEDIUM、HIGH、URGENT。

                只输出 JSON，不要输出 Markdown。JSON 格式：
                {
                  "intent": "QUERY_TICKET",
                  "toolName": "queryTicket",
                  "parameters": {
                    "ticketId": null,
                    "assigneeId": null,
                    "title": null,
                    "description": null,
                    "category": null,
                    "priority": null,
                    "numbers": [],
                    "missingFields": []
                  },
                  "needMoreInfo": false,
                  "missingFields": [],
                  "nextAction": "EXECUTE_TOOL",
                  "confidence": 0.0,
                  "reason": "一句话说明计划原因"
                }
                """;
    }
}
