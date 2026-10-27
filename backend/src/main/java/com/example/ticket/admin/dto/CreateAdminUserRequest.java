package com.example.ticket.admin.dto;

import com.example.ticket.security.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Account fields accepted by the administrator. ADMIN accounts are never created here. */
public record CreateAdminUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 64) String displayName,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[0-9+() -]{5,32}$", message = "手机号格式不正确") @Size(max = 32) String phone,
        @NotNull UserRole role) {
}
