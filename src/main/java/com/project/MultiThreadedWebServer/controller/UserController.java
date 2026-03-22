package com.project.MultiThreadedWebServer.controller;

import com.project.MultiThreadedWebServer.annotations.GetMapping;
import com.project.MultiThreadedWebServer.annotations.PostMapping;
import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.DeleteMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.ResponseEntity;
import com.project.MultiThreadedWebServer.exception.ValidationException;
import com.project.MultiThreadedWebServer.model.User;
import com.project.MultiThreadedWebServer.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                           USER CONTROLLER                                    ║
 * ║          (REST API — NOW USES ResponseEntity LIKE REAL SPRING!)              ║
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
 *   1. Method RETURNS ResponseEntity — doesn't manually build response
 *   2. Method takes only HttpRequest — no HttpResponse needed!
 *   3. Framework auto-serializes the body object to JSON
 *   4. Status code is embedded in ResponseEntity
 * 
 * Each endpoint teaches a DIFFERENT concept:
 * 
 * ┌───────────────────────────────────────────────────────────────────────────────┐
 * │  Endpoint                │  Concept Taught                                   │
 * ├───────────────────────────┼─────────────────────────────────────────────────┤
 * │  GET  /api/users/{id}    │  Path Variables + ResponseEntity.ok()            │
 * │  POST /api/users         │  Request Body + ResponseEntity.created()         │
 * │  DELETE /api/users/{id}  │  Exception flow + ResponseEntity.ok()            │
 * └───────────────────────────────────────────────────────────────────────────────┘
 */
@RestController("/api")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    /**
     * @Autowired — UserService is automatically injected by the IoC container.
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
     * GET /api/users/{id} — Demonstrates Path Variable + ResponseEntity.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * COMPARE: Old way vs Spring way
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * OLD WAY:
     *   public void getUser(HttpRequest req, HttpResponse resp) {
     *       User user = service.getUserById(id);
     *       Map<String, Object> body = Map.of("success", true, "data", user);
     *       resp.status(200).json(mapper.writeValueAsString(body));
     *   }
     * 
     * SPRING WAY (what we use now):
     *   public ResponseEntity<?> getUser(HttpRequest request) {
     *       User user = service.getUserById(id);
     *       return ResponseEntity.ok(Map.of("success", true, "data", user));
     *   }
     * 
     * The framework reads the ResponseEntity, serializes the body Map to JSON,
     * sets status 200, and sends the response. You just return!
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(HttpRequest request) {
        String idStr = request.getPathVariable("id");
        Long id = Long.parseLong(idStr);

        User user = userService.getUserById(id);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", user);

        return ResponseEntity.ok(body);
    }

    /**
     * POST /api/users — Demonstrates Request Body + ResponseEntity.created().
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * CONCEPT: REQUEST BODY PARSING + 201 CREATED
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * When a client sends:
     *   POST /api/users
     *   Content-Type: application/json
     *   {"name": "John", "email": "john@mail.com"}
     * 
     * We:
     * 1. Read the raw body string from HttpRequest
     * 2. Deserialize JSON → User object (in real Spring: @RequestBody does this)
     * 3. Validate required fields
     * 4. Return ResponseEntity.created(newUser)  ← 201 status!
     * 
     * Note: ResponseEntity.created() sets status 201 instead of 200.
     * In real Spring, 201 means "a new resource was created."
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(HttpRequest request) throws Exception {
        // Read raw JSON body from request
        String body = request.getBody();
        if (body == null || body.isEmpty()) {
            throw new ValidationException("body", "Request body is required");
        }

        // Deserialize JSON into User object
        // In real Spring, @RequestBody User userData does this automatically!
        User userData = objectMapper.readValue(body, User.class);

        // Validate required fields
        if (userData.getName() == null || userData.getName().trim().isEmpty()) {
            throw new ValidationException("name", "Name is required");
        }
        if (userData.getEmail() == null || !userData.getEmail().contains("@")) {
            throw new ValidationException("email", "Valid email is required");
        }

        // Create user via service
        User newUser = userService.createUser(userData.getName().trim(), userData.getEmail().trim());

        // Return 201 Created — NOT 200 OK!
        // This is the correct HTTP status for resource creation.
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "User created successfully");
        responseBody.put("data", newUser);

        return ResponseEntity.created(responseBody);
    }

    /**
     * DELETE /api/users/{id} — Demonstrates Exception Flow.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * CONCEPT: EXCEPTION HANDLING + CLEAN CONTROLLER
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * If the user doesn't exist, UserService.deleteUser() throws NotFoundException.
     * That exception propagates up to GlobalExceptionHandler (like @ControllerAdvice)
     * which formats a proper 404 JSON error response automatically.
     * 
     * Notice how clean the controller code is:
     * - No try/catch needed for business exceptions
     * - The framework handles error formatting
     * - Controller only contains the "happy path"
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(HttpRequest request) {
        String idStr = request.getPathVariable("id");
        Long id = Long.parseLong(idStr);

        // May throw NotFoundException → handled by GlobalExceptionHandler
        userService.deleteUser(id);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "User deleted successfully");

        return ResponseEntity.ok(body);
    }
}
