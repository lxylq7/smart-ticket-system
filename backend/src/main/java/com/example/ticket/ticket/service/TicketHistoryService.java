package com.example.ticket.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket.common.exception.ApiException;
import com.example.ticket.common.response.PageResponse;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import com.example.ticket.ticket.dto.TicketHistoryQuery;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.entity.TicketOperationHistory;
import com.example.ticket.ticket.mapper.TicketMapper;
import com.example.ticket.ticket.mapper.TicketOperationHistoryMapper;
import com.example.ticket.ticket.vo.TicketOperationHistoryView;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketHistoryService {

    private final TicketMapper ticketMapper;
    private final TicketOperationHistoryMapper historyMapper;
    private final CurrentUser currentUser;

    public TicketHistoryService(TicketMapper ticketMapper, TicketOperationHistoryMapper historyMapper,
                                CurrentUser currentUser) {
        this.ticketMapper = ticketMapper;
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketOperationHistoryView> page(long ticketId, TicketHistoryQuery request) {
        AuthenticatedUser user = currentUser.require();
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || !canView(user, ticket)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在或无权访问");
        }
        Page<TicketOperationHistory> page = historyMapper.pageByTicket(
                new Page<>(request.getPage(), request.getSize()), ticketId);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(),
                page.getRecords().stream().map(TicketOperationHistoryView::from).toList());
    }

    private boolean canView(AuthenticatedUser user, Ticket ticket) {
        if (user.hasRole(UserRole.ADMIN)) return true;
        if (user.hasRole(UserRole.USER) && Objects.equals(ticket.getCreatorId(), user.id())) return true;
        return user.hasRole(UserRole.AGENT) && Objects.equals(ticket.getAssigneeId(), user.id());
    }
}
