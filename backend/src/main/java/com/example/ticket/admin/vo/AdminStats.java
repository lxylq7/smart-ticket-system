package com.example.ticket.admin.vo;

public record AdminStats(long totalTickets, long pendingTickets, long assignedTickets,
                         long processingTickets, long waitingUserTickets, long resolvedTickets,
                         long closedTickets, long totalUsers, long activeUsers, long activeAgents) {
}
