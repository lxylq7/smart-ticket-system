package com.example.ticket.ticket.vo;

import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.ClassificationStatus;
import com.example.ticket.ticket.enums.TicketCategory;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.enums.TicketStatus;
import java.time.LocalDateTime;

public record TicketView(Long id, String ticketNo, String title, String description,
                         TicketCategory category, String categoryReason, ClassificationStatus aiStatus,
                         TicketPriority priority, TicketStatus status, Long creatorId, Long assigneeId,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Integer version) {

    public static TicketView from(Ticket ticket) {
        return new TicketView(ticket.getId(), ticket.getTicketNo(), ticket.getTitle(), ticket.getDescription(),
                ticket.getCategory(), ticket.getCategoryReason(), ticket.getAiStatus(), ticket.getPriority(),
                ticket.getStatus(), ticket.getCreatorId(), ticket.getAssigneeId(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getVersion());
    }
}
