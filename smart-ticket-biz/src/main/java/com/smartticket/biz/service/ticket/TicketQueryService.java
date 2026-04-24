package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.dto.ticket.TicketPageQueryDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.biz.service.type.TicketTypeProfileService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 负责工单读模型查询，包括详情、摘要和分页列表。
 * 这里聚焦查询装配，缓存命中、审批补充信息和摘要生成都在这里收口。
 */
@Service
public class TicketQueryService {
    // 支撑
    private final TicketServiceSupport support;
    // 工单仓储
    private final TicketRepository ticketRepository;
    // 工单详情缓存服务
    private final TicketDetailCacheService ticketDetailCacheService;
    // 工单类型画像服务
    private final TicketTypeProfileService ticketTypeProfileService;
    // 工单审批服务
    private final TicketApprovalService ticketApprovalService;
    // 工单摘要服务
    private final TicketSummaryService ticketSummaryService;

    /**
     * 构造工单查询服务。
     */
    public TicketQueryService(
            TicketServiceSupport support,
            TicketRepository ticketRepository,
            TicketDetailCacheService ticketDetailCacheService,
            TicketTypeProfileService ticketTypeProfileService,
            TicketApprovalService ticketApprovalService,
            TicketSummaryService ticketSummaryService
    ) {
        this.support = support;
        this.ticketRepository = ticketRepository;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.ticketTypeProfileService = ticketTypeProfileService;
        this.ticketApprovalService = ticketApprovalService;
        this.ticketSummaryService = ticketSummaryService;
    }

    /**
     * 获取详情。
     */
    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        TicketDetailDTO cached = loadCachedDetail(operator, ticketId);
        if (cached != null) {
            return cached;
        }
        return loadAndCacheDetail(operator, ticketId);
    }

    /**
     * 获取摘要。
     */
    public TicketSummaryDTO getSummary(CurrentUser operator, Long ticketId, TicketSummaryViewEnum requestedView) {
        TicketDetailDTO detail = getDetail(operator, ticketId);
        TicketSummaryViewEnum view = ticketSummaryService.resolveView(operator, detail.getTicket(), requestedView);
        return ticketSummaryService.generateForView(detail, view);
    }

    /**
     * 分页查询工单。
     */
    public PageResult<Ticket> page工单(CurrentUser operator, TicketPageQueryDTO query) {
        PageQuery pageQuery = buildPageQuery(query);
        List<Ticket> records = loadPageRecords(operator, pageQuery);
        long total = countPageRecords(operator, pageQuery);
        ticketTypeProfileService.attachProfiles(records);
        return PageResult.<Ticket>builder()
                .pageNo(pageQuery.pageNo())
                .pageSize(pageQuery.pageSize())
                .total(total)
                .records(records)
                .build();
    }

    /**
     * 加载Cached详情。
     */
    private TicketDetailDTO loadCachedDetail(CurrentUser operator, Long ticketId) {
        TicketDetailDTO cached = ticketDetailCacheService.get(ticketId);
        if (cached == null || cached.getTicket() == null) {
            return null;
        }
        support.requireVisibleFromTicket(operator, cached.getTicket());
        ensureSummaries(cached);
        return cached;
    }

    /**
     * 加载并缓存详情。
     */
    private TicketDetailDTO loadAndCacheDetail(CurrentUser operator, Long ticketId) {
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        ticketTypeProfileService.attachProfile(ticket);
        TicketDetailDTO detail = support.buildDetail(ticketId, ticket);
        detail.setApproval(ticketApprovalService.getApproval(operator, ticketId));
        ensureSummaries(detail);
        ticketDetailCacheService.put(ticketId, detail);
        return detail;
    }

    /**
     * 处理摘要列表。
     */
    private void ensureSummaries(TicketDetailDTO detail) {
        if (detail.getSummaries() == null) {
            detail.setSummaries(ticketSummaryService.generateAll(detail));
        }
    }

    /**
     * 加载分页Records。
     */
    private List<Ticket> loadPageRecords(CurrentUser operator, PageQuery pageQuery) {
        if (operator.isAdmin()) {
            return ticketRepository.pageAll(
                    pageQuery.status(),
                    pageQuery.type(),
                    pageQuery.category(),
                    pageQuery.priority(),
                    pageQuery.offset(),
                    pageQuery.pageSize()
            );
        }
        return ticketRepository.pageVisible(
                operator.getUserId(),
                pageQuery.status(),
                pageQuery.type(),
                pageQuery.category(),
                pageQuery.priority(),
                pageQuery.offset(),
                pageQuery.pageSize()
        );
    }

    /**
     * 统计分页Records。
     */
    private long countPageRecords(CurrentUser operator, PageQuery pageQuery) {
        if (operator.isAdmin()) {
            return ticketRepository.countAll(
                    pageQuery.status(),
                    pageQuery.type(),
                    pageQuery.category(),
                    pageQuery.priority()
            );
        }
        return ticketRepository.countVisible(
                operator.getUserId(),
                pageQuery.status(),
                pageQuery.type(),
                pageQuery.category(),
                pageQuery.priority()
        );
    }

    /**
     * 构建分页查询。
     */
    private PageQuery buildPageQuery(TicketPageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        return new PageQuery(
                pageNo,
                pageSize,
                (pageNo - 1) * pageSize,
                enumCode(query.getStatus()),
                enumCode(query.getType()),
                enumCode(query.getCategory()),
                enumCode(query.getPriority())
        );
    }

    /**
     * 处理编码。
     */
    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }

    private record PageQuery(
            int pageNo,
            int pageSize,
            int offset,
            String status,
            String type,
            String category,
            String priority
    ) {
    }
}
