package com.example.ticket.ticket.controller;

import com.example.ticket.common.response.ApiResponse;
import com.example.ticket.ticket.dto.AssignTicketRequest;
import com.example.ticket.ticket.dto.ChangeTicketStatusRequest;
import com.example.ticket.ticket.service.TicketWorkflowService;
import com.example.ticket.ticket.vo.TicketView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketWorkflowController {

    private final TicketWorkflowService service;

    public TicketWorkflowController(TicketWorkflowService service) { this.service = service; }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketView> assign(@PathVariable @Positive long id,
            @Valid @RequestBody AssignTicketRequest request) {
        return ApiResponse.success(service.assign(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ApiResponse<TicketView> changeStatus(@PathVariable @Positive long id,
            @Valid @RequestBody ChangeTicketStatusRequest request) {
        return ApiResponse.success(service.changeStatus(id, request));
    }
}
