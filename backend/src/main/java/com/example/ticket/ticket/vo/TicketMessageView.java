package com.example.ticket.ticket.vo;

import com.example.ticket.ticket.entity.TicketMessage;
import com.example.ticket.ticket.enums.MessageVisibility;
import java.time.LocalDateTime;

public record TicketMessageView(Long id, Long ticketId, Long senderId, MessageVisibility visibility,
                                String content, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static TicketMessageView from(TicketMessage message) {
        return new TicketMessageView(message.getId(), message.getTicketId(), message.getSenderId(),
                message.getVisibility(), message.getContent(), message.getCreatedAt(), message.getUpdatedAt());
    }
}
