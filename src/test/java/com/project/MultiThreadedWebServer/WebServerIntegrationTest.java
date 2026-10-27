package com.project.MultiThreadedWebServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.exception.UnauthorizedException;
import com.project.MultiThreadedWebServer.filter.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Starts the REAL server (component scan, routing, filters, exception handling)
 * on a random free port and talks to it over HTTP.
 */
class WebServerIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static WebServer server;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() {
        server = new WebServer(0, 8);   // port 0 = let the OS choose a free port
        // Demo auth filter: any request carrying "X-Block: yes" is rejected with 401
        server.addFilter(new Filter() {
            @Override
            public boolean preHandle(HttpRequest request, HttpResponse response) {
                if ("yes".equals(request.getHeader("X-Block"))) {
                    throw new UnauthorizedException("Blocked by test filter");
                }
                return true;
            }

            @Override
            public int getOrder() {
                return 10;
            }
        });
        server.start(WebServer.BASE_PACKAGE);
        baseUrl = "http://localhost:" + server.getPort();
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static java.net.http.HttpResponse<String> send(String method, String path, String body, String... headers)
            throws IOException, InterruptedException {
        var builder = java.net.http.HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return client.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode json(java.net.http.HttpResponse<String> response) throws IOException {
        return mapper.readTree(response.body());
    }

    private static long createUser(String name, String email) throws Exception {
        var response = send("POST", "/api/users", mapper.writeValueAsString(
                java.util.Map.of("name", name, "email", email)));
        assertEquals(201, response.statusCode(), response.body());
        return json(response).at("/data/id").asLong();
    }

    /** Sends raw bytes over a socket - for requests HttpClient refuses to build. */
    private static String rawRequest(String raw) throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void homePageAndHealthCheck() throws Exception {
        var home = send("GET", "/", null);
        assertEquals(200, home.statusCode());
        assertTrue(home.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(home.body().contains("HSpring Framework"));

        var health = send("GET", "/health", null);
        assertEquals(200, health.statusCode());
        assertEquals("UP", json(health).get("status").asText());
    }

    @Test
    void listsSeededUsersInIdOrderAndHonoursLimit() throws Exception {
        var response = send("GET", "/api/users?limit=2", null);
        assertEquals(200, response.statusCode());
        JsonNode body = json(response);
        assertTrue(body.get("success").asBoolean());
        assertEquals(2, body.get("count").asInt());
        assertEquals(1, body.at("/data/0/id").asLong());
        assertEquals(2, body.at("/data/1/id").asLong());
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        long id = createUser("Alice", "alice@example.com");

        var created = send("GET", "/api/users/" + id, null);
        assertEquals(200, created.statusCode());
        assertEquals("Alice", json(created).at("/data/name").asText());
        assertTrue(json(created).at("/data/updatedAt").isMissingNode(), "never updated → field omitted");

        var updated = send("PUT", "/api/users/" + id, "{\"name\": \"Alice Updated\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("Alice Updated", json(updated).at("/data/name").asText());
        assertEquals("alice@example.com", json(updated).at("/data/email").asText(), "email kept");
        assertFalse(json(updated).at("/data/updatedAt").isMissingNode());

        assertEquals(200, send("DELETE", "/api/users/" + id, null).statusCode());
        assertEquals(404, send("GET", "/api/users/" + id, null).statusCode());
        assertEquals(404, send("DELETE", "/api/users/" + id, null).statusCode());
    }

    @Test
    void createReturnsLocationHeader() throws Exception {
        var response = send("POST", "/api/users", "{\"name\":\"Loc\",\"email\":\"loc@example.com\"}");
        assertEquals(201, response.statusCode());
        long id = json(response).at("/data/id").asLong();
        assertEquals("/api/users/" + id, response.headers().firstValue("Location").orElse(null));
    }

    @Test
    void unicodeSurvivesTheRoundTrip() throws Exception {
        long id = createUser("Zoë 日本 🚀", "zoe@example.com");
        var response = send("GET", "/api/users/" + id, null);
        assertEquals("Zoë 日本 🚀", json(response).at("/data/name").asText());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // error handling - each of these returned an EMPTY reply before the fix
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unknownUserIs404WithJsonBody() throws Exception {
        var response = send("GET", "/api/users/999999", null);
        assertEquals(404, response.statusCode());
        JsonNode body = json(response);
        assertEquals("Not Found", body.get("error").asText());
        assertEquals("User not found with id: 999999", body.get("message").asText());
        assertEquals("/api/users/999999", body.get("path").asText());
    }

    @Test
    void validationErrorsAre400WithField() throws Exception {
        var missingEmail = send("POST", "/api/users", "{\"name\": \"No Email\"}");
        assertEquals(400, missingEmail.statusCode());
        assertEquals("Validation Error", json(missingEmail).get("error").asText());
        assertEquals("email", json(missingEmail).get("field").asText());

        var badId = send("GET", "/api/users/abc", null);
        assertEquals(400, badId.statusCode());
        assertEquals("id", json(badId).get("field").asText());

        var badLimit = send("GET", "/api/users?limit=-1", null);
        assertEquals(400, badLimit.statusCode());
        assertEquals("limit", json(badLimit).get("field").asText());
    }

    @Test
    void malformedOrEmptyJsonIs400() throws Exception {
        assertEquals(400, send("POST", "/api/users", "{not json").statusCode());
        assertEquals(400, send("PUT", "/api/users/1", "").statusCode());
        assertEquals(400, send("PUT", "/api/users/1", "{}").statusCode());
    }

    @Test
    void unknownPathIs404() throws Exception {
        var response = send("GET", "/nonexistent", null);
        assertEquals(404, response.statusCode());
        assertEquals("Not Found", json(response).get("error").asText());
        assertEquals("/nonexistent", json(response).get("path").asText());
    }

    @Test
    void wrongMethodIs405WithAllowHeader() throws Exception {
        var response = send("DELETE", "/api/users", null);
        assertEquals(405, response.statusCode());
        assertEquals("GET, POST", response.headers().firstValue("Allow").orElse(null));
        assertEquals("Method Not Allowed", json(response).get("error").asText());
        assertEquals("/api/users", json(response).get("path").asText());
    }

    @Test
    void filterExceptionBecomes401() throws Exception {
        var response = send("GET", "/health", null, "X-Block", "yes");
        assertEquals(401, response.statusCode());
        assertEquals("Blocked by test filter", json(response).get("message").asText());
    }

    @Test
    void malformedHttpRequestGets400() throws Exception {
        String reply = rawRequest("NONSENSE\r\n\r\n");
        assertTrue(reply.startsWith("HTTP/1.1 400 Bad Request"), reply);
    }

    @Test
    void lowercaseHeadersAreUnderstood() throws Exception {
        String body = "{\"name\":\"lower\",\"email\":\"lower@example.com\"}";
        String reply = rawRequest("POST /api/users HTTP/1.1\r\nhost: localhost\r\ncontent-type: application/json\r\n"
                + "content-length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
        assertTrue(reply.startsWith("HTTP/1.1 201 Created"), reply);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // concurrency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void concurrentCreatesGetUniqueIds() throws Exception {
        int requests = 40;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                int n = i;
                tasks.add(() -> createUser("Concurrent " + n, "c" + n + "@example.com"));
            }
            Set<Long> ids = new HashSet<>();
            for (Future<Long> future : pool.invokeAll(tasks)) {
                ids.add(future.get());
            }
            assertEquals(requests, ids.size(), "every concurrent create must get its own id");
        } finally {
            pool.shutdownNow();
        }
    }
}
