package com.example.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.ticket.dto.UpdateTicketRequest;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.enums.TicketCategory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TicketMapper extends BaseMapper<Ticket> {

    /** Permission, editable state and optimistic locking are checked atomically. */
    @Update("""
            UPDATE ticket SET title = #{request.title}, description = #{request.description},
                category = NULL, category_reason = NULL, ai_status = 'PENDING',
                ai_error_message = NULL, ai_attempts = 0,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND creator_id = #{creatorId} AND version = #{request.version}
                AND status = 'PENDING' AND assignee_id IS NULL
            """)
    int updateEditable(@Param("id") long id, @Param("creatorId") long creatorId,
                       @Param("request") UpdateTicketRequest request);

    @Select("""
            SELECT COUNT(*) FROM sys_user u
            WHERE u.id = #{id} AND u.status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                    WHERE ur.user_id = u.id AND r.code = 'AGENT' AND r.status = 'ACTIVE')
            """)
    int countActiveAgent(long id);

    @Update("""
            <script>
            UPDATE ticket SET assignee_id = #{assigneeId}, status = 'ASSIGNED',
                resolved_at = NULL, closed_at = NULL,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{ticket.id} AND version = #{version} AND status = #{ticket.status}
                <choose>
                    <when test="ticket.assigneeId != null">AND assignee_id = #{ticket.assigneeId}</when>
                    <otherwise>AND assignee_id IS NULL</otherwise>
                </choose>
            </script>
            """)
    int assign(@Param("ticket") Ticket ticket, @Param("assigneeId") long assigneeId,
               @Param("version") int version);

    @Update("""
            <script>
            UPDATE ticket SET status = #{target},
                resolved_at = CASE WHEN #{target} = 'RESOLVED' THEN CURRENT_TIMESTAMP(3)
                    WHEN #{target} = 'CLOSED' THEN resolved_at ELSE NULL END,
                closed_at = CASE WHEN #{target} = 'CLOSED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{ticket.id} AND version = #{version} AND status = #{ticket.status}
                AND assignee_id = #{ticket.assigneeId}
                <if test="!admin">AND assignee_id = #{operatorId}</if>
            </script>
            """)
    int changeStatus(@Param("ticket") Ticket ticket, @Param("target") TicketStatus target,
                     @Param("version") int version, @Param("operatorId") long operatorId,
                     @Param("admin") boolean admin);

    @Update("""
            UPDATE ticket SET ai_attempts = ai_attempts + 1,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND version = #{version} AND category IS NULL AND ai_status = 'PENDING'
            """)
    int claimClassification(@Param("id") long id, @Param("version") int version);

    @Update("""
            UPDATE ticket SET category = #{category}, category_reason = #{reason}, ai_status = 'SUCCESS',
                ai_error_message = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND version = #{version} AND category IS NULL AND ai_status = 'PENDING'
            """)
    int saveClassificationSuccess(@Param("id") long id, @Param("version") int version,
                                  @Param("category") TicketCategory category, @Param("reason") String reason);

    @Update("""
            UPDATE ticket SET ai_status = 'FAILED', ai_error_message = #{errorMessage},
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND version = #{version} AND category IS NULL AND ai_status = 'PENDING'
            """)
    int saveClassificationFailure(@Param("id") long id, @Param("version") int version,
                                  @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE ticket SET ai_status = 'PENDING', ai_error_message = NULL,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND version = #{version} AND category IS NULL AND ai_status = 'FAILED'
            """)
    int resetClassification(@Param("id") long id, @Param("version") int version);
}
