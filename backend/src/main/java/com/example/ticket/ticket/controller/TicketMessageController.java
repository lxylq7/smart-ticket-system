package com.example.ticket.ticket.controller;

import com.example.ticket.common.response.ApiResponse;
import com.example.ticket.ticket.dto.CreateTicketMessageRequest;
import com.example.ticket.ticket.service.TicketMessageService;
import com.example.ticket.ticket.vo.TicketMessageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketId}/messages")
public class TicketMessageController {

    private final TicketMessageService service;

    public TicketMessageController(TicketMessageService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TicketMessageView>> list(@PathVariable @Positive long ticketId) {
        return ApiResponse.success(service.list(ticketId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TicketMessageView> create(@PathVariable @Positive long ticketId,
            @Valid @RequestBody CreateTicketMessageRequest request) {
        return ApiResponse.success(service.create(ticketId, request));
    }
}
