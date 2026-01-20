package com.project.MultiThreadedWebServer.service;

import com.project.MultiThreadedWebServer.annotations.Service;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UserService is a sample Service layer component.
 * Demonstrates the @Service annotation and how services are injected into controllers.
 * 
 * In Spring Boot, services contain business logic and are typically
 * injected into controllers via @Autowired.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // In-memory "database" for demonstration
    private final Map<Long, Map<String, Object>> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public UserService() {
        // Pre-populate with sample data
        createUser("John Doe", "john@example.com");
        createUser("Jane Smith", "jane@example.com");
        createUser("Bob Johnson", "bob@example.com");
        logger.info("UserService initialized with {} sample users", users.size());
    }

    /**
     * Retrieves all users.
     * 
     * @return Map of all users
     */
    public Map<Long, Map<String, Object>> getAllUsers() {
        logger.debug("Fetching all users");
        return new HashMap<>(users);
    }

    /**
     * Retrieves a user by ID.
     * 
     * @param id The user ID
     * @return The user data
     * @throws NotFoundException if user not found
     */
    public Map<String, Object> getUserById(Long id) {
        logger.debug("Fetching user with id: {}", id);
        Map<String, Object> user = users.get(id);
        if (user == null) {
            throw new NotFoundException("User not found with id: " + id);
        }
        return new HashMap<>(user);
    }

    /**
     * Creates a new user.
     * 
     * @param name  User's name
     * @param email User's email
     * @return The created user with generated ID
     */
    public Map<String, Object> createUser(String name, String email) {
        Long id = idGenerator.getAndIncrement();
        Map<String, Object> user = new HashMap<>();
        user.put("id", id);
        user.put("name", name);
        user.put("email", email);
        user.put("createdAt", System.currentTimeMillis());
        
        users.put(id, user);
        logger.info("Created user with id: {}", id);
        return user;
    }

    /**
     * Updates an existing user.
     * 
     * @param id    The user ID to update
     * @param name  New name (or null to keep existing)
     * @param email New email (or null to keep existing)
     * @return The updated user
     * @throws NotFoundException if user not found
     */
    public Map<String, Object> updateUser(Long id, String name, String email) {
        Map<String, Object> user = users.get(id);
        if (user == null) {
            throw new NotFoundException("User not found with id: " + id);
        }
        
        if (name != null) user.put("name", name);
        if (email != null) user.put("email", email);
        user.put("updatedAt", System.currentTimeMillis());
        
        logger.info("Updated user with id: {}", id);
        return new HashMap<>(user);
    }

    /**
     * Deletes a user.
     * 
     * @param id The user ID to delete
     * @throws NotFoundException if user not found
     */
    public void deleteUser(Long id) {
        if (!users.containsKey(id)) {
            throw new NotFoundException("User not found with id: " + id);
        }
        users.remove(id);
        logger.info("Deleted user with id: {}", id);
    }

    /**
     * Counts total users.
     */
    public int countUsers() {
        return users.size();
    }
}
