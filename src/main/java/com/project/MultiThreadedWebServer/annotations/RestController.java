package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a REST Controller.
 * Similar to Spring's @RestController.
 * 
 * Classes annotated with @RestController will be:
 * 1. Automatically instantiated by the ApplicationContext
 * 2. Scanned for route mappings (@GetMapping, @PostMapping, etc.)
 * 3. Registered with the RouteResolver
 * 
 * Example:
 * <pre>
 * @RestController
 * public class UserController {
 *     @GetMapping("/users")
 *     public void getUsers(HttpRequest req, HttpResponse res) { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestController {
    /**
     * Optional base path for all routes in this controller.
     * Example: @RestController("/api/v1")
     */
    String value() default "";
}
