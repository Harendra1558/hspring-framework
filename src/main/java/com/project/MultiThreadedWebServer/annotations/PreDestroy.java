package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                            @PREDESTROY                                       ║
 * ║                    (Bean Lifecycle - Cleanup)                                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Marks a method to be called BEFORE the bean is destroyed (application shutdown).
 * Similar to javax.annotation.PreDestroy.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHEN IS @PREDESTROY CALLED?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * It's called when the ApplicationContext is being shut down:
 * 
 *   Application Running...
 *         │
 *         ▼
 *   context.close() called (or JVM shutdown hook)
 *         │
 *         ▼
 *   For each bean with @PreDestroy:
 *     → Call the annotated method
 *         │
 *         ▼
 *   Beans cleared from container
 *         │
 *         ▼
 *   Application terminated
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * USE CASES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. Close database connections
 * 2. Flush caches to disk
 * 3. Release file handles
 * 4. Send shutdown notifications
 * 5. Stop background threads
 * 
 * Example:
 * <pre>
 * @Service
 * public class DatabaseService {
 *     private Connection connection;
 * 
 *     @PostConstruct
 *     public void connect() {
 *         connection = DriverManager.getConnection(url);
 *     }
 * 
 *     @PreDestroy
 *     public void disconnect() {
 *         connection.close();  // Clean up when app shuts down
 *     }
 * }
 * </pre>
 * 
 * RULES:
 * - The method must take no arguments
 * - The method must return void
 * - Only ONE method per class should have @PreDestroy
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreDestroy {
}
