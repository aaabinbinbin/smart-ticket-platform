package com.smartticket.domain.enums;

import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * 工单状态转换规则表。
 *
 * <p>定义所有合法的状态转换路径及转换条件。取代手写 if/else 链。</p>
 */
public enum TicketStatusTransition {

    INSTANCE;

    private final Map<TicketStatusEnum, Map<TicketStatusEnum, TransitionRule>> transitions;

    TicketStatusTransition() {
        this.transitions = Map.of(
                TicketStatusEnum.PENDING_ASSIGN, Map.of(
                        TicketStatusEnum.PROCESSING, TransitionRule.builder()
                                .requiresAdmin(true)
                                .requiresAssignee(true)
                                .build()
                ),
                TicketStatusEnum.PROCESSING, Map.of(
                        TicketStatusEnum.RESOLVED, TransitionRule.builder()
                                .requiresResolvePermission(true)
                                .build()
                ),
                TicketStatusEnum.RESOLVED, Map.of(
                        TicketStatusEnum.CLOSED, TransitionRule.builder()
                                .requiresClosePermission(true)
                                .build()
                )
        );
    }

    /**
     * 判断从 {@code from} 到 {@code to} 的状态转换是否合法。
     */
    public boolean isValid(TicketStatusEnum from, TicketStatusEnum to) {
        return findRule(from, to).isPresent();
    }

    /**
     * 获取转换规则。
     */
    public Optional<TransitionRule> findRule(TicketStatusEnum from, TicketStatusEnum to) {
        return Optional.ofNullable(transitions.get(from))
                .flatMap(toMap -> Optional.ofNullable(toMap.get(to)));
    }

    /**
     * 获取从指定状态出发的所有可达目标状态。
     */
    public Set<TicketStatusEnum> allowedTargets(TicketStatusEnum from) {
        return transitions.getOrDefault(from, Map.of()).keySet();
    }

    /**
     * 一条状态转换的规则定义。
     */
    public static class TransitionRule {
        private final boolean requiresAdmin;
        private final boolean requiresAssignee;
        private final boolean requiresResolvePermission;
        private final boolean requiresClosePermission;

        TransitionRule(boolean requiresAdmin, boolean requiresAssignee,
                       boolean requiresResolvePermission, boolean requiresClosePermission) {
            this.requiresAdmin = requiresAdmin;
            this.requiresAssignee = requiresAssignee;
            this.requiresResolvePermission = requiresResolvePermission;
            this.requiresClosePermission = requiresClosePermission;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isRequiresAdmin() { return requiresAdmin; }
        public boolean isRequiresAssignee() { return requiresAssignee; }
        public boolean isRequiresResolvePermission() { return requiresResolvePermission; }
        public boolean isRequiresClosePermission() { return requiresClosePermission; }

        public static class Builder {
            private boolean requiresAdmin;
            private boolean requiresAssignee;
            private boolean requiresResolvePermission;
            private boolean requiresClosePermission;

            public Builder requiresAdmin(boolean v) { this.requiresAdmin = v; return this; }
            public Builder requiresAssignee(boolean v) { this.requiresAssignee = v; return this; }
            public Builder requiresResolvePermission(boolean v) { this.requiresResolvePermission = v; return this; }
            public Builder requiresClosePermission(boolean v) { this.requiresClosePermission = v; return this; }
            public TransitionRule build() { return new TransitionRule(requiresAdmin, requiresAssignee, requiresResolvePermission, requiresClosePermission); }
        }
    }
}
