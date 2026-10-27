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
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.exception.ValidationException;
import com.project.MultiThreadedWebServer.model.User;
import com.project.MultiThreadedWebServer.service.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * UserController demonstrates a full RESTful API controller.
 *
 * This showcases:
 * 1. @RestController annotation for auto-discovery
 * 2. @Autowired for dependency injection (FIELD injection - compare with
 *    UserService, which uses CONSTRUCTOR injection)
 * 3. @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
 * 4. Path variables: /users/{id}
 * 5. Query parameters: /users?limit=10
 * 6. Request body parsing
 * 7. JSON responses
 *
 * ERROR HANDLING STYLE:
 * Handlers do NOT catch exceptions and build error responses themselves.
 * They just THROW (ValidationException, NotFoundException, ...) and let the
 * GlobalExceptionHandler turn the exception into the right HTTP status.
 * That's exactly how @ControllerAdvice keeps Spring controllers clean.
 *
 * API Endpoints:
 * - GET    /api/users          - List all users
 * - GET    /api/users/{id}     - Get user by ID
 * - POST   /api/users          - Create new user
 * - PUT    /api/users/{id}     - Update user
 * - DELETE /api/users/{id}     - Delete user
 */
@RestController("/api")
public class UserController {

    /** ObjectMapper is thread-safe once configured, so one shared instance is fine. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Deliberately simple email check: something@something.something */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired
    private UserService userService;

    /**
     * GET /api/users - List all users
     *
     * Query parameters:
     * - limit: Maximum number of users to return (optional, must be >= 0)
     */
    @GetMapping("/users")
    public void getAllUsers(HttpRequest request, HttpResponse response) throws JsonProcessingException {
        // Demonstrate query parameter usage
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

        // Build response
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("count", users.size());
        responseBody.put("data", users);
        response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
    }

    /**
     * GET /api/users/{id} - Get user by ID
     *
     * Throws NotFoundException (→ 404) if the user does not exist.
     */
    @GetMapping("/users/{id}")
    public void getUserById(HttpRequest request, HttpResponse response) throws JsonProcessingException {
        long id = parseId(request);
        User user = userService.getUserById(id);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("data", user);
        response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
    }

    /**
     * POST /api/users - Create new user
     *
     * Request body: {"name": "John", "email": "john@example.com"}
     * Responds 201 Created.
     */
    @PostMapping("/users")
    public void createUser(HttpRequest request, HttpResponse response) throws JsonProcessingException {
        Map<String, String> userData = readJsonBody(request);
        String name = userData.get("name");
        String email = userData.get("email");

        // Validate input
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "Name is required");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("email", "Valid email is required");
        }

        User newUser = userService.createUser(name.trim(), email.trim());

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "User created successfully");
        responseBody.put("data", newUser);
        response.status(HttpResponse.CREATED)
                .header("Location", "/api/users/" + newUser.id())
                .json(objectMapper.writeValueAsString(responseBody));
    }

    /**
     * PUT /api/users/{id} - Update user
     *
     * Request body: {"name": "New Name"} and/or {"email": "new@example.com"}
     * Fields that are missing keep their current value.
     */
    @PutMapping("/users/{id}")
    public void updateUser(HttpRequest request, HttpResponse response) throws JsonProcessingException {
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

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "User updated successfully");
        responseBody.put("data", updatedUser);
        response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
    }

    /**
     * DELETE /api/users/{id} - Delete user
     */
    @DeleteMapping("/users/{id}")
    public void deleteUser(HttpRequest request, HttpResponse response) throws JsonProcessingException {
        long id = parseId(request);
        userService.deleteUser(id);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "User deleted successfully");
        response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
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
