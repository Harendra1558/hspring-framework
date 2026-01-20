package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for mapping HTTP PUT requests to handler methods.
 * Similar to Spring's @PutMapping.
 * 
 * Example usage:
 * <pre>
 * @PutMapping("/users/{id}")
 * public void updateUser(HttpRequest request, HttpResponse response) {
 *     String id = request.getPathVariable("id");
 *     // Update user logic
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PutMapping {
    /**
     * The URI path pattern for this mapping.
     * Supports path variables like /users/{id}
     */
    String value();
}
