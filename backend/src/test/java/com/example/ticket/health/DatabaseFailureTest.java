package com.example.ticket.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ticket.health.mapper.HealthMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthMapper healthMapper;

    @Test
    void databaseFailureReturns503WithoutLeakingDetails() throws Exception {
        when(healthMapper.selectOne()).thenThrow(
                new DataAccessResourceFailureException("private database connection detail"));

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("数据库访问失败，请检查数据库服务与连接配置"))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
