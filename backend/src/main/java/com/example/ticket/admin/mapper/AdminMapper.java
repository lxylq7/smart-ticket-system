package com.example.ticket.admin.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket.admin.entity.AdminUserRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AdminMapper {

    @Select("SELECT COUNT(*) FROM ticket")
    long countTickets();

    @Select("SELECT COUNT(*) FROM ticket WHERE status = #{status}")
    long countTicketsByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM sys_user")
    long countUsers();

    @Select("SELECT COUNT(*) FROM sys_user WHERE status = 'ACTIVE'")
    long countActiveUsers();

    @Select("""
            SELECT COUNT(DISTINCT u.id) FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.status = 'ACTIVE' AND r.code = 'AGENT' AND r.status = 'ACTIVE'
            """)
    long countActiveAgents();

    @Select("""
            <script>
            SELECT id, username, password_hash, display_name, email, phone, status,
                   last_login_at, created_at, updated_at
            FROM sys_user
            <where>
                <if test="keyword != null and keyword != ''">
                    (username LIKE CONCAT('%', #{keyword}, '%')
                     OR display_name LIKE CONCAT('%', #{keyword}, '%')
                     OR email LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="status != null">AND status = #{status}</if>
                <if test="role != null">
                    AND EXISTS (SELECT 1 FROM sys_user_role ur
                        JOIN sys_role r ON r.id = ur.role_id
                        WHERE ur.user_id = sys_user.id AND r.code = #{role} AND r.status = 'ACTIVE')
                </if>
            </where>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    Page<AdminUserRecord> pageUsers(Page<AdminUserRecord> page,
                                    @Param("keyword") String keyword,
                                    @Param("status") String status,
                                    @Param("role") String role);

    @Select("""
            SELECT id, username, password_hash, display_name, email, phone, status,
                   last_login_at, created_at, updated_at
            FROM sys_user WHERE id = #{id}
            """)
    AdminUserRecord findUserById(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    long countByUsername(@Param("username") String username);

    @Select("SELECT id FROM sys_role WHERE code = #{role} AND status = 'ACTIVE'")
    Long findRoleId(@Param("role") String role);

    @Insert("""
            INSERT INTO sys_user (username, password_hash, display_name, email, phone, status)
            VALUES (#{user.username}, #{user.passwordHash}, #{user.displayName},
                    #{user.email}, #{user.phone}, 'ACTIVE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "user.id", keyColumn = "id")
    int insertUser(@Param("user") AdminUserRecord user);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    @Update("UPDATE sys_user SET status = #{status}, updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Select("""
            SELECT COUNT(DISTINCT u.id) FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.status = 'ACTIVE' AND r.code = 'ADMIN' AND r.status = 'ACTIVE'
            """)
    long countActiveAdmins();

    @Select("""
            SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE' ORDER BY r.code
            """)
    List<String> findRoleCodes(@Param("userId") long userId);
}
