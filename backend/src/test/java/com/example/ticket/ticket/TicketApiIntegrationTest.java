package com.example.ticket.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ticket.config.SecurityConfig;
import com.example.ticket.ticket.mapper.TicketHistoryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"classpath:db/schema.sql", "classpath:db/data.sql"})
@Import(TicketApiIntegrationTest.CaptureConfiguration.class)
class TicketApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private SqlCapture sqlCapture;
    @MockitoSpyBean private TicketHistoryMapper historyMapper;

    @BeforeEach
    void setPasswords() {
        jdbc.update("UPDATE sys_user SET password_hash = ?", passwordEncoder.encode("TestPass123!"));
        sqlCapture.clear();
    }

    @Test
    void loginReturnsTokenWithoutPasswordHash() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", "TestPass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("password", "passwordHash", "TestPass123!");
    }

    @Test
    void wrongPasswordAndDisabledAccountCannotLogin() throws Exception {
        for (Map<String, String> request : List.of(
                Map.of("username", "alice", "password", "wrong"),
                Map.of("username", "disabled", "password", "TestPass123!"),
                Map.of("username", "missing", "password", "TestPass123!"))) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void missingAndInvalidTokensAreRejected() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndWrongIssuerTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        for (JwtClaimsSet claims : List.of(
                JwtClaimsSet.builder().issuer(SecurityConfig.JWT_ISSUER).subject("1")
                        .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build(),
                JwtClaimsSet.builder().issuer("wrong-issuer").subject("1")
                        .issuedAt(now).expiresAt(now.plusSeconds(3600)).build())) {
            String token = jwtEncoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/tickets").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void disablingUserInvalidatesAlreadyIssuedToken() throws Exception {
        String token = login("alice");
        jdbc.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = 1");
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleRemovalIsEffectiveForAlreadyIssuedToken() throws Exception {
        String token = login("alice");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 1");
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUsesAuthenticatedCreatorAndServerDefaultsWithHistory() throws Exception {
        String token = login("alice");
        String response = mvc.perform(post("/api/tickets").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" 支付异常 \",\"description\":\" 已扣款但订单未更新 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.creatorId").value(1))
                .andExpect(jsonPath("$.data.assigneeId").isEmpty())
                .andExpect(jsonPath("$.data.category").isEmpty())
                .andExpect(jsonPath("$.data.aiStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.title").value("支付异常"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.updatedAt").isString())
                .andReturn().getResponse().getContentAsString();
        JsonNode ticket = json.readTree(response).get("data");
        assertThat(ticket.get("ticketNo").asText()).hasSize(32);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = ? AND operation_type = 'CREATE'",
                Long.class, ticket.get("id").asLong())).isEqualTo(1L);
    }

    @Test
    void duplicateSubmissionsStillReceiveDifferentTicketNumbers() throws Exception {
        String token = login("alice");
        String first = create(token, "same title");
        String second = create(token, "same title");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void identityAndPrivilegedFieldInjectionAreRejected() throws Exception {
        String token = login("alice");
        for (String extra : List.of("\"creatorId\":2", "\"assigneeId\":3", "\"priority\":\"URGENT\"",
                "\"status\":\"CLOSED\"", "\"category\":\"ORDER\"")) {
            mvc.perform(post("/api/tickets").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"valid\",\"description\":\"valid\"," + extra + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket", Long.class)).isEqualTo(5L);
    }

    @Test
    void blankOrOversizedFieldsAreRejected() throws Exception {
        String token = login("alice");
        for (Map<String, String> body : List.of(
                Map.of("title", " ", "description", "valid"),
                Map.of("title", "x".repeat(201), "description", "valid"),
                Map.of("title", "valid", "description", "x".repeat(20001)))) {
            mvc.perform(post("/api/tickets").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void userCanReadOwnTicketButNotOthers() throws Exception {
        String token = login("alice");
        mvc.perform(get("/api/tickets/101").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        for (long id : List.of(102L, 999999L)) {
            mvc.perform(get("/api/tickets/" + id).header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        }
    }

    @Test
    void agentSeesOnlyAssignedTicketsAndCannotCreate() throws Exception {
        String token = login("agent");
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/tickets/103").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tickets/104").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/tickets").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"valid\",\"description\":\"valid\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAllTickets() throws Exception {
        String token = login("admin");
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5));
        mvc.perform(get("/api/tickets/104").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void multiRoleScopeIsUnionAndCannotEscapeFilters() throws Exception {
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 2)");
        jdbc.update("UPDATE ticket SET assignee_id = 1 WHERE id = 104");
        String token = login("alice");
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)).param("status", "ASSIGNED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/tickets/my").header("Authorization", bearer(token)).param("status", "ASSIGNED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void paginationIsAppliedInDatabaseAndCountsOnlyVisibleTickets() throws Exception {
        String token = login("alice");
        sqlCapture.clear();
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token))
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pages").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(103));
        assertThat(sqlCapture.statements()).anyMatch(sql ->
                sql.toLowerCase().contains("limit") && sql.toLowerCase().contains("creator_id"));
    }

    @Test
    void ownTicketsCannotBeRedirectedByQueryParameters() throws Exception {
        mvc.perform(get("/api/tickets/my").header("Authorization", bearer(login("alice")))
                        .param("creatorId", "2").param("userId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].creatorId").value(1));
    }

    @Test
    void filtersAndPaginationBoundariesAreValidated() throws Exception {
        String token = login("alice");
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)).param("status", "NEW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)).param("keyword", "Bob"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        for (Map<String, String> params : List.of(Map.of("page", "0"), Map.of("size", "101"),
                Map.of("size", "0"), Map.of("status", "INVALID"), Map.of("aiStatus", "INVALID"))) {
            var request = get("/api/tickets").header("Authorization", bearer(token));
            params.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/tickets").header("Authorization", bearer(token)).param("page", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void creatorCanUpdateUnassignedNewTicketWithHistory() throws Exception {
        jdbc.update("UPDATE ticket SET category = 'ORDER', category_reason = 'old reason', ai_status = 'SUCCESS' WHERE id = 101");
        mvc.perform(put("/api/tickets/101").header("Authorization", bearer(login("alice")))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("updated", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("updated"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.category").isEmpty())
                .andExpect(jsonPath("$.data.categoryReason").isEmpty())
                .andExpect(jsonPath("$.data.aiStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.status").value("NEW"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = 101 AND operation_type = 'UPDATE'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void updateRejectsOtherOwnersAndAssignedTickets() throws Exception {
        String token = login("alice");
        mvc.perform(put("/api/tickets/102").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("attack", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/tickets/103").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("too late", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_EDITABLE"));
    }

    @Test
    void staleVersionAndPrivilegedUpdatesAreRejected() throws Exception {
        String token = login("alice");
        mvc.perform(put("/api/tickets/101").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("stale", 5)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/tickets/101").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"attack\",\"description\":\"attack\",\"version\":0,\"creatorId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentUpdatesAllowOnlyOneWinner() throws Exception {
        String token = login("alice");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = List.of("first", "second").stream().map(title -> executor.submit(() -> {
                start.await();
                return mvc.perform(put("/api/tickets/101").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(updateBody(title, 0)))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT version FROM ticket WHERE id = 101", Integer.class)).isEqualTo(1);
    }

    @Test
    void historyFailureRollsBackTicketCreation() throws Exception {
        doThrow(new DataAccessResourceFailureException("simulated history failure"))
                .when(historyMapper).insertCreated(anyLong(), anyLong());
        mvc.perform(post("/api/tickets").header("Authorization", bearer(login("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"rollback\",\"description\":\"rollback\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket", Long.class)).isEqualTo(5L);
    }

    @Test
    void historyFailureRollsBackTicketUpdate() throws Exception {
        doThrow(new DataAccessResourceFailureException("simulated history failure"))
                .when(historyMapper).insertUpdated(anyLong(), anyLong());
        mvc.perform(put("/api/tickets/101").header("Authorization", bearer(login("alice")))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("rollback", 0)))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT title FROM ticket WHERE id = 101", String.class)).isEqualTo("Alice new");
        assertThat(jdbc.queryForObject("SELECT version FROM ticket WHERE id = 101", Integer.class)).isZero();
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "TestPass123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("accessToken").asText();
    }

    private String create(String token, String title) throws Exception {
        String response = mvc.perform(post("/api/tickets").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", title, "description", "test"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("ticketNo").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String updateBody(String title, int version) throws Exception {
        return json.writeValueAsString(Map.of("title", title, "description", "updated description", "version", version));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {
        @Bean
        SqlCapture sqlCapture() { return new SqlCapture(); }
    }
}
