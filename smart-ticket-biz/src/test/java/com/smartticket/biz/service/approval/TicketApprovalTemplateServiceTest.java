package com.smartticket.biz.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.approval.TicketApprovalTemplateCommandDTO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplateStepCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.approval.TicketApprovalTemplateRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketApprovalTemplateServiceTest {

    @Mock
    private TicketApprovalTemplateRepository repository;

    @Mock
    private TicketPermissionService permissionService;

    @Mock
    private TicketUserDirectoryService ticketUserDirectoryService;

    @InjectMocks
    private TicketApprovalTemplateService service;

    @Test
    void createShouldSortStepsAndPersistTemplate() {
        TicketApprovalTemplateCommandDTO command = TicketApprovalTemplateCommandDTO.builder()
                .templateName("Access Approval")
                .ticketType(TicketTypeEnum.ACCESS_REQUEST)
                .description("approval flow")
                .enabled(true)
                .steps(List.of(
                        TicketApprovalTemplateStepCommandDTO.builder().stepOrder(2).stepName("manager").approverId(102L).build(),
                        TicketApprovalTemplateStepCommandDTO.builder().stepOrder(1).stepName("owner").approverId(101L).build()
                ))
                .build();
        doAnswer(invocation -> {
            TicketApprovalTemplate template = invocation.getArgument(0);
            template.setId(88L);
            return 1;
        }).when(repository).insert(any(TicketApprovalTemplate.class));
        TicketApprovalTemplate persisted = TicketApprovalTemplate.builder()
                .id(88L)
                .templateName("Access Approval")
                .ticketType(TicketTypeEnum.ACCESS_REQUEST)
                .enabled(1)
                .steps(List.of(
                        TicketApprovalTemplateStep.builder().templateId(88L).stepOrder(1).stepName("owner").approverId(101L).build(),
                        TicketApprovalTemplateStep.builder().templateId(88L).stepOrder(2).stepName("manager").approverId(102L).build()
                ))
                .build();
        when(repository.findById(88L)).thenReturn(persisted);

        TicketApprovalTemplate result = service.create(adminUser(), command);

        assertEquals(88L, result.getId());
        verify(permissionService).requireAdmin(adminUser());
        verify(ticketUserDirectoryService).requireApproverUser(101L);
        verify(ticketUserDirectoryService).requireApproverUser(102L);
        ArgumentCaptor<List<TicketApprovalTemplateStep>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceSteps(eq(88L), captor.capture());
        List<TicketApprovalTemplateStep> steps = captor.getValue();
        assertEquals(List.of(1, 2), steps.stream().map(TicketApprovalTemplateStep::getStepOrder).toList());
        assertEquals(List.of(101L, 102L), steps.stream().map(TicketApprovalTemplateStep::getApproverId).toList());
    }

    @Test
    void createShouldRejectNonContinuousStepOrder() {
        TicketApprovalTemplateCommandDTO command = TicketApprovalTemplateCommandDTO.builder()
                .templateName("Change Approval")
                .ticketType(TicketTypeEnum.CHANGE_REQUEST)
                .enabled(true)
                .steps(List.of(
                        TicketApprovalTemplateStepCommandDTO.builder().stepOrder(1).stepName("owner").approverId(101L).build(),
                        TicketApprovalTemplateStepCommandDTO.builder().stepOrder(3).stepName("manager").approverId(102L).build()
                ))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(adminUser(), command));

        assertEquals("INVALID_TICKET_APPROVAL", ex.getCode());
    }

    private CurrentUser adminUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("admin1")
                .roles(List.of("ADMIN"))
                .build();
    }
}
