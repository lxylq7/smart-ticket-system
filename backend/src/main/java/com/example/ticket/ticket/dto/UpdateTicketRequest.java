package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Both editable fields must be provided; version is taken from the latest GET response. */
public record UpdateTicketRequest(@NotBlank @Size(max = 200) String title,
                                  @NotBlank @Size(max = 20000) String description,
                                  @NotNull @PositiveOrZero Integer version) {
}
