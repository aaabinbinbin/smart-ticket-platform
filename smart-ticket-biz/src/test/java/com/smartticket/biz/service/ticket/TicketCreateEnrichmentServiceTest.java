package com.smartticket.biz.service.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

class TicketCreateEnrichmentServiceTest {

    private final TicketCreateEnrichmentProperties properties = new TicketCreateEnrichmentProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TicketCreateEnrichmentService enrichment;

    @BeforeEach
    void setUp() {
        // LLM enrichment 默认关闭，仅测试规则 enrichment
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        enrichment = new TicketCreateEnrichmentService(properties, chatModelProvider, objectMapper);
    }

    @Test
    void incidentShouldBeInferredForGeneralText() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500，影响研发自测，清理缓存没用")
                .build();

        TicketCreateCommandDTO result = enrichment.enrich(command);

        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketCategoryEnum.SYSTEM, result.getCategory());
        assertEquals(TicketPriorityEnum.MEDIUM, result.getPriority());
        assertNotNull(result.getTypeProfile());
    }

    @Test
    void accessRequestShouldBeInferredForPermissionText() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("申请开通测试环境数据库权限")
                .description("研发需要访问测试环境数据库进行数据排查")
                .build();

        TicketCreateCommandDTO result = enrichment.enrich(command);

        assertEquals(TicketTypeEnum.ACCESS_REQUEST, result.getType());
        assertEquals(TicketCategoryEnum.ACCOUNT, result.getCategory());
        assertNotNull(result.getTypeProfile());
        assertTrue(result.getTypeProfile().containsKey("accountId"));
        assertTrue(result.getTypeProfile().containsKey("targetResource"));
        assertTrue(result.getTypeProfile().containsKey("requestedRole"));
        assertTrue(result.getTypeProfile().containsKey("justification"));
    }

    @Test
    void changeRequestShouldBeInferredForDeployText() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("数据库表结构变更")
                .description("本次变更涉及用户中心模块的数据库表结构调整，需要发布上线")
                .build();

        TicketCreateCommandDTO result = enrichment.enrich(command);

        assertEquals(TicketTypeEnum.CHANGE_REQUEST, result.getType());
        assertEquals(TicketCategoryEnum.SYSTEM, result.getCategory());
        assertEquals(TicketPriorityEnum.HIGH, result.getPriority());
        assertNotNull(result.getTypeProfile());
        assertTrue(result.getTypeProfile().containsKey("changeTarget"));
        assertTrue(result.getTypeProfile().containsKey("changeWindow"));
        assertTrue(result.getTypeProfile().containsKey("rollbackPlan"));
        assertTrue(result.getTypeProfile().containsKey("impactScope"));
    }

    @Test
    void userExplicitFieldsShouldNotBeOverwritten() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500")
                .type(TicketTypeEnum.INCIDENT)
                .category(TicketCategoryEnum.OTHER)
                .priority(TicketPriorityEnum.URGENT)
                .typeProfile(Map.of("symptom", "用户显式传入的故障现象", "impactScope", "影响所有用户"))
                .build();

        TicketCreateCommandDTO result = enrichment.enrich(command);

        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketCategoryEnum.OTHER, result.getCategory());
        assertEquals(TicketPriorityEnum.URGENT, result.getPriority());
        assertEquals("用户显式传入的故障现象", result.getTypeProfile().get("symptom"));
    }

    @Test
    void environmentRequestShouldBeInferredForEnvText() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("申请测试环境容器")
                .description("需要一套测试环境用于集成测试，项目名为 smart-ticket")
                .build();

        TicketCreateCommandDTO result = enrichment.enrich(command);

        assertEquals(TicketTypeEnum.ENVIRONMENT_REQUEST, result.getType());
        assertEquals(TicketCategoryEnum.ENVIRONMENT, result.getCategory());
        assertNotNull(result.getTypeProfile());
        assertTrue(result.getTypeProfile().containsKey("environmentName"));
        assertTrue(result.getTypeProfile().containsKey("resourceSpec"));
        assertTrue(result.getTypeProfile().containsKey("purpose"));
    }

    @Test
    void incidentTypeProfileShouldPassValidation() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("线上系统崩溃")
                .description("大量用户无法访问，返回 503，影响全部用户")
                .build();

        TicketCreateCommandDTO result = enrichment.enrich(command);

        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketPriorityEnum.URGENT, result.getPriority());
        assertNotNull(result.getTypeProfile());
        assertNotNull(result.getTypeProfile().get("symptom"));
        assertNotNull(result.getTypeProfile().get("impactScope"));
    }
}
