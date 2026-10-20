package com.example.ticket.ticket.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface TicketHistoryMapper {

    @Insert("""
            INSERT INTO ticket_operation_history
                (ticket_id, operator_id, operation_type, to_status, to_priority, remark)
            VALUES (#{ticketId}, #{operatorId}, 'CREATE', 'NEW', 'MEDIUM', '用户创建工单')
            """)
    int insertCreated(@Param("ticketId") long ticketId, @Param("operatorId") long operatorId);

    // Requires docs/sql/V2__ticket_update_operation.sql on an existing MySQL database.
    @Insert("""
            INSERT INTO ticket_operation_history (ticket_id, operator_id, operation_type, remark)
            VALUES (#{ticketId}, #{operatorId}, 'UPDATE', '用户修改标题和问题描述')
            """)
    int insertUpdated(@Param("ticketId") long ticketId, @Param("operatorId") long operatorId);
}
