package com.example.ticket.ticket.vo;

import com.example.ticket.ticket.entity.TicketOperationHistory;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.enums.TicketStatus;
import java.time.LocalDateTime;

public record TicketOperationHistoryView(Long id, Long ticketId, Long operatorId, String operationType,
                                         TicketStatus fromStatus, TicketStatus toStatus,
                                         Long fromAssigneeId, Long toAssigneeId,
                                         TicketPriority fromPriority, TicketPriority toPriority,
                                         String remark, LocalDateTime createdAt) {

    public static TicketOperationHistoryView from(TicketOperationHistory history) {
        return new TicketOperationHistoryView(history.getId(), history.getTicketId(), history.getOperatorId(),
                history.getOperationType(), history.getFromStatus(), history.getToStatus(),
                history.getFromAssigneeId(), history.getToAssigneeId(), history.getFromPriority(),
                history.getToPriority(), history.getRemark(), history.getCreatedAt());
    }
}
