package com.smartticket.api.support;

import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 工单接口层枚举解析器。
 *
 * <p>Controller 只保留 HTTP 协议适配职责，字符串到业务枚举的转换集中放在这里，
 * 便于统一错误码和减少控制器体积。</p>
 */
@Component
public class TicketRequestParser {

    public TicketStatusEnum parseStatus(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketStatusEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, code);
        }
    }

    public TicketTypeEnum parseType(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketTypeEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE, code);
        }
    }

    public TicketCategoryEnum parseCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketCategoryEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_CATEGORY, code);
        }
    }

    public TicketPriorityEnum parsePriority(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketPriorityEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_PRIORITY, code);
        }
    }

    public TicketSummaryViewEnum parseSummaryView(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketSummaryViewEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_SUMMARY_VIEW, code);
        }
    }
}
