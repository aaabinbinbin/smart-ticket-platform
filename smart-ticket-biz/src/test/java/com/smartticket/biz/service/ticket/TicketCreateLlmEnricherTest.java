package com.smartticket.biz.service.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * LLM enrichment 测试。
 *
 * <p>全部使用 mock，不调用真实 LLM API。</p>
 */
class TicketCreateLlmEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
    }

    /**
     * 创建带 mock ChatClient 的 TicketCreateLlmEnricher。
     */
    private TicketCreateLlmEnricher createEnricher() {
        return new TicketCreateLlmEnricher(chatClient,
                new TicketCreateEnrichmentProperties(), objectMapper);
    }

    /**
     * 创建带 mock ChatClient 的 enrichment service（LLM 启用）。
     */
    private TicketCreateEnrichmentService createLlmService() {
        TicketCreateEnrichmentProperties props = new TicketCreateEnrichmentProperties();
        props.setLlmEnabled(true);
        TicketCreateLlmEnricher enricher = new TicketCreateLlmEnricher(chatClient, props, objectMapper);
        return new TicketCreateEnrichmentService(props, enricher);
    }

    /**
     * 模拟 ChatClient 调用返回指定 JSON。
     */
    private void mockLlmResponse(String jsonResponse) {
        when(responseSpec.content()).thenReturn(jsonResponse);
    }

    // ========== 规则 enrichment 测试（LLM 关闭） ==========

    @Test
    void ruleEnrichmentShouldWorkWhenLlmDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        TicketCreateEnrichmentProperties props = new TicketCreateEnrichmentProperties(); // 默认 false
        TicketCreateEnrichmentService service = new TicketCreateEnrichmentService(props, chatModelProvider, objectMapper);

        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500，影响研发自测")
                .build());

        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertNotNull(result.getTypeProfile());
    }

    // ========== LLM enrichment 启用 ==========

    @Test
    void llmEnrichmentShouldUseValidResult() {
        mockLlmResponse("""
                {
                  "type": "ACCESS_REQUEST",
                  "category": "ACCOUNT",
                  "priority": "HIGH",
                  "typeProfile": {
                    "accountId": "待确认",
                    "targetResource": "测试环境数据库",
                    "requestedRole": "只读权限",
                    "justification": "用于数据排查"
                  },
                  "confidence": 0.85,
                  "reason": "描述中提到需要访问测试环境数据库进行数据排查"
                }
                """);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("需要访问测试环境数据库")
                .description("研发需要访问测试环境数据库进行数据排查")
                .build());

        assertEquals(TicketTypeEnum.ACCESS_REQUEST, result.getType());
        assertEquals(TicketCategoryEnum.ACCOUNT, result.getCategory());
        assertEquals(TicketPriorityEnum.HIGH, result.getPriority());
        assertNotNull(result.getTypeProfile());
        assertEquals("测试环境数据库", result.getTypeProfile().get("targetResource"));
    }

    @Test
    void userExplicitFieldsShouldWinOverLlm() {
        mockLlmResponse("""
                {
                  "type": "INCIDENT",
                  "category": "SYSTEM",
                  "priority": "HIGH",
                  "typeProfile": {
                    "symptom": "LLM 抽取的故障现象",
                    "impactScope": "LLM 的影响范围"
                  },
                  "confidence": 0.88,
                  "reason": "测试用"
                }
                """);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("系统崩溃")
                .description("系统崩溃无法使用")
                .priority(TicketPriorityEnum.URGENT)
                .typeProfile(Map.of("symptom", "用户填写的内容"))
                .build());

        // 用户显式传了 URGENT，不应被 LLM 的 HIGH 覆盖
        assertEquals(TicketPriorityEnum.URGENT, result.getPriority());
        // 用户传了 symptom，不应被 LLM 覆盖
        assertEquals("用户填写的内容", result.getTypeProfile().get("symptom"));
        // LLM 填的 impactScope 应保留（用户没传 impactScope）
        assertEquals("LLM 的影响范围", result.getTypeProfile().get("impactScope"));
    }

    // ========== LLM 失败降级 ==========

    @Test
    void llmInvalidJsonShouldFallbackToRules() {
        mockLlmResponse("这不是合法 JSON {");

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500")
                .build());

        // LLM 失败后降级到规则，应该能正确推断
        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketCategoryEnum.SYSTEM, result.getCategory());
        assertNotNull(result.getTypeProfile());
    }

    @Test
    void llmReturnedNullShouldFallbackToRules() {
        mockLlmResponse(null);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("申请权限")
                .description("需要开通数据库权限")
                .build());

        assertEquals(TicketTypeEnum.ACCESS_REQUEST, result.getType());
    }

    @Test
    void llmBlankResponseShouldFallbackToRules() {
        mockLlmResponse("   ");

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("申请权限")
                .description("需要开通数据库权限")
                .build());

        assertEquals(TicketTypeEnum.ACCESS_REQUEST, result.getType());
    }

    // ========== LLM 返回非法枚举 ==========

    @Test
    void llmInvalidEnumShouldFallbackThatFieldToRules() {
        mockLlmResponse("""
                {
                  "type": "INVALID_TYPE_123",
                  "category": "INVALID_CATEGORY",
                  "priority": "INVALID_PRIORITY",
                  "typeProfile": {},
                  "confidence": 0.9,
                  "reason": "测试非法枚举"
                }
                """);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("系统崩溃")
                .description("系统报错无法访问，影响研发")
                .build());

        // 非法枚举应降级到规则推断
        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketCategoryEnum.SYSTEM, result.getCategory());
        assertEquals(TicketPriorityEnum.MEDIUM, result.getPriority());
    }

    // ========== LLM typeProfile 缺字段 ==========

    @Test
    void llmTypeProfileMissingFieldsShouldBeFilledByRules() {
        mockLlmResponse("""
                {
                  "type": "INCIDENT",
                  "category": "SYSTEM",
                  "priority": "HIGH",
                  "typeProfile": {
                    "symptom": "登录报错"
                  },
                  "confidence": 0.8,
                  "reason": "测试缺字段"
                }
                """);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("登录报错")
                .description("登录时报 500")
                .build());

        assertEquals("登录报错", result.getTypeProfile().get("symptom"));
        // impactScope 由规则或默认兜底补齐
        assertNotNull(result.getTypeProfile().get("impactScope"));
    }

    // ========== LLM confidence 过低 ==========

    @Test
    void llmLowConfidenceShouldFallbackToRules() {
        mockLlmResponse("""
                {
                  "type": "INCIDENT",
                  "category": "SYSTEM",
                  "priority": "HIGH",
                  "typeProfile": {
                    "symptom": "登录报错",
                    "impactScope": "影响单个用户"
                  },
                  "confidence": 0.2,
                  "reason": "信息不足，置信度较低"
                }
                """);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500")
                .build());

        // 置信度过低，应使用规则推断
        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketCategoryEnum.SYSTEM, result.getCategory());
    }

    // ========== LLM 异常抛出 ==========

    @Test
    void llmExceptionShouldNotBreakCreateTicket() {
        // prompt() 抛出异常
        when(chatClient.prompt()).thenThrow(new RuntimeException("LLM 调用超时"));

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500，影响研发自测，清理缓存没用")
                .build());

        // LLM 异常不应导致创建失败，应降级到规则
        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertNotNull(result.getTypeProfile());
    }

    // ========== LLM 返回带 Markdown 代码块的 JSON ==========

    @Test
    void llmResponseWithMarkdownCodeBlockShouldBeParsed() {
        // 模拟 LLM 返回带 ```json 包裹的响应
        mockLlmResponse("""
                ```json
                {
                  "type": "CHANGE_REQUEST",
                  "category": "SYSTEM",
                  "priority": "HIGH",
                  "typeProfile": {
                    "changeTarget": "用户中心数据库表结构调整",
                    "changeWindow": "待确认",
                    "rollbackPlan": "待确认",
                    "impactScope": "影响用户中心"
                  },
                  "confidence": 0.9,
                  "reason": "描述中包含变更和数据库调整"
                }
                ```
                """);

        TicketCreateEnrichmentService service = createLlmService();
        TicketCreateCommandDTO result = service.enrich(TicketCreateCommandDTO.builder()
                .title("用户中心数据库需求")
                .description("需要修改用户中心数据库表结构，涉及变更")
                .build());

        assertEquals(TicketTypeEnum.CHANGE_REQUEST, result.getType());
        assertNotNull(result.getTypeProfile());
        assertEquals("用户中心数据库表结构调整", result.getTypeProfile().get("changeTarget"));
    }

    // ========== TicketCreateLlmEnricher.parseAndValidate 直接测试 ==========

    @Test
    void parseAndValidateShouldHandleValidJson() {
        TicketCreateLlmEnricher enricher = createEnricher();

        TicketCreateLlmEnricher.LlmEnrichmentResult result = enricher.parseAndValidate("""
                {
                  "type": "INCIDENT",
                  "category": "SYSTEM",
                  "priority": "HIGH",
                  "typeProfile": {
                    "symptom": "登录报错",
                    "impactScope": "影响研发"
                  },
                  "confidence": 0.85,
                  "reason": "描述中包含登录报错"
                }
                """);

        assertNotNull(result);
        assertEquals(TicketTypeEnum.INCIDENT, result.type());
        assertEquals(TicketCategoryEnum.SYSTEM, result.category());
        assertEquals(TicketPriorityEnum.HIGH, result.priority());
        assertTrue(result.isUsable(0.5));
        assertEquals("登录报错", result.typeProfile().get("symptom"));
    }

    @Test
    void parseAndValidateShouldReturnNullForInvalidJson() {
        TicketCreateLlmEnricher enricher = createEnricher();

        TicketCreateLlmEnricher.LlmEnrichmentResult result = enricher.parseAndValidate("不是 JSON");

        assertNull(result);
    }

    @Test
    void parseAndValidateShouldReturnNullForEmptyJson() {
        TicketCreateLlmEnricher enricher = createEnricher();

        TicketCreateLlmEnricher.LlmEnrichmentResult result = enricher.parseAndValidate("{}");

        // 空 JSON → 无有效字段，返回 null
        assertNull(result);
    }
}
