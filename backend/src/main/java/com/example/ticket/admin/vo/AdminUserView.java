package com.example.ticket.admin.vo;

import com.example.ticket.admin.entity.AdminUserRecord;
import com.example.ticket.admin.enums.UserStatus;
import com.example.ticket.security.UserRole;
import java.time.LocalDateTime;
import java.util.Set;

public record AdminUserView(Long id, String username, String displayName, String email, String phone,
                            UserStatus status, Set<UserRole> roles, LocalDateTime lastLoginAt,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AdminUserView from(AdminUserRecord user, Set<UserRole> roles) {
        return new AdminUserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getPhone(), UserStatus.valueOf(user.getStatus()), Set.copyOf(roles),
                user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
