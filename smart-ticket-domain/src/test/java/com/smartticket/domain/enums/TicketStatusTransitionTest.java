package com.smartticket.domain.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TicketStatusTransitionTest {

    @Test
    void shouldAllowValidTransitionFromPendingAssignToProcessing() {
        assertTrue(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.PROCESSING));
    }

    @Test
    void shouldAllowValidTransitionFromProcessingToResolved() {
        assertTrue(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.PROCESSING, TicketStatusEnum.RESOLVED));
    }

    @Test
    void shouldAllowValidTransitionFromResolvedToClosed() {
        assertTrue(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.RESOLVED, TicketStatusEnum.CLOSED));
    }

    @Test
    void shouldRejectInvalidTransitionFromPendingAssignToClosed() {
        assertFalse(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.CLOSED));
    }

    @Test
    void shouldRejectInvalidTransitionFromClosedToAny() {
        assertFalse(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.CLOSED, TicketStatusEnum.PENDING_ASSIGN));
        assertFalse(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.CLOSED, TicketStatusEnum.PROCESSING));
        assertFalse(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.CLOSED, TicketStatusEnum.RESOLVED));
    }

    @Test
    void shouldRejectSelfTransition() {
        assertFalse(TicketStatusTransition.INSTANCE.isValid(
                TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.PENDING_ASSIGN));
    }

    @Test
    void shouldReturnCorrectTransitionRules() {
        var rule = TicketStatusTransition.INSTANCE.findRule(
                TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.PROCESSING);
        assertTrue(rule.isPresent());
        assertTrue(rule.get().isRequiresAdmin());
        assertTrue(rule.get().isRequiresAssignee());
    }

    @Test
    void allowedTargetsShouldReturnCorrectSet() {
        Set<TicketStatusEnum> targets = TicketStatusTransition.INSTANCE.allowedTargets(
                TicketStatusEnum.PROCESSING);
        assertEquals(Set.of(TicketStatusEnum.RESOLVED), targets);
    }

    @Test
    void allowedTargetsForClosedShouldBeEmpty() {
        Set<TicketStatusEnum> targets = TicketStatusTransition.INSTANCE.allowedTargets(
                TicketStatusEnum.CLOSED);
        assertTrue(targets.isEmpty());
    }
}
