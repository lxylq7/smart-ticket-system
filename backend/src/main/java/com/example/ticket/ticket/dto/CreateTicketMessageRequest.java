package com.example.ticket.ticket.dto;

import com.example.ticket.ticket.enums.MessageVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketMessageRequest(@NotNull MessageVisibility visibility,
                                         @NotBlank @Size(max = 10000) String content) {
}
