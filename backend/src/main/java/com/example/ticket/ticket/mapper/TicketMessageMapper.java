package com.example.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.ticket.entity.TicketMessage;
import com.example.ticket.ticket.enums.MessageVisibility;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TicketMessageMapper extends BaseMapper<TicketMessage> {

    @Select({
            "<script>",
            "SELECT id, ticket_id, sender_id, visibility, content, created_at, updated_at",
            "FROM ticket_message WHERE ticket_id = #{ticketId}",
            "<if test='visibility != null'>AND visibility = #{visibility}</if>",
            "ORDER BY created_at ASC, id ASC",
            "</script>"
    })
    List<TicketMessage> findByTicketId(@Param("ticketId") long ticketId,
                                       @Param("visibility") MessageVisibility visibility);
}
