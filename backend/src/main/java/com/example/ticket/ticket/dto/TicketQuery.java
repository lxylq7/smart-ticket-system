package com.example.ticket.ticket.dto;

import com.example.ticket.ticket.enums.ClassificationStatus;
import com.example.ticket.ticket.enums.TicketCategory;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.enums.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class TicketQuery {

    @Min(1)
    @Max(1000000)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 20;
    @Size(max = 100)
    private String keyword;
    private TicketCategory category;
    private ClassificationStatus aiStatus;
    private TicketPriority priority;
    private TicketStatus status;

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public ClassificationStatus getAiStatus() { return aiStatus; }
    public void setAiStatus(ClassificationStatus aiStatus) { this.aiStatus = aiStatus; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
}
