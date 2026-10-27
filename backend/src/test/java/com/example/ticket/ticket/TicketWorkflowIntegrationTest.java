package com.example.ticket.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.enums.TicketPriority;
import com.example.ticket.ticket.mapper.TicketHistoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"classpath:db/schema.sql", "classpath:db/data.sql"})
class TicketWorkflowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private TicketHistoryMapper historyMapper;

    @BeforeEach
    void setPasswords() {
        jdbc.update("UPDATE sys_user SET password_hash = ?", passwordEncoder.encode("TestPass123!"));
    }

    @Test
    void completeLifecycleSavesAuditAndTimes() throws Exception {
        String admin = login("admin"), agent = login("agent");
        assignment(101, admin, 3, 0, 200);
        var assigned = jdbc.queryForMap("SELECT * FROM ticket_operation_history WHERE ticket_id = 101");
        assertThat(assigned).containsEntry("OPERATION_TYPE", "ASSIGN")
                .containsEntry("FROM_STATUS", "PENDING").containsEntry("TO_STATUS", "ASSIGNED")
                .containsEntry("OPERATOR_ID", 5L).containsEntry("FROM_ASSIGNEE_ID", null)
                .containsEntry("TO_ASSIGNEE_ID", 3L).containsEntry("REMARK", "分配测试");
        change(101, agent, "PROCESSING", 1, 200);
        change(101, agent, "WAITING_USER", 2, 200);
        change(101, agent, "PROCESSING", 3, 200);
        change(101, agent, "RESOLVED", 4, 200);
        assertThat(jdbc.queryForMap("SELECT resolved_at, closed_at FROM ticket WHERE id = 101"))
                .hasEntrySatisfying("RESOLVED_AT", value -> assertThat(value).isNotNull())
                .containsEntry("CLOSED_AT", null);
        change(101, admin, "CLOSED", 5, 200);
        assertThat(jdbc.queryForMap("SELECT resolved_at, closed_at, version FROM ticket WHERE id = 101"))
                .hasEntrySatisfying("RESOLVED_AT", value -> assertThat(value).isNotNull())
                .hasEntrySatisfying("CLOSED_AT", value -> assertThat(value).isNotNull())
                .containsEntry("VERSION", 6);
        var history = jdbc.queryForList("SELECT from_status, to_status, operator_id FROM ticket_operation_history "
                + "WHERE ticket_id = 101 AND operation_type = 'STATUS_CHANGE' ORDER BY id");
        assertThat(history).hasSize(5);
        assertThat(history.get(0)).containsEntry("FROM_STATUS", "ASSIGNED")
                .containsEntry("TO_STATUS", "PROCESSING").containsEntry("OPERATOR_ID", 3L);
        assertThat(history.get(4)).containsEntry("FROM_STATUS", "RESOLVED")
                .containsEntry("TO_STATUS", "CLOSED").containsEntry("OPERATOR_ID", 5L);
        mvc.perform(get("/api/tickets/101").header("Authorization", admin))
                .andExpect(jsonPath("$.data.resolvedAt").isString())
                .andExpect(jsonPath("$.data.closedAt").isString());
    }

    // Explicit expected matrix, independent of the enum implementation.
    static Stream<Arguments> statusPairs() {
        Map<String, List<String>> allowed = Map.of(
                "ASSIGNED", List.of("PROCESSING"),
                "PROCESSING", List.of("WAITING_USER", "RESOLVED"),
                "WAITING_USER", List.of("PROCESSING"),
                "RESOLVED", List.of("PROCESSING", "CLOSED"));
        return Arrays.stream(TicketStatus.values()).flatMap(from -> Arrays.stream(TicketStatus.values())
                .map(to -> Arguments.of(from.name(), to.name(), allowed.getOrDefault(from.name(), List.of()).contains(to.name()))));
    }

    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @MethodSource("statusPairs")
    void allStatusPairsAreEnforced(String from, String to, boolean allowed) throws Exception {
        jdbc.update("UPDATE ticket SET status = ?, assignee_id = 3 WHERE id = 101", from);
        change(101, login("admin"), to, 0, allowed ? 200 : 409);
        assertThat(jdbc.queryForObject("SELECT status FROM ticket WHERE id = 101", String.class))
                .isEqualTo(allowed ? to : from);
        assertThat(historyCount(101)).isEqualTo(allowed ? 1 : 0);
        if (!allowed) assertThat(version(101)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "ASSIGNED", "PROCESSING", "WAITING_USER"})
    void adminCanAssignOrReassignActiveTickets(String from) throws Exception {
        jdbc.update("UPDATE ticket SET status = ?, assignee_id = ? WHERE id = 101", from, from.equals("PENDING") ? null : 3L);
        assignment(101, login("admin"), 4, 0, 200);
        assertThat(jdbc.queryForMap("SELECT status, assignee_id, version FROM ticket WHERE id = 101"))
                .containsEntry("STATUS", "ASSIGNED").containsEntry("ASSIGNEE_ID", 4L).containsEntry("VERSION", 1);
        var history = jdbc.queryForMap("SELECT * FROM ticket_operation_history WHERE ticket_id = 101");
        assertThat(history).containsEntry("FROM_STATUS", from).containsEntry("TO_STATUS", "ASSIGNED")
                .containsEntry("FROM_ASSIGNEE_ID", from.equals("PENDING") ? null : 3L)
                .containsEntry("TO_ASSIGNEE_ID", 4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESOLVED", "CLOSED"})
    void resolvedAndClosedTicketsCannotBeAssigned(String from) throws Exception {
        jdbc.update("UPDATE ticket SET status = ? WHERE id = 103", from);
        assignment(103, login("admin"), 4, 0, 409);
        assertThat(historyCount(103)).isZero();
        assertThat(version(103)).isZero();
    }

    @Test
    void usersCannotAssignOrChangeStatusAndAgentsCannotAssign() throws Exception {
        for (String username : List.of("alice", "agent")) {
            assignment(101, login(username), 3, 0, 403);
        }
        change(103, login("alice"), "PROCESSING", 0, 403);
        assertThat(historyCount(101) + historyCount(103)).isZero();
        mvc.perform(put("/api/tickets/101/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\",\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/tickets/101/assignment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":3,\"version\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignedAgentAndAdminCanChangePriorityAndHistoryIsSaved() throws Exception {
        changePriority(103, login("agent"), "HIGH", 0, 200);
        assertThat(jdbc.queryForMap("SELECT priority, version FROM ticket WHERE id = 103"))
                .containsEntry("PRIORITY", "HIGH").containsEntry("VERSION", 1);
        assertThat(jdbc.queryForMap("SELECT operation_type, operator_id, from_priority, to_priority, remark "
                + "FROM ticket_operation_history WHERE ticket_id = 103"))
                .containsEntry("OPERATION_TYPE", "PRIORITY_CHANGE").containsEntry("OPERATOR_ID", 3L)
                .containsEntry("FROM_PRIORITY", "MEDIUM").containsEntry("TO_PRIORITY", "HIGH")
                .containsEntry("REMARK", "优先级测试");
        changePriority(101, login("admin"), "URGENT", 0, 200);
        assertThat(jdbc.queryForObject("SELECT priority FROM ticket WHERE id = 101", String.class))
                .isEqualTo("URGENT");
    }

    @Test
    void usersOtherAgentsClosedTicketsAndStaleVersionsCannotChangePriority() throws Exception {
        changePriority(103, login("alice"), "HIGH", 0, 403);
        changePriority(104, login("agent"), "HIGH", 0, 404);
        jdbc.update("UPDATE ticket SET status = 'CLOSED' WHERE id = 105");
        changePriority(105, login("admin"), "URGENT", 0, 409);
        changePriority(103, login("agent"), "HIGH", 9, 409);
        assertThat(historyCount(103) + historyCount(104) + historyCount(105)).isZero();
    }

    @Test
    void priorityRequestValidationRejectsUnknownMissingAndOversizedValues() throws Exception {
        String admin = login("admin");
        for (String body : List.of("{}", "{\"priority\":\"INVALID\",\"version\":0}",
                "{\"priority\":\"HIGH\"}",
                json.writeValueAsString(Map.of("priority", "HIGH", "version", 0, "remark", "x".repeat(501))))) {
            send("/api/tickets/101/priority", admin, body, 400);
        }
        assertThat(historyCount(101)).isZero();
    }

    @Test
    void concurrentPriorityChangesHaveOneWinnerAndOneAuditRecord() throws Exception {
        String admin = login("admin");
        race(() -> changePriority(101, admin, "HIGH", 0, 0),
                () -> changePriority(101, admin, "URGENT", 0, 0));
        assertThat(historyCount(101)).isEqualTo(1);
        assertThat(version(101)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT to_priority FROM ticket_operation_history WHERE ticket_id = 101",
                String.class)).isEqualTo(jdbc.queryForObject("SELECT priority FROM ticket WHERE id = 101", String.class));
    }

    @Test
    void agentCannotProcessUnassignedOrOtherAgentsTicketsEvenIfCreator() throws Exception {
        String agent = login("agent");
        change(101, agent, "PROCESSING", 0, 404);
        change(104, agent, "PROCESSING", 0, 404);
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 2)");
        change(103, login("alice"), "PROCESSING", 0, 404);
        change(999999, agent, "PROCESSING", 0, 404);
        assertThat(historyCount(103)).isZero();
    }

    @Test
    void reassignmentImmediatelyRevokesOldAgentAndGrantsNewAgent() throws Exception {
        String oldAgent = login("agent"), newAgent = login("agent2");
        assignment(103, login("admin"), 4, 0, 200);
        change(103, oldAgent, "PROCESSING", 1, 404);
        change(103, newAgent, "PROCESSING", 1, 200);
        assertThat(historyCount(103)).isEqualTo(2);
    }

    @Test
    void onlyAdminCanCloseAndReopeningClearsResolvedTime() throws Exception {
        String agent = login("agent");
        jdbc.update("UPDATE ticket SET resolved_at = CURRENT_TIMESTAMP(3) WHERE id = 105");
        change(105, agent, "CLOSED", 0, 403);
        change(105, agent, "PROCESSING", 0, 200);
        assertThat(jdbc.queryForMap("SELECT resolved_at, closed_at FROM ticket WHERE id = 105"))
                .containsEntry("RESOLVED_AT", null).containsEntry("CLOSED_AT", null);
        change(105, agent, "RESOLVED", 1, 200);
        change(105, login("admin"), "CLOSED", 2, 200);
        change(105, agent, "PROCESSING", 3, 409);
    }

    @Test
    void invalidInactiveOrNonAgentAssigneesAreRejected() throws Exception {
        String admin = login("admin");
        for (long id : List.of(1L, 5L, 6L, 99999L)) assignment(101, admin, id, 0, 400);
        jdbc.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = 3");
        assignment(101, admin, 3, 0, 400);
        jdbc.update("UPDATE sys_role SET status = 'DISABLED' WHERE code = 'AGENT'");
        assignment(101, admin, 4, 0, 400);
        assertThat(historyCount(101)).isZero();
        assertThat(version(101)).isZero();
    }

    @Test
    void sameAssigneeStaleVersionAndMissingTicketAreRejected() throws Exception {
        String admin = login("admin");
        assignment(103, admin, 3, 0, 409);
        assignment(101, admin, 3, 9, 409);
        assignment(99999, admin, 3, 0, 404);
        change(103, login("agent"), "PROCESSING", 9, 409);
        assertThat(historyCount(101) + historyCount(103)).isZero();
    }

    @Test
    void requestValidationRejectsMissingUnknownNegativeAndOversizedValues() throws Exception {
        String admin = login("admin");
        for (String body : List.of("{}", "{\"assigneeId\":0,\"version\":0}",
                "{\"assigneeId\":3,\"version\":-1}", "{\"assigneeId\":3}",
                "{\"assigneeId\":3,\"version\":0,\"operatorId\":1}",
                json.writeValueAsString(Map.of("assigneeId", 3, "version", 0, "remark", "x".repeat(501))))) {
            send("/api/tickets/101/assignment", admin, body, 400);
        }
        for (String body : List.of("{}", "{\"status\":\"NEW\",\"version\":0}",
                "{\"status\":\"IN_PROGRESS\",\"version\":0}", "{\"status\":null,\"version\":0}",
                "{\"status\":\"PROCESSING\",\"version\":-1}", "{\"status\":\"PROCESSING\"}",
                "{\"status\":\"PROCESSING\",\"version\":0,\"assigneeId\":4}",
                json.writeValueAsString(Map.of("status", "PROCESSING", "version", 0, "remark", "x".repeat(501))))) {
            send("/api/tickets/103/status", admin, body, 400);
        }
        send("/api/tickets/0/status", admin, "{\"status\":\"PROCESSING\",\"version\":0}", 400);
        assertThat(historyCount(101) + historyCount(103)).isZero();
    }

    @Test
    void statusHistoryFailureRollsBackStatusVersionAndTimestamps() throws Exception {
        doThrow(new DataAccessResourceFailureException("simulated history failure"))
                .when(historyMapper).insertStatusChanged(any(Ticket.class), anyLong(), any(TicketStatus.class), any());
        jdbc.update("UPDATE ticket SET status = 'PROCESSING' WHERE id = 103");
        var before = jdbc.queryForMap("SELECT * FROM ticket WHERE id = 103");
        change(103, login("agent"), "RESOLVED", 0, 503);
        assertThat(jdbc.queryForMap("SELECT * FROM ticket WHERE id = 103")).isEqualTo(before);
        assertThat(historyCount(103)).isZero();
    }

    @Test
    void assignmentHistoryFailureRollsBackAssigneeStatusAndVersion() throws Exception {
        doThrow(new DataAccessResourceFailureException("simulated history failure"))
                .when(historyMapper).insertAssigned(any(Ticket.class), anyLong(), anyLong(), any());
        var before = jdbc.queryForMap("SELECT * FROM ticket WHERE id = 101");
        assignment(101, login("admin"), 3, 0, 503);
        assertThat(jdbc.queryForMap("SELECT * FROM ticket WHERE id = 101")).isEqualTo(before);
        assertThat(historyCount(101)).isZero();
    }

    @Test
    void concurrentAssignmentsHaveOneWinnerAndOneAuditRecord() throws Exception {
        String admin = login("admin");
        race(() -> assignment(101, admin, 3, 0, 0), () -> assignment(101, admin, 4, 0, 0));
        assertThat(historyCount(101)).isEqualTo(1);
        assertThat(version(101)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT to_assignee_id FROM ticket_operation_history WHERE ticket_id = 101", Long.class))
                .isEqualTo(jdbc.queryForObject("SELECT assignee_id FROM ticket WHERE id = 101", Long.class));
    }

    @Test
    void concurrentStatusChangesHaveOneWinnerAndOneAuditRecord() throws Exception {
        jdbc.update("UPDATE ticket SET status = 'PROCESSING' WHERE id = 103");
        String agent = login("agent");
        race(() -> change(103, agent, "WAITING_USER", 0, 0), () -> change(103, agent, "RESOLVED", 0, 0));
        assertThat(historyCount(103)).isEqualTo(1);
        assertThat(version(103)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT to_status FROM ticket_operation_history WHERE ticket_id = 103", String.class))
                .isEqualTo(jdbc.queryForObject("SELECT status FROM ticket WHERE id = 103", String.class));
    }

    @Test
    void concurrentAssignmentAndAgentActionCannotOverwriteEachOther() throws Exception {
        String admin = login("admin"), agent = login("agent");
        race(() -> assignment(103, admin, 4, 0, 0), () -> change(103, agent, "PROCESSING", 0, 0), true);
        assertThat(historyCount(103)).isEqualTo(1);
        assertThat(version(103)).isEqualTo(1);
    }

    @Test
    void concurrentContentEditAndAssignmentCannotOverwriteEachOther() throws Exception {
        String admin = login("admin"), user = login("alice");
        race(() -> assignment(101, admin, 3, 0, 0), () -> send("/api/tickets/101", user,
                "{\"title\":\"edited\",\"description\":\"test\",\"version\":0}", 0));
        assertThat(historyCount(101)).isEqualTo(1);
        assertThat(version(101)).isEqualTo(1);
    }

    private void race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        race(first, second, false);
    }

    private void race(Callable<Integer> first, Callable<Integer> second, boolean mayLoseAccess) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var results = Stream.of(first, second).map(action -> executor.submit(() -> {
                start.await();
                return action.call();
            })).toList();
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(code -> code == 200).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(code -> code != 200).toList())
                    .allSatisfy(code -> assertThat(code).isIn(mayLoseAccess ? List.of(409, 404) : List.of(409)));
        } finally {
            executor.shutdownNow();
        }
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "TestPass123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("accessToken").asText();
    }

    private int assignment(long id, String token, long agent, int version, int expected) throws Exception {
        return send("/api/tickets/" + id + "/assignment", token,
                json.writeValueAsString(Map.of("assigneeId", agent, "version", version, "remark", " 分配测试 ")), expected);
    }

    private int change(long id, String token, String target, int version, int expected) throws Exception {
        return send("/api/tickets/" + id + "/status", token,
                json.writeValueAsString(Map.of("status", target, "version", version, "remark", "状态测试")), expected);
    }

    private int changePriority(long id, String token, String target, int version, int expected) throws Exception {
        return send("/api/tickets/" + id + "/priority", token,
                json.writeValueAsString(Map.of("priority", target, "version", version, "remark", "优先级测试")), expected);
    }

    private int send(String path, String token, String body, int expected) throws Exception {
        var response = mvc.perform(put(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        if (expected != 0) {
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
            assertThat(json.readTree(response.getContentAsString()).has("code")).isTrue();
        }
        return response.getStatus();
    }

    private int version(long id) {
        return jdbc.queryForObject("SELECT version FROM ticket WHERE id = ?", Integer.class, id);
    }

    private int historyCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = ?", Integer.class, id);
    }
}
