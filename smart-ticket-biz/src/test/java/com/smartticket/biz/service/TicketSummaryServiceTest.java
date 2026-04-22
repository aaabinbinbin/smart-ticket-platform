package com.smartticket.biz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketSummaryBundleDTO;
import com.smartticket.biz.dto.TicketSummaryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketSummaryServiceTest {
    private final TicketSummaryService ticketSummaryService = new TicketSummaryService();

    @Test
    @DisplayName("应生成三种视角摘要并保留审批与最近动作信息")
    void shouldGenerateAllSummaries() {
        TicketDetailDTO detail = sampleDetail();

        TicketSummaryBundleDTO bundle = ticketSummaryService.generateAll(detail);

        assertNotNull(bundle.getSubmitterSummary());
        assertNotNull(bundle.getAssigneeSummary());
        assertNotNull(bundle.getAdminSummary());
        assertTrue(bundle.getSubmitterSummary().getSummary().contains("待分配"));
        assertTrue(bundle.getAssigneeSummary().getHighlights().stream().anyMatch(item -> item.contains("最新评论")));
        assertTrue(bundle.getAdminSummary().getHighlights().stream().anyMatch(item -> item.contains("审批仍未完成")));
    }

    @Test
    @DisplayName("管理员默认应落到管理员视角，当前处理人默认应落到处理人视角")
    void shouldResolveViewByCurrentUser() {
        Ticket ticket = sampleDetail().getTicket();

        assertEquals(TicketSummaryViewEnum.ADMIN, ticketSummaryService.resolveView(admin(), ticket, null));
        assertEquals(TicketSummaryViewEnum.ASSIGNEE, ticketSummaryService.resolveView(assignee(), ticket, null));
        assertEquals(TicketSummaryViewEnum.SUBMITTER, ticketSummaryService.resolveView(submitter(), ticket, null));
    }

    @Test
    @DisplayName("高风险工单应输出高风险等级")
    void shouldMarkHighRiskSummary() {
        TicketDetailDTO detail = sampleDetail();

        TicketSummaryDTO summary = ticketSummaryService.generateForView(detail, TicketSummaryViewEnum.ADMIN);

        assertEquals("HIGH", summary.getRiskLevel());
        assertTrue(summary.getSummary().contains("高"));
    }

    private TicketDetailDTO sampleDetail() {
        Ticket ticket = Ticket.builder()
                .id(100L)
                .ticketNo("INC202604220001")
                .title("申请生产库只读权限")
                .description("新同事需要查询生产数据，请协助开通只读权限，今天内需要完成。")
                .type(TicketTypeEnum.ACCESS_REQUEST)
                .typeProfile(Map.of(
                        "accountId", "zhangsan",
                        "targetResource", "prod-db-order",
                        "requestedRole", "READ_ONLY",
                        "justification", "排查线上订单问题"
                ))
                .category(TicketCategoryEnum.ACCOUNT)
                .priority(TicketPriorityEnum.URGENT)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .assigneeId(2L)
                .updatedAt(LocalDateTime.now().minusHours(30))
                .build();
        TicketApproval approval = TicketApproval.builder()
                .ticketId(100L)
                .approvalStatus(TicketApprovalStatusEnum.PENDING)
                .approverId(9L)
                .build();
        TicketComment comment = TicketComment.builder()
                .ticketId(100L)
                .commenterId(2L)
                .content("已确认申请信息完整，等待审批。")
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        TicketOperationLog log = TicketOperationLog.builder()
                .ticketId(100L)
                .operatorId(1L)
                .operationType(OperationTypeEnum.SUBMIT_APPROVAL)
                .operationDesc("提交审批")
                .createdAt(LocalDateTime.now().minusHours(3))
                .build();
        return TicketDetailDTO.builder()
                .ticket(ticket)
                .approval(approval)
                .comments(List.of(comment))
                .operationLogs(List.of(log))
                .build();
    }

    private CurrentUser admin() {
        return CurrentUser.builder().userId(9L).roles(List.of("ADMIN")).build();
    }

    private CurrentUser assignee() {
        return CurrentUser.builder().userId(2L).roles(List.of("STAFF")).build();
    }

    private CurrentUser submitter() {
        return CurrentUser.builder().userId(1L).roles(List.of("USER")).build();
    }
}
