package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for mapping HTTP DELETE requests to handler methods.
 * Similar to Spring's @DeleteMapping.
 * 
 * Example usage:
 * <pre>
 * @DeleteMapping("/users/{id}")
 * public void deleteUser(HttpRequest request, HttpResponse response) {
 *     String id = request.getPathVariable("id");
 *     // Delete user logic
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeleteMapping {
    /**
     * The URI path pattern for this mapping.
     * Supports path variables like /users/{id}
     */
    String value();
}
