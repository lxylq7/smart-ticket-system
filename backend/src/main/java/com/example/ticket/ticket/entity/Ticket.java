package com.example.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticket.ticket.enums.ClassificationStatus;
import com.example.ticket.ticket.enums.TicketCategory;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.enums.TicketStatus;
import java.time.LocalDateTime;

@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private Long creatorId;
    private Long assigneeId;
    private String title;
    private String description;
    private TicketCategory category;
    private String categoryReason;
    private ClassificationStatus aiStatus;
    private TicketPriority priority;
    private TicketStatus status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public String getCategoryReason() { return categoryReason; }
    public void setCategoryReason(String categoryReason) { this.categoryReason = categoryReason; }
    public ClassificationStatus getAiStatus() { return aiStatus; }
    public void setAiStatus(ClassificationStatus aiStatus) { this.aiStatus = aiStatus; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
