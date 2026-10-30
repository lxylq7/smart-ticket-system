package com.example.ticket.ticket.service;

import com.example.ticket.common.exception.ApiException;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import com.example.ticket.ticket.dto.CreateTicketMessageRequest;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.entity.TicketMessage;
import com.example.ticket.ticket.enums.MessageVisibility;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.mapper.TicketHistoryMapper;
import com.example.ticket.ticket.mapper.TicketMapper;
import com.example.ticket.ticket.mapper.TicketMessageMapper;
import com.example.ticket.ticket.vo.TicketMessageView;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketMessageService {

    private final TicketMapper ticketMapper;
    private final TicketMessageMapper messageMapper;
    private final TicketHistoryMapper historyMapper;
    private final CurrentUser currentUser;

    public TicketMessageService(TicketMapper ticketMapper, TicketMessageMapper messageMapper,
            TicketHistoryMapper historyMapper, CurrentUser currentUser) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TicketMessageView> list(long ticketId) {
        AuthenticatedUser user = currentUser.require();
        Ticket ticket = find(ticketId);
        MessageVisibility visible = readableVisibility(user, ticket);
        return messageMapper.findByTicketId(ticketId, visible).stream().map(TicketMessageView::from).toList();
    }

    @Transactional
    public TicketMessageView create(long ticketId, CreateTicketMessageRequest request) {
        AuthenticatedUser user = currentUser.require();
        Ticket ticket = find(ticketId);
        require(ticket.getStatus() != TicketStatus.CLOSED, HttpStatus.CONFLICT, "TICKET_CLOSED",
                "已关闭工单不能继续发送消息");
        authorizeCreate(user, ticket, request.visibility());

        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setSenderId(user.id());
        message.setVisibility(request.visibility());
        message.setContent(request.content().strip());
        messageMapper.insert(message);
        historyMapper.insertMessageCreated(ticketId, user.id(), request.visibility().name());
        return TicketMessageView.from(messageMapper.selectById(message.getId()));
    }

    private MessageVisibility readableVisibility(AuthenticatedUser user, Ticket ticket) {
        if (user.hasRole(UserRole.ADMIN)) {
            return null;
        }
        if (user.hasRole(UserRole.AGENT) && Objects.equals(ticket.getAssigneeId(), user.id())) {
            return null;
        }
        if (user.hasRole(UserRole.USER) && Objects.equals(ticket.getCreatorId(), user.id())) {
            return MessageVisibility.PUBLIC;
        }
        throw notFound();
    }

    private void authorizeCreate(AuthenticatedUser user, Ticket ticket, MessageVisibility visibility) {
        boolean assignedAgent = user.hasRole(UserRole.AGENT) && Objects.equals(ticket.getAssigneeId(), user.id());
        boolean creator = user.hasRole(UserRole.USER) && Objects.equals(ticket.getCreatorId(), user.id());
        if (user.hasRole(UserRole.ADMIN)) {
            return;
        }
        if (visibility == MessageVisibility.INTERNAL) {
            if (!assignedAgent) {
                if (creator) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "普通用户不能添加内部备注");
                }
                throw notFound();
            }
            return;
        }
        if (!assignedAgent && !creator) {
            throw notFound();
        }
    }

    private Ticket find(long ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw notFound();
        }
        return ticket;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在或无权访问");
    }

    private void require(boolean condition, HttpStatus status, String code, String message) {
        if (!condition) {
            throw new ApiException(status, code, message);
        }
    }
}
