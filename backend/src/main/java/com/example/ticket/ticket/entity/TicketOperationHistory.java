package com.example.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.enums.TicketStatus;
import java.time.LocalDateTime;

@TableName("ticket_operation_history")
public class TicketOperationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private Long operatorId;
    private String operationType;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private Long fromAssigneeId;
    private Long toAssigneeId;
    private TicketPriority fromPriority;
    private TicketPriority toPriority;
    private String remark;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public TicketStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(TicketStatus fromStatus) { this.fromStatus = fromStatus; }
    public TicketStatus getToStatus() { return toStatus; }
    public void setToStatus(TicketStatus toStatus) { this.toStatus = toStatus; }
    public Long getFromAssigneeId() { return fromAssigneeId; }
    public void setFromAssigneeId(Long fromAssigneeId) { this.fromAssigneeId = fromAssigneeId; }
    public Long getToAssigneeId() { return toAssigneeId; }
    public void setToAssigneeId(Long toAssigneeId) { this.toAssigneeId = toAssigneeId; }
    public TicketPriority getFromPriority() { return fromPriority; }
    public void setFromPriority(TicketPriority fromPriority) { this.fromPriority = fromPriority; }
    public TicketPriority getToPriority() { return toPriority; }
    public void setToPriority(TicketPriority toPriority) { this.toPriority = toPriority; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
