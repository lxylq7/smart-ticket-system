package com.example.ticket.auth.mapper;

import com.example.ticket.auth.model.UserAccount;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface UserAccountMapper {

    @Select("SELECT id, username, password_hash, display_name, status FROM sys_user WHERE username = #{username}")
    UserAccount findByUsername(String username);

    @Select("SELECT id, username, password_hash, display_name, status FROM sys_user WHERE id = #{id}")
    UserAccount findById(long id);

    @Select("""
            SELECT r.code FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE'
            ORDER BY r.code
            """)
    List<String> findRoleCodes(long userId);
}
