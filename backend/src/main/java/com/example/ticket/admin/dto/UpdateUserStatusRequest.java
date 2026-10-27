package com.example.ticket.admin.dto;

import com.example.ticket.admin.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
