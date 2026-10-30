package com.example.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket.ticket.entity.TicketOperationHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TicketOperationHistoryMapper extends BaseMapper<TicketOperationHistory> {

    @Select("""
            SELECT id, ticket_id, operator_id, operation_type, from_status, to_status,
                   from_assignee_id, to_assignee_id, from_priority, to_priority, remark, created_at
            FROM ticket_operation_history
            WHERE ticket_id = #{ticketId}
            ORDER BY created_at DESC, id DESC
            """)
    Page<TicketOperationHistory> pageByTicket(Page<TicketOperationHistory> page,
                                               @Param("ticketId") long ticketId);
}
