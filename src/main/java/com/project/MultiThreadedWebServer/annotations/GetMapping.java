package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for mapping HTTP GET requests to handler methods.
 * Similar to Spring's @GetMapping.
 * 
 * Example usage:
 * <pre>
 * @GetMapping("/users/{id}")
 * public void getUser(HttpRequest request, HttpResponse response) {
 *     // Fetch user logic
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GetMapping {
    /**
     * The URI path pattern for this mapping.
     * Supports path variables like /users/{id}
     */
    String value();
}
