package com.smartticket.biz.service.type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.biz.repository.type.TicketTypeProfileRepository;
import com.smartticket.biz.service.ticket.TicketServiceSupport;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketTypeProfile;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketTypeProfileService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final TicketServiceSupport support;
    private final TicketTypeProfileRepository ticketTypeProfileRepository;
    private final ObjectMapper objectMapper;

    public TicketTypeProfileService(
            TicketServiceSupport support,
            TicketTypeProfileRepository ticketTypeProfileRepository,
            ObjectMapper objectMapper
    ) {
        this.support = support;
        this.ticketTypeProfileRepository = ticketTypeProfileRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveOrUpdate(Long ticketId, TicketTypeEnum type, Map<String, Object> typeProfile) {
        Map<String, Object> normalized = normalize(typeProfile);
        validate(type, normalized);
        String profileSchema = type == null ? null : type.getCode();
        String profileData = writeJson(normalized);
        TicketTypeProfile existing = ticketTypeProfileRepository.findByTicketId(ticketId);
        if (existing == null) {
            ticketTypeProfileRepository.insert(TicketTypeProfile.builder()
                    .ticketId(ticketId)
                    .profileSchema(profileSchema)
                    .profileData(profileData)
                    .build());
            return;
        }
        support.requireUpdated(ticketTypeProfileRepository.updateByTicketId(ticketId, profileSchema, profileData));
    }

    public Map<String, Object> getProfile(Long ticketId) {
        TicketTypeProfile profile = ticketTypeProfileRepository.findByTicketId(ticketId);
        if (profile == null || profile.getProfileData() == null || profile.getProfileData().isBlank()) {
            return Collections.emptyMap();
        }
        return readJson(profile.getProfileData());
    }

    public void attachProfile(Ticket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        ticket.setTypeProfile(getProfile(ticket.getId()));
    }

    public void attachProfiles(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
        Map<Long, Map<String, Object>> profileMap = ticketTypeProfileRepository.findByTicketIds(ticketIds)
                .stream()
                .collect(Collectors.toMap(TicketTypeProfile::getTicketId, profile -> readJson(profile.getProfileData())));
        for (Ticket ticket : tickets) {
            ticket.setTypeProfile(profileMap.getOrDefault(ticket.getId(), Collections.emptyMap()));
        }
    }

    public void validate(TicketTypeEnum type, Map<String, Object> typeProfile) {
        Map<String, Object> profile = normalize(typeProfile);
        switch (type) {
            case INCIDENT -> {
                requireText(profile, "symptom", "故障现象");
                requireText(profile, "impactScope", "影响范围");
            }
            case ACCESS_REQUEST -> {
                requireText(profile, "accountId", "账号标识");
                requireText(profile, "targetResource", "目标资源");
                requireText(profile, "requestedRole", "申请角色");
                requireText(profile, "justification", "申请原因");
            }
            case ENVIRONMENT_REQUEST -> {
                requireText(profile, "environmentName", "环境名称");
                requireText(profile, "resourceSpec", "资源规格");
                requireText(profile, "purpose", "用途说明");
            }
            case CONSULTATION -> {
                requireText(profile, "questionTopic", "咨询主题");
                requireText(profile, "expectedOutcome", "期望结果");
            }
            case CHANGE_REQUEST -> {
                requireText(profile, "changeTarget", "变更对象");
                requireText(profile, "changeWindow", "变更窗口");
                requireText(profile, "rollbackPlan", "回滚方案");
                requireText(profile, "impactScope", "影响范围");
            }
            default -> {
            }
        }
    }

    private Map<String, Object> normalize(Map<String, Object> typeProfile) {
        if (typeProfile == null || typeProfile.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(typeProfile);
    }

    private void requireText(Map<String, Object> profile, String field, String label) {
        Object value = profile.get(field);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE_REQUIREMENT, label + "不能为空");
        }
    }

    private String writeJson(Map<String, Object> profile) {
        try {
            return objectMapper.writeValueAsString(profile == null ? Collections.emptyMap() : profile);
        } catch (Exception ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE_REQUIREMENT, "类型资料格式不合法");
        }
    }

    private Map<String, Object> readJson(String text) {
        try {
            if (text == null || text.isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(text, MAP_TYPE);
        } catch (Exception ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE_REQUIREMENT, "类型资料解析失败");
        }
    }
}

