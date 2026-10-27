package com.hspring.routing;

import com.project.MultiThreadedWebServer.annotations.DeleteMapping;
import com.project.MultiThreadedWebServer.annotations.GetMapping;
import com.project.MultiThreadedWebServer.annotations.PostMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.core.RouteResolver;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouteResolverTest {

    @RestController("/api")
    static class SampleController {
        String lastCall;

        // Declared BEFORE the literal route on purpose: ordering must not matter
        @GetMapping("/users/{id}")
        public void byId(HttpRequest request, HttpResponse response) {
            lastCall = "byId:" + request.getPathVariable("id");
        }

        @GetMapping("/users/me")
        public void me(HttpRequest request, HttpResponse response) {
            lastCall = "me";
        }

        @GetMapping("/users/{userId}/posts/{postId}")
        public void post(HttpRequest request, HttpResponse response) {
            lastCall = "post:" + request.getPathVariable("userId") + "/" + request.getPathVariable("postId");
        }

        @PostMapping("/users")
        public void create(HttpResponse response) {
            lastCall = "create";
        }

        @DeleteMapping("/users/{id}")
        public void delete(HttpRequest request, HttpResponse response) {
            throw new NotFoundException("no user " + request.getPathVariable("id"));
        }
    }

    @RestController
    static class BadSignatureController {
        @GetMapping("/bad")
        public void bad(String notSupported) {
        }
    }

    private RouteResolver resolver;
    private SampleController controller;

    @BeforeEach
    void setUp() {
        resolver = new RouteResolver();
        controller = new SampleController();
        resolver.registerController(controller);
    }

    private boolean dispatch(String method, String path) throws IOException {
        String raw = method + " " + path + " HTTP/1.1\r\n\r\n";
        HttpRequest request = HttpRequest.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        return resolver.resolve(request, new HttpResponse(new ByteArrayOutputStream()));
    }

    @Test
    void extractsPathVariables() throws IOException {
        assertTrue(dispatch("GET", "/api/users/42"));
        assertEquals("byId:42", controller.lastCall);

        assertTrue(dispatch("GET", "/api/users/7/posts/99"));
        assertEquals("post:7/99", controller.lastCall);
    }

    @Test
    void decodesPathVariables() throws IOException {
        assertTrue(dispatch("GET", "/api/users/John%20Doe"));
        assertEquals("byId:John Doe", controller.lastCall);
    }

    @Test
    void literalRouteBeatsPathVariable() throws IOException {
        assertTrue(dispatch("GET", "/api/users/me"));
        assertEquals("me", controller.lastCall);
    }

    @Test
    void supportsResponseOnlySignature() throws IOException {
        assertTrue(dispatch("POST", "/api/users"));
        assertEquals("create", controller.lastCall);
    }

    @Test
    void returnsFalseWhenNothingMatches() throws IOException {
        assertFalse(dispatch("GET", "/api/nothing"));
        assertFalse(dispatch("GET", "/api/users/1/extra"));
        assertFalse(dispatch("PATCH", "/api/users/1"));
    }

    @Test
    void reportsAllowedMethodsForKnownPath() {
        assertEquals(Set.of("DELETE", "GET"), resolver.getAllowedMethods("/api/users/1"));
        assertEquals(Set.of("POST"), resolver.getAllowedMethods("/api/users"));
        assertTrue(resolver.getAllowedMethods("/unknown").isEmpty());
    }

    @Test
    void controllerExceptionsAreUnwrapped() {
        // Must arrive as NotFoundException (→ 404), not wrapped in a generic exception (→ 500)
        NotFoundException e = assertThrows(NotFoundException.class, () -> dispatch("DELETE", "/api/users/5"));
        assertEquals("no user 5", e.getMessage());
    }

    @Test
    void unsupportedHandlerSignatureFailsAtStartup() {
        RouteResolver fresh = new RouteResolver();
        assertThrows(IllegalStateException.class, () -> fresh.registerController(new BadSignatureController()));
    }
}
