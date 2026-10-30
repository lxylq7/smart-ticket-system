package com.example.ticket.ticket.service;

import com.example.ticket.common.exception.ApiException;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import com.example.ticket.ticket.dto.AssignTicketRequest;
import com.example.ticket.ticket.dto.ChangeTicketStatusRequest;
import com.example.ticket.ticket.dto.ChangeTicketPriorityRequest;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.mapper.TicketHistoryMapper;
import com.example.ticket.ticket.mapper.TicketMapper;
import com.example.ticket.ticket.vo.TicketView;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketWorkflowService {

    private final TicketMapper mapper;
    private final TicketHistoryMapper historyMapper;
    private final CurrentUser currentUser;

    public TicketWorkflowService(TicketMapper mapper, TicketHistoryMapper historyMapper, CurrentUser currentUser) {
        this.mapper = mapper;
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
    }

    @Transactional
    public TicketView assign(long id, AssignTicketRequest request) {
        AuthenticatedUser user = currentUser.require();
        require(user.hasRole(UserRole.ADMIN), HttpStatus.FORBIDDEN, "FORBIDDEN", "只有管理员可以分配工单");
        Ticket ticket = find(id);
        checkVersion(ticket, request.version());
        require(ticket.getStatus().canAssign(), HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "已解决或关闭的工单不能分配");
        require(!Objects.equals(ticket.getAssigneeId(), request.assigneeId()), HttpStatus.CONFLICT,
                "TICKET_ALREADY_ASSIGNED", "工单已经分配给该客服");
        require(mapper.countActiveAgent(request.assigneeId()) == 1, HttpStatus.BAD_REQUEST,
                "INVALID_ASSIGNEE", "处理人必须是启用且具有有效客服角色的账号");
        if (mapper.assign(ticket, request.assigneeId(), request.version()) != 1) {
            throw conflict();
        }
        // ASSIGN records both the assignment and the corresponding status change in one history row.
        historyMapper.insertAssigned(ticket, user.id(), request.assigneeId(), normalize(request.remark()));
        return TicketView.from(mapper.selectById(id));
    }

    @Transactional
    public TicketView changeStatus(long id, ChangeTicketStatusRequest request) {
        AuthenticatedUser user = currentUser.require();
        boolean admin = user.hasRole(UserRole.ADMIN);
        require(admin || user.hasRole(UserRole.AGENT), HttpStatus.FORBIDDEN, "FORBIDDEN", "用户不能直接修改工单状态");
        Ticket ticket = find(id);
        // A USER+AGENT account still cannot process a ticket merely because it created it.
        require(admin || Objects.equals(ticket.getAssigneeId(), user.id()), HttpStatus.NOT_FOUND,
                "TICKET_NOT_FOUND", "工单不存在或无权访问");
        require(admin || request.status() != TicketStatus.CLOSED, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "只有管理员可以关闭已解决的工单");
        checkVersion(ticket, request.version());
        require(ticket.getStatus().canTransitionTo(request.status()), HttpStatus.CONFLICT,
                "INVALID_STATUS_TRANSITION", "不允许此状态转换，请检查当前状态");
        require(ticket.getAssigneeId() != null, HttpStatus.CONFLICT,
                "TICKET_NOT_ASSIGNED", "工单尚无处理人，请先分配工单");
        if (mapper.changeStatus(ticket, request.status(), request.version(), user.id(), admin) != 1) {
            throw conflict();
        }
        historyMapper.insertStatusChanged(ticket, user.id(), request.status(), normalize(request.remark()));
        return TicketView.from(mapper.selectById(id));
    }

    @Transactional
    public TicketView changePriority(long id, ChangeTicketPriorityRequest request) {
        AuthenticatedUser user = currentUser.require();
        boolean admin = user.hasRole(UserRole.ADMIN);
        require(admin || user.hasRole(UserRole.AGENT), HttpStatus.FORBIDDEN, "FORBIDDEN", "用户不能修改工单优先级");
        Ticket ticket = find(id);
        require(admin || Objects.equals(ticket.getAssigneeId(), user.id()), HttpStatus.NOT_FOUND,
                "TICKET_NOT_FOUND", "工单不存在或无权访问");
        require(ticket.getStatus() != TicketStatus.CLOSED, HttpStatus.CONFLICT,
                "TICKET_CLOSED", "已关闭工单不能修改优先级");
        require(ticket.getPriority() != request.priority(), HttpStatus.CONFLICT,
                "TICKET_PRIORITY_UNCHANGED", "工单已经是该优先级");
        checkVersion(ticket, request.version());
        if (mapper.changePriority(ticket, request.priority(), request.version(), user.id(), admin) != 1) {
            throw conflict();
        }
        historyMapper.insertPriorityChanged(ticket, user.id(), request.priority(), normalize(request.remark()));
        return TicketView.from(mapper.selectById(id));
    }

    private Ticket find(long id) {
        Ticket ticket = mapper.selectById(id);
        require(ticket != null, HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在或无权访问");
        return ticket;
    }

    private void checkVersion(Ticket ticket, int version) {
        if (ticket.getVersion() != version) throw conflict();
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "TICKET_CONFLICT", "工单已发生变化，请刷新后重试");
    }

    private void require(boolean condition, HttpStatus status, String code, String message) {
        if (!condition) throw new ApiException(status, code, message);
    }

    private String normalize(String remark) {
        return remark == null || remark.isBlank() ? null : remark.strip();
    }
}
