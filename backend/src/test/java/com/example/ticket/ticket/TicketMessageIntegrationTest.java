package com.example.ticket.ticket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class TicketMessageIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setPasswords() {
        jdbc.update("UPDATE sys_user SET password_hash = ?", passwordEncoder.encode("TestPass123!"));
    }

    @Test
    void userCanCreateAndReadPublicMessage() throws Exception {
        String user = login("alice");
        mvc.perform(post("/api/tickets/101/messages").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("visibility", "PUBLIC", "content", "补充订单发生时间"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ticketId").value(101))
                .andExpect(jsonPath("$.data.senderId").value(1))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.content").value("补充订单发生时间"));
        mvc.perform(get("/api/tickets/101/messages").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].visibility").value("PUBLIC"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = 101 AND operation_type = 'PUBLIC_REPLY'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void assignedAgentCanCreateBothMessageTypesButUserSeesOnlyPublic() throws Exception {
        String agent = login("agent");
        postMessage(103, agent, "PUBLIC", "已收到问题，开始排查");
        postMessage(103, agent, "INTERNAL", "检查支付回调日志");
        mvc.perform(get("/api/tickets/103/messages").header("Authorization", agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/tickets/103/messages").header("Authorization", login("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].visibility").value("PUBLIC"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = 103 AND operation_type IN ('PUBLIC_REPLY', 'INTERNAL_NOTE')",
                Long.class)).isEqualTo(2L);
    }

    @Test
    void userCannotCreateInternalNoteAndAgentCannotAccessUnassignedTicket() throws Exception {
        mvc.perform(post("/api/tickets/101/messages").header("Authorization", login("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("visibility", "INTERNAL", "content", "不应该成功"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/101/messages").header("Authorization", login("agent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("visibility", "PUBLIC", "content", "不应该成功"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/101/messages").header("Authorization", login("agent")))
                .andExpect(status().isNotFound());
    }

    @Test
    void closedTicketRejectsNewMessage() throws Exception {
        jdbc.update("UPDATE ticket SET status = 'CLOSED' WHERE id = 101");
        mvc.perform(post("/api/tickets/101/messages").header("Authorization", login("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("visibility", "PUBLIC", "content", "关闭后不允许发送"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_CLOSED"));
    }

    private void postMessage(long ticketId, String token, String visibility, String content) throws Exception {
        mvc.perform(post("/api/tickets/" + ticketId + "/messages").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("visibility", visibility, "content", content))))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "TestPass123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("data").path("accessToken").asText();
    }
}
