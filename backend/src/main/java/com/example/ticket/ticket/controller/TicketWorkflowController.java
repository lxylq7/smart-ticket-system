package com.example.ticket.ticket.controller;

import com.example.ticket.common.response.ApiResponse;
import com.example.ticket.ticket.dto.AssignTicketRequest;
import com.example.ticket.ticket.dto.ChangeTicketStatusRequest;
import com.example.ticket.ticket.dto.ChangeTicketPriorityRequest;
import com.example.ticket.ticket.service.TicketWorkflowService;
import com.example.ticket.ai.TicketClassificationService;
import com.example.ticket.ticket.vo.TicketView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketWorkflowController {

    private final TicketWorkflowService service;
    private final TicketClassificationService classificationService;

    public TicketWorkflowController(TicketWorkflowService service, TicketClassificationService classificationService) {
        this.service = service;
        this.classificationService = classificationService;
    }

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

    @PutMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ApiResponse<TicketView> changePriority(@PathVariable @Positive long id,
            @Valid @RequestBody ChangeTicketPriorityRequest request) {
        return ApiResponse.success(service.changePriority(id, request));
    }

    @PostMapping("/{id}/classification/retry")
    public ApiResponse<TicketView> retryClassification(@PathVariable @Positive long id) {
        return ApiResponse.success(classificationService.retry(id));
    }
}
