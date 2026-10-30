package com.example.ticket.admin.dto;

import com.example.ticket.admin.enums.UserStatus;
import com.example.ticket.security.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Filters used by the administrator user directory. */
public class AdminUserQuery {

    @Min(1)
    @Max(1_000_000)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 20;

    @Size(max = 100)
    private String keyword;
    private UserRole role;
    private UserStatus status;

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
