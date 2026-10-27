package com.project.MultiThreadedWebServer.core;

/**
 * Thrown when the IoC container cannot build the application's beans.
 * Similar to Spring's BeanCreationException.
 * 
 * Typical causes:
 *   - A required dependency has no matching bean (missing @Service?)
 *   - Circular constructor dependency: A → B → A
 *   - Two beans match an injection point and the container can't choose
 *   - A constructor or @PostConstruct method threw an exception
 * 
 * The container FAILS FAST: startup aborts with this exception instead of
 * starting a half-wired application that breaks on the first request.
 */
public class BeanCreationException extends RuntimeException {

    public BeanCreationException(String message) {
        super(message);
    }

    public BeanCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
