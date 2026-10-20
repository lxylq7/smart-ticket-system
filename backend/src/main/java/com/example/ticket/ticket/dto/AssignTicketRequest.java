package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssignTicketRequest(@NotNull @Positive Long assigneeId,
                                  @NotNull @PositiveOrZero Integer version,
                                  @Size(max = 500) String remark) {
}
