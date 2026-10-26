package com.example.ticket.ai;

import com.example.ticket.ticket.enums.TicketCategory;
import com.example.ticket.ticket.mapper.TicketHistoryMapper;
import com.example.ticket.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketClassificationPersistence {

    private final TicketMapper ticketMapper;
    private final TicketHistoryMapper historyMapper;

    public TicketClassificationPersistence(TicketMapper ticketMapper, TicketHistoryMapper historyMapper) {
        this.ticketMapper = ticketMapper;
        this.historyMapper = historyMapper;
    }

    @Transactional
    public boolean saveSuccess(long ticketId, int claimedVersion, TicketCategory category, String reason) {
        if (ticketMapper.saveClassificationSuccess(ticketId, claimedVersion, category, reason) != 1) {
            return false;
        }
        historyMapper.insertAiClassified(ticketId, "分类=" + category.name() + "；" + reason);
        return true;
    }

    @Transactional
    public boolean saveFailure(long ticketId, int claimedVersion, String errorMessage) {
        if (ticketMapper.saveClassificationFailure(ticketId, claimedVersion, errorMessage) != 1) {
            return false;
        }
        historyMapper.insertAiClassificationFailed(ticketId, errorMessage);
        return true;
    }
}
