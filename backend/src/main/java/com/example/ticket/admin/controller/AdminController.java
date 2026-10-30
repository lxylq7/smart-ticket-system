package com.example.ticket.admin.controller;

import com.example.ticket.admin.dto.AdminUserQuery;
import com.example.ticket.admin.dto.CreateAdminUserRequest;
import com.example.ticket.admin.dto.UpdateUserStatusRequest;
import com.example.ticket.admin.service.AdminService;
import com.example.ticket.admin.vo.AdminStats;
import com.example.ticket.admin.vo.AdminUserView;
import com.example.ticket.common.response.ApiResponse;
import com.example.ticket.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/stats")
    public ApiResponse<AdminStats> stats() { return ApiResponse.success(service.stats()); }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserView>> users(@Valid @ModelAttribute AdminUserQuery query) {
        return ApiResponse.success(service.users(query));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminUserView> createUser(@Valid @RequestBody CreateAdminUserRequest request) {
        return ApiResponse.success(service.createUser(request));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<AdminUserView> updateStatus(@PathVariable @Positive long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(service.updateStatus(id, request));
    }
}
