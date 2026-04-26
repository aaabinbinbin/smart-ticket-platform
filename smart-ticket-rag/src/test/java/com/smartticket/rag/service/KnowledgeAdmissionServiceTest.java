package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .assigneeId(2L)  // 有人处理过
                .title("生产环境登录异常排查与修复记录")
                .description("用户登录生产系统时持续返回 500 错误，经过网关日志排查、数据库连接检测和应用服务重启后逐步定位到认证服务缓存异常。")
                .solutionSummary("通过分析网关日志确认请求已抵达认证服务但返回 500，数据库连接池正常，排除数据库故障。重启认证服务并清理 Redis 缓存后恢复登录功能，后续补充了认证服务健康检查告警。")
                .build(), List.of(
                TicketComment.builder().content("经排查确认是认证服务 Redis 缓存数据损坏导致所有登录请求失败，已协调运维清理缓存并重启服务。").build(),
                TicketComment.builder().content("已完成缓存清理和服务重启，登录功能恢复正常。建议后续增加认证服务缓存健康检查告警。").build()
        ));

        assertEquals(KnowledgeAdmissionDecision.AUTO_APPROVED, result.getDecision());
        // 新评分：内容+结构+质量信号 ≥ 55 即自动通过
        assertTrue(result.getQualityScore() >= 55);
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
