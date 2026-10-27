package com.example.ticket.ticket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"classpath:db/schema.sql", "classpath:db/data.sql"})
class TicketHistoryIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setPasswords() {
        jdbc.update("UPDATE sys_user SET password_hash = ?", passwordEncoder.encode("TestPass123!"));
    }

    @Test
    void visibleUserAgentAndAdminCanPageHistory() throws Exception {
        String admin = login("admin");
        mvc.perform(put("/api/tickets/101/assignment").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":3,\"version\":0,\"remark\":\"分配历史测试\"}"))
                .andExpect(status().isOk());
        String agent = login("agent");
        mvc.perform(put("/api/tickets/101/priority").header("Authorization", agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"HIGH\",\"version\":1,\"remark\":\"优先级历史测试\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tickets/101/history?page=1&size=1").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].operationType").value("PRIORITY_CHANGE"))
                .andExpect(jsonPath("$.data.records[0].fromPriority").value("MEDIUM"))
                .andExpect(jsonPath("$.data.records[0].toPriority").value("HIGH"));
        mvc.perform(get("/api/tickets/101/history").header("Authorization", login("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/tickets/101/history").header("Authorization", agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void unrelatedUserAndUnassignedAgentCannotReadHistory() throws Exception {
        mvc.perform(get("/api/tickets/101/history").header("Authorization", login("bob")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        mvc.perform(get("/api/tickets/101/history").header("Authorization", login("agent")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        mvc.perform(get("/api/tickets/999999/history").header("Authorization", login("admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPaginationAndMissingAuthenticationAreRejected() throws Exception {
        mvc.perform(get("/api/tickets/101/history?page=0&size=101").header("Authorization", login("alice")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets/101/history"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "TestPass123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return "Bearer " + node.path("data").path("accessToken").asText();
    }
}
