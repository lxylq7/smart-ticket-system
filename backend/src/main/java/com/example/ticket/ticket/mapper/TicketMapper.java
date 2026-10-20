package com.example.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.ticket.dto.UpdateTicketRequest;
import com.example.ticket.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TicketMapper extends BaseMapper<Ticket> {

    /** Permission, editable state and optimistic locking are checked atomically. */
    @Update("""
            UPDATE ticket SET title = #{request.title}, description = #{request.description},
                category = NULL, category_reason = NULL, ai_status = 'PENDING',
                ai_error_message = NULL, ai_attempts = 0,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND creator_id = #{creatorId} AND version = #{request.version}
                AND status = 'NEW' AND assignee_id IS NULL
            """)
    int updateEditable(@Param("id") long id, @Param("creatorId") long creatorId,
                       @Param("request") UpdateTicketRequest request);
}
