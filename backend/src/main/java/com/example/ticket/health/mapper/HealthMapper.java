package com.example.ticket.health.mapper;

import org.apache.ibatis.annotations.Select;

public interface HealthMapper {

    @Select("SELECT 1")
    int selectOne();
}
