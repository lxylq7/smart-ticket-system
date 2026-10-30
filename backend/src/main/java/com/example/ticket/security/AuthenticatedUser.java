package com.example.ticket.security;

import java.util.Set;

public record AuthenticatedUser(long id, String username, Set<UserRole> roles) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }
}
