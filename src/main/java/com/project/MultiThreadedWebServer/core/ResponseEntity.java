package com.project.MultiThreadedWebServer.core;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                          RESPONSE ENTITY                                     ║
 * ║              (Spring's ResponseEntity\u003cT\u003e Equivalent)                         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * ResponseEntity wraps a response body + HTTP status code together.
 * 
 * In real Spring Boot, controllers RETURN ResponseEntity objects:
 * 
 *   @GetMapping("/users/{id}")
 *   public ResponseEntity\u003cUser\u003e getUser(@PathVariable Long id) {
 *       User user = userService.findById(id);
 *       return ResponseEntity.ok(user);           // 200 + user object
 *   }
 * 
 *   @PostMapping("/users")
 *   public ResponseEntity\u003cUser\u003e createUser(@RequestBody User user) {
 *       User saved = userService.save(user);
 *       return ResponseEntity.status(201).body(saved);  // 201 + saved user
 *   }
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHY RESPONSEE ENTITY?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Without ResponseEntity (old way):
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  public void getUser(HttpRequest req, HttpResponse res) {                   │
 * │      User user = service.findById(id);                                      │
 * │      String json = mapper.writeValueAsString(user);                         │
 * │      res.status(200).json(json);   // Manual serialization!               │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * With ResponseEntity (Spring way):
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  public ResponseEntity\u003cUser\u003e getUser(...) {                                  │
 * │      User user = service.findById(id);                                      │
 * │      return ResponseEntity.ok(user);  // Framework handles the rest!       │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Benefits:
 *   1. Cleaner controller code — no manual JSON conversion
 *   2. Framework auto-serializes the body to JSON
 *   3. Status code and body are coupled together
 *   4. Static factory methods make intent clear (ok, created, notFound, etc.)
 * 
 * @param <T> The type of the response body
 */
public class ResponseEntity<T> {

    private final T body;
    private final int statusCode;

    /**
     * Private constructor — use static factory methods instead.
     */
    private ResponseEntity(T body, int statusCode) {
        this.body = body;
        this.statusCode = statusCode;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC FACTORY METHODS (Fluent API — just like real Spring!)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 200 OK with a response body.
     * 
     * Usage: return ResponseEntity.ok(user);
     */
    public static <T> ResponseEntity<T> ok(T body) {
        return new ResponseEntity<>(body, 200);
    }

    /**
     * 201 Created with a response body.
     * 
     * Usage: return ResponseEntity.created(newUser);
     */
    public static <T> ResponseEntity<T> created(T body) {
        return new ResponseEntity<>(body, 201);
    }

    /**
     * 204 No Content (no body).
     * 
     * Usage: return ResponseEntity.noContent();
     */
    public static <T> ResponseEntity<T> noContent() {
        return new ResponseEntity<>(null, 204);
    }

    /**
     * 400 Bad Request with error body.
     * 
     * Usage: return ResponseEntity.badRequest(errorMap);
     */
    public static <T> ResponseEntity<T> badRequest(T body) {
        return new ResponseEntity<>(body, 400);
    }

    /**
     * 404 Not Found with error body.
     * 
     * Usage: return ResponseEntity.notFound("User not found");
     */
    public static <T> ResponseEntity<T> notFound(T body) {
        return new ResponseEntity<>(body, 404);
    }

    /**
     * Custom status code with a body.
     * 
     * Usage: return ResponseEntity.status(202).body(result);
     */
    public static StatusBuilder status(int statusCode) {
        return new StatusBuilder(statusCode);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS BUILDER (for custom status codes)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builder for ResponseEntity with a custom status.
     * 
     * This allows the fluent syntax:
     *   ResponseEntity.status(201).body(user)
     *   ResponseEntity.status(202).body(result)
     */
    public static class StatusBuilder {
        private final int statusCode;

        StatusBuilder(int statusCode) {
            this.statusCode = statusCode;
        }

        /**
         * Sets the response body for the given status.
         */
        public <T> ResponseEntity<T> body(T body) {
            return new ResponseEntity<>(body, statusCode);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the response body (will be auto-serialized to JSON by the framework). */
    public T getBody() {
        return body;
    }

    /** Returns the HTTP status code. */
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "ResponseEntity{statusCode=" + statusCode + ", body=" + body + "}";
    }
}
