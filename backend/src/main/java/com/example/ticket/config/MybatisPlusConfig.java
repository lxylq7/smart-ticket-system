package com.example.ticket.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.ticket.health.mapper")
public class MybatisPlusConfig {
}
