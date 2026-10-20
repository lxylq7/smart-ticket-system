package com.example.ticket.ticket.dto;

import com.example.ticket.ticket.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ChangeTicketStatusRequest(@NotNull TicketStatus status,
                                        @NotNull @PositiveOrZero Integer version,
                                        @Size(max = 500) String remark) {
}
