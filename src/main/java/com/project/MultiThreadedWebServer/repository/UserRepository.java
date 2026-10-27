package com.project.MultiThreadedWebServer.repository;

import com.project.MultiThreadedWebServer.annotations.Component;
import com.project.MultiThreadedWebServer.model.User;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/**
 * In-memory, thread-safe storage for users - the DATA ACCESS layer.
 * Plays the role of a Spring Data repository (@Repository) backed by a database.
 *
 * LAYERED ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  UserController   → HTTP: parse request, validate input, build response    │
 * │       ↓                                                                     │
 * │  UserService      → Business rules                                         │
 * │       ↓                                                                     │
 * │  UserRepository   → Storage (here: a ConcurrentHashMap; normally a DB)     │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * THREAD SAFETY:
 *   - ConcurrentHashMap: many threads can read/write without external locks
 *   - AtomicLong: two concurrent creates can never get the same id
 *   - Every operation below is a SINGLE atomic map call. Two-step logic like
 *     "if (map.containsKey(id)) map.remove(id)" is a race condition
 *     (check-then-act): another thread can act between the two steps.
 */
@Component
public class UserRepository {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /** Creates and stores a new user with a freshly generated id. */
    public User create(String name, String email) {
        long id = idGenerator.getAndIncrement();
        User user = new User(id, name, email, System.currentTimeMillis(), null);
        users.put(id, user);
        return user;
    }

    public Optional<User> findById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    /** All users, ordered by id. */
    public List<User> findAll() {
        return users.values().stream()
                .sorted(Comparator.comparing(User::id))
                .toList();
    }

    /**
     * Atomically replaces a user with an updated copy.
     * computeIfPresent runs the update function while holding the lock for that key,
     * so two concurrent updates of the same user can't overwrite each other.
     *
     * @return the updated user, or empty if no user has this id
     */
    public Optional<User> update(long id, UnaryOperator<User> updater) {
        return Optional.ofNullable(users.computeIfPresent(id, (key, existing) -> updater.apply(existing)));
    }

    /**
     * Deletes a user in ONE atomic step: remove() returns the removed value,
     * or null if there was nothing to remove.
     *
     * @return true if a user was deleted
     */
    public boolean deleteById(long id) {
        return users.remove(id) != null;
    }

    public int count() {
        return users.size();
    }
}
