package com.project.MultiThreadedWebServer.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.DeleteMapping;
import com.project.MultiThreadedWebServer.annotations.GetMapping;
import com.project.MultiThreadedWebServer.annotations.PostMapping;
import com.project.MultiThreadedWebServer.annotations.PutMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.ResponseEntity;
import com.project.MultiThreadedWebServer.exception.ValidationException;
import com.project.MultiThreadedWebServer.model.User;
import com.project.MultiThreadedWebServer.service.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                           USER CONTROLLER                                    ║
 * ║          (REST API — USES ResponseEntity LIKE REAL SPRING!)                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEFORE (Old Way — Manual response building):
 *   public void getUser(HttpRequest req, HttpResponse resp) {
 *       User user = userService.findById(id);
 *       String json = mapper.writeValueAsString(user);
 *       resp.status(200).json(json);  // Manual!
 *   }
 *
 * AFTER (Spring Way — Return ResponseEntity):
 *   public ResponseEntity<?> getUser(HttpRequest req) {
 *       User user = userService.findById(id);
 *       return ResponseEntity.ok(user);  // Auto-serialized!
 *   }
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Notice:
 *   1. Methods RETURN ResponseEntity — they don't manually build the response
 *   2. Methods take only HttpRequest — no HttpResponse needed!
 *   3. Framework auto-serializes the body object to JSON
 *   4. Status code is embedded in ResponseEntity
 *
 * ERROR HANDLING STYLE:
 * Handlers do NOT catch exceptions and build error responses themselves.
 * They just THROW (ValidationException, NotFoundException, ...) and let the
 * GlobalExceptionHandler turn the exception into the right HTTP status.
 * That's exactly how @ControllerAdvice keeps Spring controllers clean.
 *
 * Each endpoint teaches a DIFFERENT concept:
 *
 * ┌───────────────────────────────────────────────────────────────────────────────┐
 * │  Endpoint                │  Concept Taught                                   │
 * ├───────────────────────────┼─────────────────────────────────────────────────┤
 * │  GET    /api/users       │  Query parameters (?limit=10)                    │
 * │  GET    /api/users/{id}  │  Path Variables + ResponseEntity.ok()            │
 * │  POST   /api/users       │  Request Body + ResponseEntity.created()         │
 * │  PUT    /api/users/{id}  │  Partial update + input validation               │
 * │  DELETE /api/users/{id}  │  Exception flow + ResponseEntity.ok()            │
 * └───────────────────────────────────────────────────────────────────────────────┘
 */
@RestController("/api")
public class UserController {

    /** Deliberately simple email check: something@something.something */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * @Autowired — UserService is automatically injected by the IoC container.
     * (FIELD injection - compare with UserService, which uses CONSTRUCTOR injection)
     */
    @Autowired
    private UserService userService;

    /**
     * @Autowired — ObjectMapper from AppConfig's @Bean method.
     * Used to parse request bodies (deserialization).
     *
     * Note: We still need ObjectMapper for READING request bodies.
     * The framework auto-serializes RESPONSES (via ResponseEntity),
     * but we still manually deserialize REQUEST bodies.
     *
     * In real Spring, @RequestBody does this automatically.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * GET /api/users - List all users
     *
     * Query parameters:
     * - limit: Maximum number of users to return (optional, must be >= 0)
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(HttpRequest request) {
        String limitStr = request.getQueryParam("limit");
        Integer limit = null;
        if (limitStr != null) {
            long requested = parseNumber("limit", limitStr, "limit must be a non-negative number");
            if (requested < 0) {
                throw new ValidationException("limit", "limit must be a non-negative number");
            }
            limit = (int) Math.min(requested, Integer.MAX_VALUE);
        }

        List<User> users = userService.getAllUsers(limit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("count", users.size());
        body.put("data", users);
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/users/{id} — Demonstrates Path Variable + ResponseEntity.
     *
     * The framework reads the ResponseEntity, serializes the body Map to JSON,
     * sets status 200, and sends the response. You just return!
     *
     * Throws NotFoundException (→ 404) if the user does not exist.
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(HttpRequest request) {
        User user = userService.getUserById(parseId(request));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", user);
        return ResponseEntity.ok(body);
    }

    /**
     * POST /api/users — Demonstrates Request Body + ResponseEntity.created().
     *
     * When a client sends:
     *   POST /api/users
     *   Content-Type: application/json
     *   {"name": "John", "email": "john@mail.com"}
     *
     * We:
     * 1. Read the raw body string from HttpRequest
     * 2. Deserialize JSON (in real Spring: @RequestBody does this)
     * 3. Validate required fields
     * 4. Return ResponseEntity.created(newUser)  ← 201 status + Location header
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(HttpRequest request) {
        Map<String, String> userData = readJsonBody(request);
        String name = userData.get("name");
        String email = userData.get("email");

        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "Name is required");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("email", "Valid email is required");
        }

        User newUser = userService.createUser(name.trim(), email.trim());

        // Return 201 Created — NOT 200 OK!
        // This is the correct HTTP status for resource creation.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "User created successfully");
        body.put("data", newUser);
        return ResponseEntity.created(body).header("Location", "/api/users/" + newUser.id());
    }

    /**
     * PUT /api/users/{id} - Update user
     *
     * Request body: {"name": "New Name"} and/or {"email": "new@example.com"}
     * Fields that are missing keep their current value.
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(HttpRequest request) {
        long id = parseId(request);
        Map<String, String> userData = readJsonBody(request);
        String name = userData.get("name");
        String email = userData.get("email");

        if (name == null && email == null) {
            throw new ValidationException("body", "Provide at least one of: name, email");
        }
        if (name != null && name.isBlank()) {
            throw new ValidationException("name", "Name must not be blank");
        }
        if (email != null && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("email", "Valid email is required");
        }

        User updatedUser = userService.updateUser(id,
                name == null ? null : name.trim(),
                email == null ? null : email.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "User updated successfully");
        body.put("data", updatedUser);
        return ResponseEntity.ok(body);
    }

    /**
     * DELETE /api/users/{id} — Demonstrates Exception Flow.
     *
     * If the user doesn't exist, UserService.deleteUser() throws NotFoundException.
     * That exception propagates up to GlobalExceptionHandler (like @ControllerAdvice)
     * which formats a proper 404 JSON error response automatically.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(HttpRequest request) {
        userService.deleteUser(parseId(request));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "User deleted successfully");
        return ResponseEntity.ok(body);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS - in Spring, @PathVariable / @RequestBody / @Valid do this for you
    // ═══════════════════════════════════════════════════════════════════════════

    private long parseId(HttpRequest request) {
        return parseNumber("id", request.getPathVariable("id"), "User ID must be a valid number");
    }

    private long parseNumber(String field, String value, String message) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(field, message);
        }
    }

    /**
     * Parses the request body as a flat JSON object of strings.
     * Empty or malformed bodies become a 400 Validation Error, not a 500.
     */
    private Map<String, String> readJsonBody(HttpRequest request) {
        String body = request.getBody();
        if (body == null || body.isBlank()) {
            throw new ValidationException("body", "Request body is required");
        }
        try {
            Map<String, String> data = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});
            if (data == null) {
                throw new ValidationException("body", "Request body must be a JSON object");
            }
            return data;
        } catch (JsonProcessingException e) {
            throw new ValidationException("body", "Request body must be a JSON object with string fields");
        }
    }
}
