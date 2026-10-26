package com.example.ticket.ai;

public interface TicketClassifier {

    /** Returns false when AI is intentionally disabled; this is not a classification failure. */
    default boolean available() {
        return true;
    }

    TicketClassificationResult classify(String title, String description) throws Exception;
}
