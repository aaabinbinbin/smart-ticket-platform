package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryBundleDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 根据工单详情生成不同视角的摘要结果。
 * 当前支持提单人、处理人和管理员三个视角，分别关注进度、执行信息和风险。
 */
@Service
public class TicketSummaryService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public TicketSummaryBundleDTO generateAll(TicketDetailDTO detail) {
        if (detail == null || detail.getTicket() == null) {
            return TicketSummaryBundleDTO.builder().build();
        }
        return TicketSummaryBundleDTO.builder()
                .submitterSummary(buildSubmitterSummary(detail))
                .assigneeSummary(buildAssigneeSummary(detail))
                .adminSummary(buildAdminSummary(detail))
                .build();
    }

    public TicketSummaryDTO generateForView(TicketDetailDTO detail, TicketSummaryViewEnum view) {
        if (view == null) {
            return null;
        }
        TicketSummaryBundleDTO bundle = detail.getSummaries() == null ? generateAll(detail) : detail.getSummaries();
        return switch (view) {
            case SUBMITTER -> bundle.getSubmitterSummary();
            case ASSIGNEE -> bundle.getAssigneeSummary();
            case ADMIN -> bundle.getAdminSummary();
        };
    }

    public TicketSummaryViewEnum resolveView(CurrentUser operator, Ticket ticket, TicketSummaryViewEnum requestedView) {
        if (requestedView != null) {
            return requestedView;
        }
        if (operator != null && operator.isAdmin()) {
            return TicketSummaryViewEnum.ADMIN;
        }
        if (operator != null && ticket != null && Objects.equals(operator.getUserId(), ticket.getAssigneeId())) {
            return TicketSummaryViewEnum.ASSIGNEE;
        }
        return TicketSummaryViewEnum.SUBMITTER;
    }

    private TicketSummaryDTO buildSubmitterSummary(TicketDetailDTO detail) {
        Ticket ticket = detail.getTicket();
        List<String> highlights = new ArrayList<>();
        highlights.add("当前状态：" + ticket.getStatus().getInfo());
        highlights.add("当前处理人：" + userLabel(ticket.getAssigneeId(), "暂未分配"));
        appendApprovalHighlight(highlights, detail.getApproval());
        highlights.add("最近动作：" + latestActivity(detail));
        highlights.add("下一步：" + nextStepForSubmitter(ticket, detail.getApproval()));
        return TicketSummaryDTO.builder()
                .view(TicketSummaryViewEnum.SUBMITTER)
                .title("提单人进展摘要")
                .summary("工单 " + ticket.getTicketNo() + " 当前处于" + ticket.getStatus().getInfo()
                        + "，" + assigneeSentence(ticket.getAssigneeId()) + approvalSentence(detail.getApproval()))
                .highlights(highlights)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private TicketSummaryDTO buildAssigneeSummary(TicketDetailDTO detail) {
        Ticket ticket = detail.getTicket();
        List<String> highlights = new ArrayList<>();
        highlights.add("问题现象：" + summarizeText(ticket.getDescription(), 80));
        appendTypeProfileHighlights(highlights, ticket.getTypeProfile(), 2);
        appendRecentWorkHighlights(highlights, detail);
        if (detail.getApproval() != null) {
            highlights.add("审批状态：" + detail.getApproval().getApprovalStatus().getInfo());
        }
        return TicketSummaryDTO.builder()
                .view(TicketSummaryViewEnum.ASSIGNEE)
                .title("处理人问题与动作摘要")
                .summary("工单主题为“" + ticket.getTitle() + "”，当前状态为" + ticket.getStatus().getInfo()
                        + "。建议优先关注核心现象、最近处理动作和审批阻塞。")
                .highlights(highlights)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private TicketSummaryDTO buildAdminSummary(TicketDetailDTO detail) {
        Ticket ticket = detail.getTicket();
        RiskSummary riskSummary = evaluateRisk(detail);
        return TicketSummaryDTO.builder()
                .view(TicketSummaryViewEnum.ADMIN)
                .title("管理员风险摘要")
                .summary("工单 " + ticket.getTicketNo() + " 的当前风险等级为" + riskLevelInfo(riskSummary.score())
                        + "，需关注分配、审批和处理推进节奏。")
                .highlights(riskSummary.highlights())
                .riskLevel(riskLevelCode(riskSummary.score()))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private RiskSummary evaluateRisk(TicketDetailDTO detail) {
        Ticket ticket = detail.getTicket();
        List<String> risks = new ArrayList<>();
        int riskScore = 0;

        riskScore += appendPriorityRisk(risks, ticket);
        riskScore += appendStatusRisk(risks, ticket);
        riskScore += appendApprovalRisk(risks, detail.getApproval());
        riskScore += appendTransferRisk(risks, detail);
        riskScore += appendStaleRisk(risks, ticket);

        if (risks.isEmpty()) {
            risks.add("当前未发现明显升级风险");
        }
        return new RiskSummary(riskScore, risks);
    }

    private int appendPriorityRisk(List<String> risks, Ticket ticket) {
        if (ticket.getPriority() == null) {
            return 0;
        }
        if ("URGENT".equals(ticket.getPriority().getCode())) {
            risks.add("工单优先级为紧急");
            return 2;
        }
        if ("HIGH".equals(ticket.getPriority().getCode())) {
            risks.add("工单优先级较高");
            return 1;
        }
        return 0;
    }

    private int appendStatusRisk(List<String> risks, Ticket ticket) {
        if (ticket.getStatus() == TicketStatusEnum.PENDING_ASSIGN) {
            risks.add("工单仍处于待分配状态");
            return 2;
        }
        return 0;
    }

    private int appendApprovalRisk(List<String> risks, TicketApproval approval) {
        if (approval == null || approval.getApprovalStatus() == null) {
            return 0;
        }
        if (approval.getApprovalStatus() == TicketApprovalStatusEnum.PENDING) {
            risks.add("审批仍未完成");
            return 2;
        }
        if (approval.getApprovalStatus() == TicketApprovalStatusEnum.REJECTED) {
            risks.add("审批已驳回，需要重新评估");
            return 2;
        }
        return 0;
    }

    private int appendTransferRisk(List<String> risks, TicketDetailDTO detail) {
        long transferCount = operationLogs(detail).stream()
                .filter(log -> log.getOperationType() == OperationTypeEnum.TRANSFER)
                .count();
        if (transferCount >= 2) {
            risks.add("转派次数较多，可能存在归属不清");
            return 1;
        }
        return 0;
    }

    private int appendStaleRisk(List<String> risks, Ticket ticket) {
        if (isStale(ticket.getUpdatedAt())) {
            risks.add("最近 24 小时未见更新");
            return 1;
        }
        return 0;
    }

    private void appendApprovalHighlight(List<String> highlights, TicketApproval approval) {
        if (approval == null || approval.getApprovalStatus() == null) {
            return;
        }
        highlights.add("审批状态：" + approval.getApprovalStatus().getInfo());
    }

    private void appendTypeProfileHighlights(List<String> highlights, Map<String, Object> profile, int limit) {
        if (profile == null || profile.isEmpty()) {
            return;
        }
        List<String> entries = profile.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank())
                .sorted(Map.Entry.comparingByKey())
                .limit(limit)
                .map(entry -> profileLabel(entry.getKey()) + "：" + summarizeText(String.valueOf(entry.getValue()), 36))
                .toList();
        highlights.addAll(entries);
    }

    private void appendRecentWorkHighlights(List<String> highlights, TicketDetailDTO detail) {
        TicketComment latestComment = latestComment(detail);
        TicketOperationLog latestLog = latestLog(detail);
        if (latestComment != null) {
            highlights.add("最新评论：" + summarizeText(latestComment.getContent(), 48));
        }
        if (latestLog != null) {
            highlights.add("最近操作：" + latestLog.getOperationType().getInfo() + "（" + formatTime(latestLog.getCreatedAt()) + "）");
        }
    }

    private TicketComment latestComment(TicketDetailDTO detail) {
        return comments(detail).stream()
                .filter(comment -> comment.getCreatedAt() != null)
                .max(Comparator.comparing(TicketComment::getCreatedAt))
                .orElse(null);
    }

    private TicketOperationLog latestLog(TicketDetailDTO detail) {
        return operationLogs(detail).stream()
                .filter(log -> log.getCreatedAt() != null)
                .max(Comparator.comparing(TicketOperationLog::getCreatedAt))
                .orElse(null);
    }

    private String nextStepForSubmitter(Ticket ticket, TicketApproval approval) {
        if (approval != null && approval.getApprovalStatus() == TicketApprovalStatusEnum.PENDING) {
            return "等待审批人完成当前审批步骤。";
        }
        if (approval != null && approval.getApprovalStatus() == TicketApprovalStatusEnum.REJECTED) {
            return "根据驳回意见补充信息后重新提交审批。";
        }
        if (ticket.getStatus() == TicketStatusEnum.PENDING_ASSIGN) {
            return "等待系统或管理员分配处理人。";
        }
        if (ticket.getStatus() == TicketStatusEnum.PROCESSING) {
            return "等待处理人继续排查并同步处理结果。";
        }
        if (ticket.getStatus() == TicketStatusEnum.RESOLVED) {
            return "确认解决方案是否生效，必要时关闭工单。";
        }
        return "当前流程已结束，如仍有问题可继续补充评论。";
    }

    private String approvalSentence(TicketApproval approval) {
        if (approval == null || approval.getApprovalStatus() == null) {
            return "";
        }
        return "审批状态为" + approval.getApprovalStatus().getInfo() + "。";
    }

    private String assigneeSentence(Long assigneeId) {
        if (assigneeId == null) {
            return "当前尚未分配处理人，";
        }
        return "当前由用户#" + assigneeId + " 跟进，";
    }

    private String latestActivity(TicketDetailDTO detail) {
        List<ActivityItem> activities = new ArrayList<>();
        comments(detail).stream()
                .filter(comment -> comment.getCreatedAt() != null)
                .map(comment -> new ActivityItem(comment.getCreatedAt(), "评论：" + summarizeText(comment.getContent(), 40)))
                .forEach(activities::add);
        operationLogs(detail).stream()
                .filter(log -> log.getCreatedAt() != null)
                .map(log -> new ActivityItem(log.getCreatedAt(), log.getOperationType().getInfo()))
                .forEach(activities::add);
        return activities.stream()
                .max(Comparator.comparing(ActivityItem::time))
                .map(item -> formatTime(item.time()) + " " + item.content())
                .orElse("暂无协作记录");
    }

    private boolean isStale(LocalDateTime updatedAt) {
        return updatedAt != null && updatedAt.isBefore(LocalDateTime.now().minusHours(24));
    }

    private String riskLevelCode(int riskScore) {
        if (riskScore >= 4) {
            return "HIGH";
        }
        if (riskScore >= 2) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String riskLevelInfo(int riskScore) {
        return switch (riskLevelCode(riskScore)) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            default -> "低";
        };
    }

    private String profileLabel(String key) {
        return switch (key) {
            case "symptom" -> "故障现象";
            case "impactScope" -> "影响范围";
            case "accountId" -> "账号";
            case "targetResource" -> "目标资源";
            case "requestedRole" -> "申请角色";
            case "justification" -> "申请原因";
            case "environmentName" -> "环境名称";
            case "resourceSpec" -> "资源规格";
            case "purpose" -> "用途";
            case "questionTopic" -> "咨询主题";
            case "expectedOutcome" -> "期望结果";
            case "changeTarget" -> "变更对象";
            case "changeWindow" -> "变更窗口";
            case "rollbackPlan" -> "回滚方案";
            default -> key;
        };
    }

    private String summarizeText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(maxLength - 1, 1)) + "…";
    }

    private String userLabel(Long userId, String fallback) {
        return userId == null ? fallback : "用户#" + userId;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "未知时间" : TIME_FORMATTER.format(time);
    }

    private List<TicketComment> comments(TicketDetailDTO detail) {
        return detail.getComments() == null ? Collections.emptyList() : detail.getComments();
    }

    private List<TicketOperationLog> operationLogs(TicketDetailDTO detail) {
        return detail.getOperationLogs() == null ? Collections.emptyList() : detail.getOperationLogs();
    }

    private record ActivityItem(LocalDateTime time, String content) {
    }

    private record RiskSummary(int score, List<String> highlights) {
    }
}
