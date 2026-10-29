package com.project.MultiThreadedWebServer.service;

import com.project.MultiThreadedWebServer.annotations.PostConstruct;
import com.project.MultiThreadedWebServer.annotations.PreDestroy;
import com.project.MultiThreadedWebServer.annotations.Service;
import com.project.MultiThreadedWebServer.annotations.Value;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import com.project.MultiThreadedWebServer.model.User;
import com.project.MultiThreadedWebServer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * UserService demonstrates the Service layer in Spring Boot.
 *
 * CONCEPTS SHOWN HERE:
 *
 * 1. CONSTRUCTOR INJECTION (the recommended style)
 *    1. The dependency can be FINAL → the object is immutable and thread-safe
 *    2. The object can never exist in a half-built state (no null fields)
 *    3. Easy to unit test: just call  new UserService(fakeRepository)
 *    4. Circular dependencies are detected at startup instead of hiding
 *
 *    No @Autowired is needed: the class has exactly one constructor,
 *    so the container uses it automatically (same rule as Spring 4.3+).
 *
 * 2. @Value("property.key") → Injects config from application.properties
 *    Instead of hardcoding "USER", the default role is read from config.
 *
 * 3. @PostConstruct → Called AFTER the bean is created AND all @Value/@Autowired
 *    fields are injected. Safe to use injected values here (unlike the constructor).
 *
 * 4. @PreDestroy → Called when the application shuts down.
 *    Used for cleanup (closing connections, flushing caches, etc.)
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    /**
     * @Value injects from application.properties:
     *   user.default-role=USER
     *
     * This value is NOT available in the constructor!
     * It's injected AFTER construction, which is why we use @PostConstruct below.
     */
    @Value("user.default-role")
    private String defaultRole;

    /**
     * Constructor — called FIRST in the bean lifecycle.
     *
     * IMPORTANT: @Value fields are NULL here!
     * Do NOT try to use defaultRole in the constructor.
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @PostConstruct — called AFTER all @Value and @Autowired injections.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * LIFECYCLE ORDER:
     *   1. new UserService(repo)    → constructor (defaultRole = null)
     *   2. @Value injection         → defaultRole = "USER" (from properties)
     *   3. @PostConstruct           → NOW we can safely use defaultRole! ✓
     * ═══════════════════════════════════════════════════════════════════════════
     */
    @PostConstruct
    public void loadSampleData() {
        logger.info("@PostConstruct: defaultRole is now '{}' (loaded from application.properties)", defaultRole);
        createUser("John Doe", "john@example.com");
        createUser("Jane Smith", "jane@example.com");
        createUser("Bob Johnson", "bob@example.com");
        logger.info("UserService initialized with {} sample users", userRepository.count());
    }

    /**
     * @PreDestroy — called when the application shuts down.
     *
     * Use cases in real apps:
     * - Close database connections
     * - Flush caches to disk
     * - Release file handles
     */
    @PreDestroy
    public void cleanup() {
        logger.info("@PreDestroy: UserService shutting down with {} users in memory", userRepository.count());
    }

    /**
     * Retrieves all users.
     *
     * @param limit maximum number of users to return (null = no limit)
     * @return users ordered by id
     */
    public List<User> getAllUsers(Integer limit) {
        logger.debug("Fetching all users");
        List<User> users = userRepository.findAll();
        if (limit != null && limit < users.size()) {
            return users.subList(0, limit);
        }
        return users;
    }

    /**
     * Retrieves a user by ID.
     *
     * @param id The user ID
     * @return The user data
     * @throws NotFoundException if user not found
     */
    public User getUserById(long id) {
        logger.debug("Fetching user with id: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + id));
    }

    /**
     * Creates a new user with the default role from @Value configuration.
     *
     * @param name  User's name
     * @param email User's email
     * @return The created user with generated ID
     */
    public User createUser(String name, String email) {
        User user = userRepository.create(name, email, defaultRole);
        logger.info("Created user with id: {} and role: {}", user.id(), defaultRole);
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
    public User updateUser(long id, String name, String email) {
        User updated = userRepository.update(id, existing -> {
            User user = existing;
            if (name != null) user = user.withName(name);
            if (email != null) user = user.withEmail(email);
            return user;
        }).orElseThrow(() -> new NotFoundException("User not found with id: " + id));

        logger.info("Updated user with id: {}", id);
        return updated;
    }

    /**
     * Deletes a user.
     *
     * @param id The user ID to delete
     * @throws NotFoundException if user not found
     */
    public void deleteUser(long id) {
        if (!userRepository.deleteById(id)) {
            throw new NotFoundException("User not found with id: " + id);
        }
        logger.info("Deleted user with id: {}", id);
    }

    /**
     * Counts total users.
     */
    public int countUsers() {
        return userRepository.count();
    }
}
