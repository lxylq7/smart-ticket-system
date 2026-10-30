package com.example.ticket.ai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TicketClassificationEventListener {

    private final TicketClassificationService service;

    public TicketClassificationEventListener(TicketClassificationService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(TicketClassificationRequested event) {
        service.schedule(event.ticketId());
    }
}
