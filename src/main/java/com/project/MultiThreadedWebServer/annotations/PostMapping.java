package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for mapping HTTP POST requests to handler methods.
 * Similar to Spring's @PostMapping.
 * 
 * Example usage:
 * <pre>
 * @PostMapping("/users")
 * public void createUser(HttpRequest request, HttpResponse response) {
 *     // Create user from request.getBody()
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostMapping {
    /**
     * The URI path pattern for this mapping.
     * Supports path variables like /users/{id}
     */
    String value();
}
