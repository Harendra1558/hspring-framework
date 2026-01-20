package com.project.MultiThreadedWebServer.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.GetMapping;
import com.project.MultiThreadedWebServer.PostMapping;
import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.DeleteMapping;
import com.project.MultiThreadedWebServer.annotations.PutMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.exception.ValidationException;
import com.project.MultiThreadedWebServer.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * UserController demonstrates a full RESTful API controller.
 * 
 * This showcases:
 * 1. @RestController annotation for auto-discovery
 * 2. @Autowired for dependency injection
 * 3. @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
 * 4. Path variables: /users/{id}
 * 5. Query parameters: /users?limit=10
 * 6. Request body parsing
 * 7. JSON responses
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

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserService userService;

    /**
     * GET /api/users - List all users
     * 
     * Query parameters:
     * - limit: Maximum number of users to return (optional)
     */
    @GetMapping("/users")
    public void getAllUsers(HttpRequest request, HttpResponse response) {
        try {
            // Demonstrate query parameter usage
            String limitStr = request.getQueryParam("limit");
            
            Map<Long, Map<String, Object>> users = userService.getAllUsers();
            
            // Apply limit if specified
            if (limitStr != null) {
                int limit = Integer.parseInt(limitStr);
                Map<Long, Map<String, Object>> limited = new HashMap<>();
                int count = 0;
                for (Map.Entry<Long, Map<String, Object>> entry : users.entrySet()) {
                    if (count >= limit) break;
                    limited.put(entry.getKey(), entry.getValue());
                    count++;
                }
                users = limited;
            }

            // Build response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("count", users.size());
            responseBody.put("data", users.values());

            response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
            
        } catch (Exception e) {
            logger.error("Error fetching users", e);
            response.sendError(HttpResponse.INTERNAL_SERVER_ERROR, "Failed to fetch users");
        }
    }

    /**
     * GET /api/users/{id} - Get user by ID
     * 
     * Path variables:
     * - id: The user ID
     */
    @GetMapping("/users/{id}")
    public void getUserById(HttpRequest request, HttpResponse response) {
        try {
            // Extract path variable
            String idStr = request.getPathVariable("id");
            Long id = Long.parseLong(idStr);

            // Fetch user (throws NotFoundException if not found)
            Map<String, Object> user = userService.getUserById(id);

            // Build response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("data", user);

            response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
            
        } catch (NumberFormatException e) {
            throw new ValidationException("id", "User ID must be a valid number");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * POST /api/users - Create new user
     * 
     * Request body (JSON):
     * {
     *   "name": "John Doe",
     *   "email": "john@example.com"
     * }
     */
    @PostMapping("/users")
    public void createUser(HttpRequest request, HttpResponse response) {
        try {
            // Parse request body
            String body = request.getBody();
            if (body == null || body.isEmpty()) {
                throw new ValidationException("body", "Request body is required");
            }

            Map<String, String> userData = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});

            // Validate required fields
            String name = userData.get("name");
            String email = userData.get("email");
            
            if (name == null || name.trim().isEmpty()) {
                throw new ValidationException("name", "Name is required");
            }
            if (email == null || !email.contains("@")) {
                throw new ValidationException("email", "Valid email is required");
            }

            // Create user
            Map<String, Object> newUser = userService.createUser(name.trim(), email.trim());

            // Build response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "User created successfully");
            responseBody.put("data", newUser);

            response.status(HttpResponse.CREATED).json(objectMapper.writeValueAsString(responseBody));
            
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error creating user", e);
            response.sendError(HttpResponse.BAD_REQUEST, "Invalid request body");
        }
    }

    /**
     * PUT /api/users/{id} - Update user
     * 
     * Path variables:
     * - id: The user ID
     * 
     * Request body (JSON):
     * {
     *   "name": "Updated Name",
     *   "email": "updated@example.com"
     * }
     */
    @PutMapping("/users/{id}")
    public void updateUser(HttpRequest request, HttpResponse response) {
        try {
            // Extract path variable
            String idStr = request.getPathVariable("id");
            Long id = Long.parseLong(idStr);

            // Parse request body
            String body = request.getBody();
            Map<String, String> userData = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});

            String name = userData.get("name");
            String email = userData.get("email");

            // Validate email if provided
            if (email != null && !email.contains("@")) {
                throw new ValidationException("email", "Valid email is required");
            }

            // Update user
            Map<String, Object> updatedUser = userService.updateUser(id, name, email);

            // Build response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "User updated successfully");
            responseBody.put("data", updatedUser);

            response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
            
        } catch (NumberFormatException e) {
            throw new ValidationException("id", "User ID must be a valid number");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * DELETE /api/users/{id} - Delete user
     * 
     * Path variables:
     * - id: The user ID
     */
    @DeleteMapping("/users/{id}")
    public void deleteUser(HttpRequest request, HttpResponse response) {
        try {
            // Extract path variable
            String idStr = request.getPathVariable("id");
            Long id = Long.parseLong(idStr);

            // Delete user
            userService.deleteUser(id);

            // Build response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "User deleted successfully");

            response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(responseBody));
            
        } catch (NumberFormatException e) {
            throw new ValidationException("id", "User ID must be a valid number");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
