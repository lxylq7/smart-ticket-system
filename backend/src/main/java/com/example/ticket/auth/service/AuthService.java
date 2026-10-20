package com.example.ticket.auth.service;

import com.example.ticket.auth.dto.LoginRequest;
import com.example.ticket.auth.mapper.UserAccountMapper;
import com.example.ticket.auth.model.UserAccount;
import com.example.ticket.auth.vo.LoginResponse;
import com.example.ticket.common.exception.ApiException;
import com.example.ticket.config.SecurityConfig;
import com.example.ticket.security.AccountIdentityService;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountIdentityService identityService;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final String dummyPasswordHash;

    public AuthService(UserAccountMapper mapper, PasswordEncoder passwordEncoder,
            AccountIdentityService identityService, JwtEncoder jwtEncoder, JwtProperties properties) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.identityService = identityService;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount account = mapper.findByUsername(request.username());
        String hash = account == null ? dummyPasswordHash : account.passwordHash();
        boolean passwordMatches = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(request.password(), hash);
        AuthenticatedUser user = passwordMatches ? identityService.activeIdentity(account) : null;
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误，或账号不可用");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER).subject(Long.toString(user.id()))
                .issuedAt(now).expiresAt(now.plusSeconds(properties.expirationSeconds()))
                .id(UUID.randomUUID().toString()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", properties.expirationSeconds(),
                new LoginResponse.UserInfo(user.id(), user.username(), user.roles()));
    }
}
