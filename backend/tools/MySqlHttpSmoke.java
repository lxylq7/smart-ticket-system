import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Explicit development test tool. Never loaded by the application or normal unit tests. */
class MySqlHttpSmoke {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String prefix = "smoke_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final String password = UUID.randomUUID().toString();
    private final Map<String, Long> users = new LinkedHashMap<>();
    private final List<String> results = new ArrayList<>();
    private Connection database;
    private Process application;
    private String baseUrl;
    private int passed;

    public static void main(String[] args) throws Exception {
        new MySqlHttpSmoke().run();
    }

    private void run() throws Exception {
        Files.createDirectories(Path.of("target"));
        boolean success = false;
        try {
            connect();
            migrateIfRequired();
            seedAccounts();
            startApplication();
            testHttp();
            success = true;
        } catch (Throwable failure) {
            // Do not print credentials, tokens, database URL or private response bodies.
            record("FAIL: " + failure.getClass().getSimpleName()
                    + (failure instanceof AssertionError ? " - " + failure.getMessage() : ""));
            throw failure;
        } finally {
            try {
                if (application != null) {
                    application.destroy();
                    if (!application.waitFor(10, TimeUnit.SECONDS)) {
                        application.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                    }
                }
                if (database != null) {
                    cleanup();
                }
            } catch (Throwable cleanupFailure) {
                success = false;
                record("FAIL: cleanup - " + cleanupFailure.getClass().getSimpleName());
                throw cleanupFailure;
            } finally {
                if (database != null) database.close();
                record("HTTP assertions passed: " + passed);
                record("Overall result: " + (success ? "PASS" : "FAIL"));
                Files.write(Path.of("target/mysql-http-smoke-report.txt"), results, StandardCharsets.UTF_8);
            }
        }
    }

    private void connect() throws Exception {
        Map<?, ?> config = new Yaml().load(Files.readString(Path.of("config/application-local.yml")));
        Map<?, ?> ds = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        database = DriverManager.getConnection(String.valueOf(ds.get("url")),
                String.valueOf(ds.get("username")), String.valueOf(ds.get("password")));
        execute("SET SESSION lock_wait_timeout = 5");
        execute("SET SESSION innodb_lock_wait_timeout = 5");
        record("MySQL connection: OK");
    }

    private void migrateIfRequired() throws Exception {
        String clause;
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS "
                        + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_ticket_history_operation_type'")) {
            if (!rows.next()) throw new AssertionError("Expected operation history CHECK not found");
            // MySQL may escape literal quotes in INFORMATION_SCHEMA.CHECK_CLAUSE.
            clause = rows.getString(1).replace("\\", "");
        }
        if (clause.contains("'UPDATE'")) {
            record("UPDATE history migration: already applied");
            return;
        }
        Set<String> expected = Set.of("CREATE", "STATUS_CHANGE", "ASSIGN", "PRIORITY_CHANGE",
                "PUBLIC_REPLY", "INTERNAL_NOTE", "AI_CLASSIFY", "AI_CLASSIFY_FAILED");
        Set<String> actual = new HashSet<>();
        var values = Pattern.compile("'([A-Z_]+)'").matcher(clause);
        while (values.find()) actual.add(values.group(1));
        if (!actual.equals(expected)) throw new AssertionError("Unexpected history CHECK; migration not applied");
        String migration = Files.readString(Path.of("docs/sql/V2__ticket_update_operation.sql"));
        execute(migration.substring(migration.indexOf("ALTER TABLE")));
        record("UPDATE history migration: applied existing V2 script (no data deleted)");
    }

    private void seedAccounts() throws Exception {
        String hash = new BCryptPasswordEncoder().encode(password);
        for (String name : List.of("owner", "other", "agent", "agent2", "admin")) {
            String role = name.startsWith("agent") ? "AGENT" : name.equals("admin") ? "ADMIN" : "USER";
            long roleId = scalar("SELECT id FROM sys_role WHERE code = ? AND status = 'ACTIVE'", role);
            try (PreparedStatement insert = database.prepareStatement(
                    "INSERT INTO sys_user (username, password_hash, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, prefix + "_" + name);
                insert.setString(2, hash);
                insert.setString(3, "Temporary HTTP test " + name);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new AssertionError("Generated user ID missing");
                    users.put(name, keys.getLong(1));
                }
            }
            execute("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", users.get(name), roleId);
        }
        record("Temporary test accounts: 5 (existing accounts unchanged)");
    }

    private void startApplication() throws Exception {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        Path log = Path.of("target/mysql-http-smoke-app.log");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-jar", "target/smart-ticket-backend-0.0.1-SNAPSHOT.jar",
                "--spring.profiles.active=local", "--server.port=0");
        builder.environment().put("JWT_SECRET", Base64.getEncoder().encodeToString(bytes));
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        application = builder.start();
        for (int attempt = 0; attempt < 160; attempt++) {
            if (!application.isAlive()) throw new AssertionError("Temporary server failed to start; inspect local application log");
            String output = Files.exists(log) ? Files.readString(log) : "";
            var match = Pattern.compile("Tomcat started on port (\\d+)").matcher(output);
            if (match.find()) {
                baseUrl = "http://127.0.0.1:" + match.group(1);
                record("Packaged application: started on independent random port");
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Temporary server startup timed out");
    }

    private void testHttp() throws Exception {
        expect("Service health", request("GET", "/api/health", null, null), 200);
        expect("Real MySQL health", request("GET", "/api/health/db", null, null), 200);
        expect("Unauthenticated query", request("GET", "/api/tickets", null, null), 401);
        expect("Invalid JWT", request("GET", "/api/tickets", "invalid.token.value", null), 401);
        expect("Wrong login password", request("POST", "/api/auth/login", null,
                Map.of("username", prefix + "_owner", "password", "wrong")), 401);

        String owner = login("owner"), other = login("other"), agent = login("agent"), admin = login("admin");
        long main = create(owner, "main");
        long second = create(owner, "page");
        long otherTicket = create(other, "other");
        long assigned = create(owner, "assigned");
        long assignedOtherAgent = create(owner, "assigned2");
        execute("UPDATE ticket SET assignee_id = ?, status = 'ASSIGNED', version = version + 1 WHERE id = ? AND creator_id = ?",
                users.get("agent"), assigned, users.get("owner"));
        execute("UPDATE ticket SET assignee_id = ?, status = 'ASSIGNED', version = version + 1 WHERE id = ? AND creator_id = ?",
                users.get("agent2"), assignedOtherAgent, users.get("owner"));

        JsonNode detail = expect("Owner reads own ticket", request("GET", "/api/tickets/" + main, owner, null), 200).path("data");
        check(detail.path("creatorId").asLong() == users.get("owner"), "Creator comes from authentication");
        check(detail.path("status").asText().equals("NEW") && detail.path("priority").asText().equals("MEDIUM")
                && detail.path("aiStatus").asText().equals("PENDING") && detail.path("category").isNull()
                && detail.path("assigneeId").isNull(), "Server-controlled creation defaults");
        expect("Other user cannot read", request("GET", "/api/tickets/" + main, other, null), 404);
        expect("Owner cannot read other user's ticket", request("GET", "/api/tickets/" + otherTicket, owner, null), 404);
        expect("Admin reads any ticket", request("GET", "/api/tickets/" + main, admin, null), 200);

        JsonNode page = expect("Owner database pagination", request("GET", "/api/tickets?page=1&size=2&keyword=" + prefix, owner, null), 200).path("data");
        check(page.path("total").asLong() == 4 && page.path("records").size() == 2 && page.path("pages").asLong() == 2,
                "Pagination total/size/pages are correct");
        for (JsonNode ticket : page.path("records")) check(ticket.path("creatorId").asLong() == users.get("owner"), "Page excludes other users");
        JsonNode mine = expect("My tickets with forged identity query", request("GET",
                "/api/tickets/my?keyword=" + prefix + "&creatorId=" + users.get("other"), owner, null), 200).path("data");
        check(mine.path("total").asLong() == 4, "Query parameter cannot change creator scope");
        JsonNode filtered = expect("Status filter", request("GET", "/api/tickets?keyword=" + prefix + "&status=NEW", owner, null), 200).path("data");
        check(filtered.path("total").asLong() == 2, "Status filter stays inside permission scope");
        JsonNode all = expect("Admin pagination", request("GET", "/api/tickets?keyword=" + prefix, admin, null), 200).path("data");
        check(all.path("total").asLong() == 5, "Admin sees all five temporary tickets");
        JsonNode agentPage = expect("Agent pagination", request("GET", "/api/tickets?keyword=" + prefix, agent, null), 200).path("data");
        check(agentPage.path("total").asLong() == 1, "Agent sees only assigned tickets");
        expect("Assigned agent reads ticket", request("GET", "/api/tickets/" + assigned, agent, null), 200);
        expect("Agent cannot read unassigned ticket", request("GET", "/api/tickets/" + main, agent, null), 404);
        expect("Agent cannot read another agent's ticket", request("GET", "/api/tickets/" + assignedOtherAgent, agent, null), 404);
        expect("Agent cannot create", request("POST", "/api/tickets", agent, Map.of("title", "forbidden", "description", "test")), 403);

        expect("Forged creator rejected", request("POST", "/api/tickets", owner,
                Map.of("title", "forged", "description", "test", "creatorId", users.get("other"))), 400);
        expect("Blank title rejected", request("POST", "/api/tickets", owner,
                Map.of("title", " ", "description", "test")), 400);
        expect("Oversized title rejected", request("POST", "/api/tickets", owner,
                Map.of("title", "x".repeat(201), "description", "test")), 400);
        expect("Invalid pagination rejected", request("GET", "/api/tickets?page=0&size=101", owner, null), 400);
        expect("Invalid enum rejected", request("GET", "/api/tickets?status=INVALID", owner, null), 400);
        expect("Invalid ID rejected", request("GET", "/api/tickets/0", owner, null), 400);

        execute("UPDATE ticket SET category = 'ORDER', category_reason = 'test classification', ai_status = 'SUCCESS' WHERE id = ? AND creator_id = ?", main, users.get("owner"));
        JsonNode updated = expect("Update editable ticket", request("PUT", "/api/tickets/" + main, owner,
                Map.of("title", prefix + " updated", "description", "new description", "version", 0)), 200).path("data");
        check(updated.path("version").asInt() == 1 && updated.path("category").isNull()
                && updated.path("categoryReason").isNull() && updated.path("aiStatus").asText().equals("PENDING"),
                "Update increments version and resets stale classification");
        expect("Stale version rejected", request("PUT", "/api/tickets/" + main, owner,
                Map.of("title", "stale", "description", "test", "version", 0)), 409);
        expect("Other user cannot update", request("PUT", "/api/tickets/" + main, other,
                Map.of("title", "attack", "description", "test", "version", 1)), 404);
        expect("Assigned ticket cannot be edited", request("PUT", "/api/tickets/" + assigned, owner,
                Map.of("title", "late", "description", "test", "version", 1)), 409);
        expect("Protected update field rejected", request("PUT", "/api/tickets/" + main, owner,
                Map.of("title", "attack", "description", "test", "version", 1, "status", "CLOSED")), 400);

        HttpRequest first = build("PUT", "/api/tickets/" + main, owner,
                Map.of("title", prefix + " concurrent 1", "description", "test", "version", 1));
        HttpRequest secondRequest = build("PUT", "/api/tickets/" + main, owner,
                Map.of("title", prefix + " concurrent 2", "description", "test", "version", 1));
        var firstResult = client.sendAsync(first, HttpResponse.BodyHandlers.ofString());
        var secondResult = client.sendAsync(secondRequest, HttpResponse.BodyHandlers.ofString());
        List<Integer> statuses = new ArrayList<>(List.of(firstResult.get(20, TimeUnit.SECONDS).statusCode(),
                secondResult.get(20, TimeUnit.SECONDS).statusCode()));
        Collections.sort(statuses);
        check(statuses.equals(List.of(200, 409)), "Real MySQL concurrent updates have exactly one winner");
        check(scalar("SELECT version FROM ticket WHERE id = ?", main) == 2, "Concurrent update increments version once");
        check(scalar("SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = ? AND operation_type = 'CREATE'", main) == 1,
                "CREATE history persisted");
        check(scalar("SELECT COUNT(*) FROM ticket_operation_history WHERE ticket_id = ? AND operation_type = 'UPDATE'", main) == 2,
                "Only successful updates have UPDATE history");

        execute("UPDATE sys_user SET status = 'DISABLED' WHERE id = ?", users.get("owner"));
        expect("Disabled user old JWT rejected", request("GET", "/api/tickets", owner, null), 401);
        execute("DELETE FROM sys_user_role WHERE user_id = ?", users.get("agent"));
        expect("Removed roles invalidate old JWT", request("GET", "/api/tickets", agent, null), 401);
    }

    private String login(String name) throws Exception {
        return expect("Login " + name, request("POST", "/api/auth/login", null,
                Map.of("username", prefix + "_" + name, "password", password)), 200)
                .path("data").path("accessToken").asText();
    }

    private long create(String token, String label) throws Exception {
        return expect("Create ticket " + label, request("POST", "/api/tickets", token,
                Map.of("title", prefix + " " + label, "description", "Temporary real MySQL HTTP test")), 201)
                .path("data").path("id").asLong();
    }

    private HttpRequest build(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json; charset=utf-8");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        return client.send(build(method, path, token, body), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode expect(String name, HttpResponse<String> response, int expected) throws Exception {
        check(response.statusCode() == expected, name + " HTTP " + expected + " (actual " + response.statusCode() + ")");
        JsonNode payload = json.readTree(response.body());
        check(payload.has("code") && payload.has("data") && payload.has("timestamp"), name + " unified response");
        return payload;
    }

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
        record("PASS: " + name);
    }

    private long scalar(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new AssertionError("Expected scalar row missing");
                return rows.getLong(1);
            }
        }
    }

    private void execute(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
    }

    private void cleanup() throws SQLException {
        database.setAutoCommit(false);
        try {
            for (var user : users.entrySet()) {
                long id = user.getValue();
                execute("DELETE FROM ticket_message WHERE ticket_id IN (SELECT id FROM ticket WHERE creator_id = ?)", id);
                execute("DELETE FROM ticket_operation_history WHERE ticket_id IN (SELECT id FROM ticket WHERE creator_id = ?)", id);
                execute("DELETE FROM ticket WHERE creator_id = ?", id);
            }
            for (var user : users.entrySet()) {
                execute("DELETE FROM sys_user_role WHERE user_id = ?", user.getValue());
                execute("DELETE FROM sys_user WHERE id = ? AND username = ?", user.getValue(), prefix + "_" + user.getKey());
            }
            database.commit();
            check(scalar("SELECT COUNT(*) FROM sys_user WHERE username LIKE ?", prefix + "%") == 0,
                    "All temporary accounts removed");
            record("Temporary tickets, messages, history and role bindings: cleaned");
        } catch (SQLException | RuntimeException | Error failure) {
            database.rollback();
            throw failure;
        } finally {
            database.setAutoCommit(true);
        }
    }

    private void record(String line) {
        results.add(line);
        System.out.println(line);
    }
}
