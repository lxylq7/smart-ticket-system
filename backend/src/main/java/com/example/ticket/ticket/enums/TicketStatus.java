package com.example.ticket.ticket.enums;

public enum TicketStatus {
    PENDING, ASSIGNED, PROCESSING, WAITING_USER, RESOLVED, CLOSED;

    /** Assignment is handled separately and CLOSED is terminal. */
    public boolean canTransitionTo(TicketStatus target) {
        return switch (this) {
            case ASSIGNED -> target == PROCESSING;
            case PROCESSING -> target == WAITING_USER || target == RESOLVED;
            case WAITING_USER -> target == PROCESSING;
            case RESOLVED -> target == PROCESSING || target == CLOSED;
            case PENDING, CLOSED -> false;
        };
    }

    public boolean canAssign() {
        return this == PENDING || this == ASSIGNED || this == PROCESSING || this == WAITING_USER;
    }
}
