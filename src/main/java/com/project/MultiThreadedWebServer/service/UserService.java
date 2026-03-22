package com.project.MultiThreadedWebServer.service;

import com.project.MultiThreadedWebServer.annotations.PostConstruct;
import com.project.MultiThreadedWebServer.annotations.PreDestroy;
import com.project.MultiThreadedWebServer.annotations.Service;
import com.project.MultiThreadedWebServer.annotations.Value;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import com.project.MultiThreadedWebServer.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UserService demonstrates the Service layer in Spring Boot.
 * 
 * NEW CONCEPTS SHOWN HERE:
 * 
 * 1. @Value("property.key") → Injects config from application.properties
 *    Instead of hardcoding "USER", the default role is read from config.
 * 
 * 2. @PostConstruct → Called AFTER the bean is created AND all @Value/@Autowired
 *    fields are injected. Safe to use injected values here (unlike the constructor).
 * 
 * 3. @PreDestroy → Called when the application shuts down.
 *    Used for cleanup (closing connections, flushing caches, etc.)
 * 
 * 4. User model → Uses a proper POJO instead of Map<String, Object>
 *    Type-safe, IDE-friendly, and compiler-checked.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // In-memory "database" — now stores User objects instead of raw Maps
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

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
    public UserService() {
        // Don't initialize data here. Use @PostConstruct instead,
        // because @Value and @Autowired fields are not yet available.
        logger.info("UserService constructor called (defaultRole is still null here)");
    }

    /**
     * @PostConstruct — called AFTER all @Value and @Autowired injections.
     * 
     * This is the safe place to perform initialization that depends on
     * injected values. The defaultRole field is now populated from
     * application.properties.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * LIFECYCLE ORDER:
     *   1. new UserService()        → constructor (defaultRole = null)
     *   2. @Value injection         → defaultRole = "USER" (from properties)
     *   3. @PostConstruct init()    → NOW we can safely use defaultRole! ✓
     * ═══════════════════════════════════════════════════════════════════════════
     */
    @PostConstruct
    public void init() {
        logger.info("@PostConstruct: defaultRole is now '{}' (loaded from application.properties)", defaultRole);
        
        // Pre-populate sample data — now we can safely use defaultRole
        createUser("John Doe", "john@example.com");
        createUser("Jane Smith", "jane@example.com");
        createUser("Bob Johnson", "bob@example.com");
        
        logger.info("UserService initialized with {} sample users", users.size());
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
        logger.info("@PreDestroy: UserService cleaning up {} users from memory", users.size());
        users.clear();
    }

    /**
     * Retrieves a user by ID.
     */
    public User getUserById(Long id) {
        logger.debug("Fetching user with id: {}", id);
        User user = users.get(id);
        if (user == null) {
            throw new NotFoundException("User not found with id: " + id);
        }
        return user;
    }

    /**
     * Retrieves all users.
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * Creates a new user with the default role from @Value configuration.
     */
    public User createUser(String name, String email) {
        Long id = idGenerator.getAndIncrement();
        User user = new User(id, name, email);
        user.setRole(defaultRole);  // Uses the @Value injected config!
        users.put(id, user);
        logger.info("Created user: {} with role: {}", name, defaultRole);
        return user;
    }

    /**
     * Deletes a user.
     */
    public void deleteUser(Long id) {
        if (!users.containsKey(id)) {
            throw new NotFoundException("User not found with id: " + id);
        }
        users.remove(id);
        logger.info("Deleted user with id: {}", id);
    }
}
