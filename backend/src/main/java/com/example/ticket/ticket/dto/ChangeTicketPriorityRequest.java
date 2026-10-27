package com.example.ticket.ticket.dto;

import com.example.ticket.ticket.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ChangeTicketPriorityRequest(@NotNull TicketPriority priority,
                                          @NotNull @PositiveOrZero Integer version,
                                          @Size(max = 500) String remark) {
}
