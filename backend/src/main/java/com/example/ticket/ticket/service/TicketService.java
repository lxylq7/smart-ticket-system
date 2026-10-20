package com.example.ticket.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket.common.exception.ApiException;
import com.example.ticket.common.response.PageResponse;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import com.example.ticket.ticket.dto.CreateTicketRequest;
import com.example.ticket.ticket.dto.TicketQuery;
import com.example.ticket.ticket.dto.UpdateTicketRequest;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.ClassificationStatus;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.mapper.TicketHistoryMapper;
import com.example.ticket.ticket.mapper.TicketMapper;
import com.example.ticket.ticket.vo.TicketView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TicketService {

    private final TicketMapper mapper;
    private final TicketHistoryMapper historyMapper;
    private final CurrentUser currentUser;

    public TicketService(TicketMapper mapper, TicketHistoryMapper historyMapper, CurrentUser currentUser) {
        this.mapper = mapper;
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
    }

    @Transactional
    public TicketView create(CreateTicketRequest request) {
        AuthenticatedUser user = requireUserRole();
        Ticket ticket = new Ticket();
        // 32 hexadecimal characters fit the existing VARCHAR(32) unique key.
        ticket.setTicketNo(UUID.randomUUID().toString().replace("-", ""));
        ticket.setCreatorId(user.id());
        ticket.setTitle(request.title().strip());
        ticket.setDescription(request.description().strip());
        ticket.setAiStatus(ClassificationStatus.PENDING);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setVersion(0);
        mapper.insert(ticket);
        historyMapper.insertCreated(ticket.getId(), user.id());
        return TicketView.from(mapper.selectById(ticket.getId()));
    }

    @Transactional(readOnly = true)
    public TicketView get(long id) {
        return TicketView.from(findVisible(id, currentUser.require()));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketView> page(TicketQuery request, boolean mine) {
        AuthenticatedUser user = mine ? requireUserRole() : currentUser.require();
        LambdaQueryWrapper<Ticket> query = visibleScope(user);
        if (mine) {
            query.eq(Ticket::getCreatorId, user.id());
        }
        query.eq(request.getStatus() != null, Ticket::getStatus, request.getStatus())
                .eq(request.getPriority() != null, Ticket::getPriority, request.getPriority())
                .eq(request.getCategory() != null, Ticket::getCategory, request.getCategory())
                .eq(request.getAiStatus() != null, Ticket::getAiStatus, request.getAiStatus());
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().strip();
            query.and(filter -> filter.like(Ticket::getTitle, keyword).or().eq(Ticket::getTicketNo, keyword));
        }
        query.orderByDesc(Ticket::getCreatedAt).orderByDesc(Ticket::getId);
        Page<Ticket> page = mapper.selectPage(new Page<>(request.getPage(), request.getSize()), query);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(),
                page.getRecords().stream().map(TicketView::from).toList());
    }

    @Transactional
    public TicketView update(long id, UpdateTicketRequest request) {
        AuthenticatedUser user = requireUserRole();
        Ticket ticket = mapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getId, id).eq(Ticket::getCreatorId, user.id()));
        if (ticket == null) {
            throw notFound();
        }
        if (ticket.getStatus() != TicketStatus.PENDING || ticket.getAssigneeId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_NOT_EDITABLE", "工单已分配或开始处理，不能修改标题和描述");
        }
        UpdateTicketRequest normalized = new UpdateTicketRequest(
                request.title().strip(), request.description().strip(), request.version());
        if (mapper.updateEditable(id, user.id(), normalized) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_CONFLICT", "工单已发生变化，请刷新后重试");
        }
        historyMapper.insertUpdated(id, user.id());
        return TicketView.from(mapper.selectById(id));
    }

    private Ticket findVisible(long id, AuthenticatedUser user) {
        Ticket ticket = mapper.selectOne(visibleScope(user).eq(Ticket::getId, id));
        if (ticket == null) {
            throw notFound();
        }
        return ticket;
    }

    private LambdaQueryWrapper<Ticket> visibleScope(AuthenticatedUser user) {
        LambdaQueryWrapper<Ticket> query = Wrappers.lambdaQuery();
        if (user.hasRole(UserRole.ADMIN)) {
            return query;
        }
        if (!user.hasRole(UserRole.USER) && !user.hasRole(UserRole.AGENT)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问工单");
        }
        return query.and(scope -> {
            if (user.hasRole(UserRole.USER)) {
                scope.eq(Ticket::getCreatorId, user.id());
            }
            if (user.hasRole(UserRole.AGENT)) {
                if (user.hasRole(UserRole.USER)) {
                    scope.or();
                }
                scope.eq(Ticket::getAssigneeId, user.id());
            }
        });
    }

    private AuthenticatedUser requireUserRole() {
        AuthenticatedUser user = currentUser.require();
        if (!user.hasRole(UserRole.USER)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "此操作需要普通用户角色");
        }
        return user;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在或无权访问");
    }
}
