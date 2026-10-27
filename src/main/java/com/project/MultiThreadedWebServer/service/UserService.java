package com.project.MultiThreadedWebServer.service;

import com.project.MultiThreadedWebServer.annotations.PostConstruct;
import com.project.MultiThreadedWebServer.annotations.Service;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import com.project.MultiThreadedWebServer.model.User;
import com.project.MultiThreadedWebServer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * UserService is a sample Service layer component.
 * Demonstrates the @Service annotation, CONSTRUCTOR INJECTION and @PostConstruct.
 *
 * In Spring Boot, services contain business logic and are typically
 * injected into controllers. Constructor injection is the recommended style:
 *
 *   1. The dependency can be FINAL → the object is immutable and thread-safe
 *   2. The object can never exist in a half-built state (no null fields)
 *   3. Easy to unit test: just call  new UserService(fakeRepository)
 *   4. Circular dependencies are detected at startup instead of hiding
 *
 * No @Autowired is needed: the class has exactly one constructor,
 * so the container uses it automatically (same rule as Spring 4.3+).
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Pre-populates the store with sample data.
     * Runs once after the container has injected all dependencies.
     */
    @PostConstruct
    public void loadSampleData() {
        createUser("John Doe", "john@example.com");
        createUser("Jane Smith", "jane@example.com");
        createUser("Bob Johnson", "bob@example.com");
        logger.info("UserService initialized with {} sample users", userRepository.count());
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
     * Creates a new user.
     *
     * @param name  User's name
     * @param email User's email
     * @return The created user with generated ID
     */
    public User createUser(String name, String email) {
        User user = userRepository.create(name, email);
        logger.info("Created user with id: {}", user.id());
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
