package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.rag.security.SensitiveInfoDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeAdmissionServiceTest {

    private final KnowledgeAdmissionService service = new KnowledgeAdmissionService(null, null, null, new SensitiveInfoDetector());

    @Test
    void evaluateShouldAutoApproveHighQualityClosedTicket() {
        KnowledgeAdmissionResult result = service.evaluate(Ticket.builder()
                .status(TicketStatusEnum.CLOSED)
                .title("生产登录异常处理案例")
                .description("用户登录生产系统时持续返回 500 错误，影响多个账号正常进入系统。")
                .solutionSummary("重启认证服务并清理异常缓存后恢复，后续补充监控告警。")
                .build(), List.of(
                TicketComment.builder().content("定位到认证服务缓存异常。").build(),
                TicketComment.builder().content("已完成重启和缓存清理。").build()
        ));

        assertEquals(KnowledgeAdmissionDecision.AUTO_APPROVED, result.getDecision());
        assertEquals(100, result.getQualityScore());
    }

    @Test
    void evaluateShouldRequireManualReviewWhenSensitiveInfoAppears() {
        KnowledgeAdmissionResult result = service.evaluate(Ticket.builder()
                .status(TicketStatusEnum.CLOSED)
                .title("账号问题处理")
                .description("用户反馈密码 password=abc123 明文出现在日志里，需要处理。")
                .solutionSummary("已清理日志并重置凭据。")
                .build(), List.of());

        assertEquals(KnowledgeAdmissionDecision.MANUAL_REVIEW, result.getDecision());
    }

    @Test
    void knowledgeWithPhoneEmailTokenShouldRequireManualReview() {
        KnowledgeAdmissionResult result = service.evaluate(Ticket.builder()
                .status(TicketStatusEnum.CLOSED)
                .title("用户凭证泄露排查")
                .description("用户 13812345678 邮箱 user@example.com 的 Bearer token 可能泄露，secret=abc123")
                .solutionSummary("已重置凭证并通知用户。")
                .build(), List.of());

        assertEquals(KnowledgeAdmissionDecision.MANUAL_REVIEW, result.getDecision());
        assertFalse(result.autoApproved());
    }
}
