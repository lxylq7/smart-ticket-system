package com.example.ticket.ticket.controller;

import com.example.ticket.common.response.ApiResponse;
import com.example.ticket.common.response.PageResponse;
import com.example.ticket.ticket.dto.CreateTicketRequest;
import com.example.ticket.ticket.dto.TicketQuery;
import com.example.ticket.ticket.dto.UpdateTicketRequest;
import com.example.ticket.ticket.service.TicketService;
import com.example.ticket.ticket.vo.TicketView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<TicketView> create(@Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketView> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<TicketView>> page(@Valid @ModelAttribute TicketQuery query) {
        return ApiResponse.success(service.page(query, false));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<TicketView>> mine(@Valid @ModelAttribute TicketQuery query) {
        return ApiResponse.success(service.page(query, true));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<TicketView> update(@PathVariable @Positive long id,
            @Valid @RequestBody UpdateTicketRequest request) {
        return ApiResponse.success(service.update(id, request));
    }
}
