package com.example.ticket.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket.admin.dto.AdminUserQuery;
import com.example.ticket.admin.dto.CreateAdminUserRequest;
import com.example.ticket.admin.dto.UpdateUserStatusRequest;
import com.example.ticket.admin.entity.AdminUserRecord;
import com.example.ticket.admin.enums.UserStatus;
import com.example.ticket.admin.mapper.AdminMapper;
import com.example.ticket.admin.vo.AdminStats;
import com.example.ticket.admin.vo.AdminUserView;
import com.example.ticket.common.exception.ApiException;
import com.example.ticket.common.response.PageResponse;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminService {

    private final AdminMapper mapper;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminMapper mapper, CurrentUser currentUser, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        return new AdminStats(mapper.countTickets(), mapper.countTicketsByStatus("PENDING"),
                mapper.countTicketsByStatus("ASSIGNED"), mapper.countTicketsByStatus("PROCESSING"),
                mapper.countTicketsByStatus("WAITING_USER"), mapper.countTicketsByStatus("RESOLVED"),
                mapper.countTicketsByStatus("CLOSED"), mapper.countUsers(), mapper.countActiveUsers(),
                mapper.countActiveAgents());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> users(AdminUserQuery request) {
        String keyword = StringUtils.hasText(request.getKeyword()) ? request.getKeyword().strip() : null;
        String role = request.getRole() == null ? null : request.getRole().name();
        String status = request.getStatus() == null ? null : request.getStatus().name();
        Page<AdminUserRecord> page = mapper.pageUsers(new Page<>(request.getPage(), request.getSize()),
                keyword, status, role);
        var records = page.getRecords().stream().map(user -> AdminUserView.from(user,
                mapper.findRoleCodes(user.getId()).stream().map(UserRole::valueOf).collect(Collectors.toSet()))).toList();
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }

    @Transactional
    public AdminUserView createUser(CreateAdminUserRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_ACCOUNT_FORBIDDEN", "此接口只能创建普通用户或客服账号");
        }
        String username = request.username().strip();
        if (mapper.countByUsername(username) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
        Long roleId = mapper.findRoleId(request.role().name());
        if (roleId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_UNAVAILABLE", "指定角色不可用");
        }
        AdminUserRecord user = new AdminUserRecord();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().strip());
        user.setEmail(normalize(request.email()));
        user.setPhone(normalize(request.phone()));
        if (mapper.insertUser(user) != 1 || user.getId() == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "USER_CREATE_FAILED", "创建用户失败");
        }
        mapper.insertUserRole(user.getId(), roleId);
        return AdminUserView.from(mapper.findUserById(user.getId()), Set.of(request.role()));
    }

    @Transactional
    public AdminUserView updateStatus(long id, UpdateUserStatusRequest request) {
        AuthenticatedUser operator = currentUser.require();
        AdminUserRecord target = mapper.findUserById(id);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        UserStatus desired = request.status();
        if (operator.id() == id && desired == UserStatus.DISABLED) {
            throw new ApiException(HttpStatus.CONFLICT, "SELF_DISABLE_FORBIDDEN", "不能禁用当前管理员账号");
        }
        Set<UserRole> roles = mapper.findRoleCodes(id).stream().map(UserRole::valueOf).collect(Collectors.toSet());
        if (roles.contains(UserRole.ADMIN) && desired == UserStatus.DISABLED
                && "ACTIVE".equals(target.getStatus()) && mapper.countActiveAdmins() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_FORBIDDEN", "不能禁用最后一个管理员账号");
        }
        if (target.getStatus().equals(desired.name())) {
            return AdminUserView.from(target, roles);
        }
        if (mapper.updateStatus(id, desired.name()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_STATUS_CONFLICT", "用户状态已发生变化，请刷新后重试");
        }
        AdminUserRecord updated = mapper.findUserById(id);
        return AdminUserView.from(updated, roles);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
