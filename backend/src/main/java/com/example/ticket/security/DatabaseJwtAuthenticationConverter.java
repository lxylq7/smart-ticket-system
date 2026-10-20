package com.example.ticket.security;

import com.example.ticket.auth.mapper.UserAccountMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAccountMapper mapper;
    private final AccountIdentityService identityService;

    public DatabaseJwtAuthenticationConverter(UserAccountMapper mapper, AccountIdentityService identityService) {
        this.mapper = mapper;
        this.identityService = identityService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
            if (id <= 0) {
                throw new NumberFormatException("Invalid subject");
            }
        } catch (NumberFormatException exception) {
            throw new OAuth2AuthenticationException("invalid_token");
        }
        AuthenticatedUser user;
        try {
            user = identityService.activeIdentity(mapper.findById(id));
        } catch (DataAccessException exception) {
            throw new AuthenticationServiceException("Identity store unavailable", exception);
        }
        if (user == null) {
            throw new OAuth2AuthenticationException("invalid_token");
        }
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
    }
}
