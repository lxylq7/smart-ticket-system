package com.example.ticket.ticket.mapper;

import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.TicketStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface TicketHistoryMapper {

    @Insert("""
            INSERT INTO ticket_operation_history
                (ticket_id, operator_id, operation_type, to_status, to_priority, remark)
            VALUES (#{ticketId}, #{operatorId}, 'CREATE', 'PENDING', 'MEDIUM', '用户创建工单')
            """)
    int insertCreated(@Param("ticketId") long ticketId, @Param("operatorId") long operatorId);

    // Requires docs/sql/V2__ticket_update_operation.sql on an existing MySQL database.
    @Insert("""
            INSERT INTO ticket_operation_history (ticket_id, operator_id, operation_type, remark)
            VALUES (#{ticketId}, #{operatorId}, 'UPDATE', '用户修改标题和问题描述')
            """)
    int insertUpdated(@Param("ticketId") long ticketId, @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO ticket_operation_history
                (ticket_id, operator_id, operation_type, from_status, to_status,
                 from_assignee_id, to_assignee_id, remark)
            VALUES (#{ticket.id}, #{operatorId}, 'ASSIGN', #{ticket.status}, 'ASSIGNED',
                    #{ticket.assigneeId}, #{assigneeId}, #{remark})
            """)
    int insertAssigned(@Param("ticket") Ticket ticket, @Param("operatorId") long operatorId,
                       @Param("assigneeId") long assigneeId, @Param("remark") String remark);

    @Insert("""
            INSERT INTO ticket_operation_history
                (ticket_id, operator_id, operation_type, from_status, to_status, remark)
            VALUES (#{ticket.id}, #{operatorId}, 'STATUS_CHANGE', #{ticket.status}, #{target}, #{remark})
            """)
    int insertStatusChanged(@Param("ticket") Ticket ticket, @Param("operatorId") long operatorId,
                            @Param("target") TicketStatus target, @Param("remark") String remark);

    @Insert("""
            INSERT INTO ticket_operation_history (ticket_id, operation_type, remark)
            VALUES (#{ticketId}, 'AI_CLASSIFY', #{remark})
            """)
    int insertAiClassified(@Param("ticketId") long ticketId, @Param("remark") String remark);

    @Insert("""
            INSERT INTO ticket_operation_history (ticket_id, operation_type, remark)
            VALUES (#{ticketId}, 'AI_CLASSIFY_FAILED', #{errorMessage})
            """)
    int insertAiClassificationFailed(@Param("ticketId") long ticketId, @Param("errorMessage") String errorMessage);

    @Insert("""
            INSERT INTO ticket_operation_history (ticket_id, operator_id, operation_type, remark)
            VALUES (#{ticketId}, #{operatorId},
                CASE WHEN #{visibility} = 'PUBLIC' THEN 'PUBLIC_REPLY' ELSE 'INTERNAL_NOTE' END,
                CASE WHEN #{visibility} = 'PUBLIC' THEN '发送公开回复' ELSE '添加内部备注' END)
            """)
    int insertMessageCreated(@Param("ticketId") long ticketId, @Param("operatorId") long operatorId,
                             @Param("visibility") String visibility);
}
