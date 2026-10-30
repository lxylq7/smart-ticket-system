package com.example.ticket.health.service;

import com.example.ticket.health.mapper.HealthMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final HealthMapper healthMapper;

    public HealthService(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    public void checkDatabase() {
        if (healthMapper.selectOne() != 1) {
            throw new DataAccessResourceFailureException("Unexpected database health result");
        }
    }
}
