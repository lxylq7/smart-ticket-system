package com.example.ticket.auth.vo;

import com.example.ticket.security.UserRole;
import java.util.Set;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {

    public record UserInfo(long id, String username, Set<UserRole> roles) {
    }
}
