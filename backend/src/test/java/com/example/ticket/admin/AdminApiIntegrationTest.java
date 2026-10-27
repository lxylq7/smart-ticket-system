package com.example.ticket.admin;

import static org.assertj.core.api.Assertions.assertThat;
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
class AdminApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setPasswords() {
        jdbc.update("UPDATE sys_user SET password_hash = ?", passwordEncoder.encode("TestPass123!"));
    }

    @Test
    void adminCanReadStatsAndPaginatedUserDirectory() throws Exception {
        String token = login("admin");
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTickets").value(5))
                .andExpect(jsonPath("$.data.pendingTickets").value(2))
                .andExpect(jsonPath("$.data.assignedTickets").value(2))
                .andExpect(jsonPath("$.data.resolvedTickets").value(1))
                .andExpect(jsonPath("$.data.totalUsers").value(6))
                .andExpect(jsonPath("$.data.activeAgents").value(2));
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(token))
                        .param("page", "1").param("size", "2").param("role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].roles[0]").value("AGENT"))
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
    }

    @Test
    void nonAdminCannotUseAdminEndpoints() throws Exception {
        String token = login("alice");
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanCreateUserOrAgentButNeverAdmin() throws Exception {
        String token = login("admin");
        String body = json.writeValueAsString(Map.of("username", "new-agent", "password", "NewPass123!",
                "displayName", "New Agent", "email", "agent@example.com", "role", "AGENT"));
        String response = mvc.perform(post("/api/admin/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.username").value("new-agent"))
                .andExpect(jsonPath("$.data.roles[0]").value("AGENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = 2",
                Long.class, id)).isEqualTo(1L);

        mvc.perform(post("/api/admin/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("new-agent", "new-admin").replace("AGENT", "ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_FORBIDDEN"));
    }

    @Test
    void adminCanDisableAndEnableUserButCannotDisableSelf() throws Exception {
        String token = login("admin");
        mvc.perform(put("/api/admin/users/1/status").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(put("/api/admin/users/1/status").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(put("/api/admin/users/5/status").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_DISABLE_FORBIDDEN"));
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "TestPass123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.path("data").path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
