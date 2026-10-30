package com.example.ticket.ai;

public class DisabledTicketClassifier implements TicketClassifier {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public TicketClassificationResult classify(String title, String description) {
        throw new IllegalStateException("AI classification is disabled");
    }
}
