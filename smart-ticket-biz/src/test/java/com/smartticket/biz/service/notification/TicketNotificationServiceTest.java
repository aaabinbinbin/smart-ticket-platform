package com.smartticket.biz.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.notification.TicketNotificationPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.notification.TicketNotificationRepository;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketNotification;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketNotificationServiceTest {

    @Mock
    private TicketNotificationRepository ticketNotificationRepository;

    @InjectMocks
    private TicketNotificationService service;

    @Test
    void pageMyNotificationsShouldUseCurrentUserAndUnreadFilter() {
        when(ticketNotificationRepository.pageByReceiverUserId(7L, true, 0, 5)).thenReturn(List.of(
                TicketNotification.builder().id(1L).receiverUserId(7L).readStatus(0).build()
        ));
        when(ticketNotificationRepository.countByReceiverUserId(7L, true)).thenReturn(1L);

        PageResult<TicketNotification> page = service.pageMyNotifications(currentUser(7L), TicketNotificationPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(5)
                .unreadOnly(true)
                .build());

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        verify(ticketNotificationRepository).pageByReceiverUserId(7L, true, 0, 5);
    }

    @Test
    void markReadShouldRejectNotificationOfAnotherUser() {
        when(ticketNotificationRepository.findById(100L)).thenReturn(TicketNotification.builder()
                .id(100L)
                .receiverUserId(9L)
                .readStatus(0)
                .build());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(currentUser(7L), 100L));

        assertEquals("TICKET_NOTIFICATION_NOT_FOUND", ex.getCode());
    }

    @Test
    void markReadShouldUpdateUnreadNotification() {
        TicketNotification unread = TicketNotification.builder()
                .id(101L)
                .receiverUserId(7L)
                .readStatus(0)
                .build();
        TicketNotification read = TicketNotification.builder()
                .id(101L)
                .receiverUserId(7L)
                .readStatus(1)
                .readAt(LocalDateTime.of(2026, 4, 23, 14, 0))
                .build();
        when(ticketNotificationRepository.findById(101L)).thenReturn(unread, read);
        when(ticketNotificationRepository.markRead(eq(101L), eq(7L), any(LocalDateTime.class))).thenReturn(1);

        TicketNotification notification = service.markRead(currentUser(7L), 101L);

        assertEquals(1, notification.getReadStatus());
        verify(ticketNotificationRepository).markRead(eq(101L), eq(7L), any(LocalDateTime.class));
    }

    private CurrentUser currentUser(Long userId) {
        return CurrentUser.builder()
                .userId(userId)
                .username("user" + userId)
                .roles(List.of("USER"))
                .build();
    }
}
