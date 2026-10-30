package com.example.ticket.ai;

import com.example.ticket.ticket.enums.TicketCategory;

/** The only model-controlled fields accepted by the application. */
public record TicketClassificationResult(TicketCategory category, String reason) {

    public TicketClassificationResult normalized() {
        if (category == null) {
            throw new IllegalArgumentException("AI classification category is missing");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("AI classification reason is missing");
        }
        String normalizedReason = reason.strip();
        if (normalizedReason.length() > 500) {
            throw new IllegalArgumentException("AI classification reason is too long");
        }
        return new TicketClassificationResult(category, normalizedReason);
    }
}
