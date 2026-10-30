package com.example.ticket.security;

import com.example.ticket.auth.mapper.UserAccountMapper;
import com.example.ticket.auth.model.UserAccount;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AccountIdentityService {

    private final UserAccountMapper mapper;

    public AccountIdentityService(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    public AuthenticatedUser activeIdentity(UserAccount account) {
        if (account == null || !"ACTIVE".equals(account.status())) {
            return null;
        }
        Set<UserRole> roles = mapper.findRoleCodes(account.id()).stream()
                .filter(code -> Set.of("USER", "AGENT", "ADMIN").contains(code))
                .map(UserRole::valueOf).collect(Collectors.toUnmodifiableSet());
        return roles.isEmpty() ? null : new AuthenticatedUser(account.id(), account.username(), roles);
    }
}
