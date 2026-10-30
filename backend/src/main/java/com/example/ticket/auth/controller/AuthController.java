package com.example.ticket.auth.controller;

import com.example.ticket.auth.dto.LoginRequest;
import com.example.ticket.auth.service.AuthService;
import com.example.ticket.auth.vo.LoginResponse;
import com.example.ticket.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(service.login(request));
    }
}
