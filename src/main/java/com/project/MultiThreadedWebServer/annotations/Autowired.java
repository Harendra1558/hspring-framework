package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic dependency injection.
 * Can be applied to fields or constructors.
 * 
 * Similar to Spring's @Autowired.
 * 
 * Example:
 * <pre>
 * @RestController
 * public class UserController {
 *     
 *     @Autowired
 *     private UserService userService;
 *     
 *     @GetMapping("/users/{id}")
 *     public void getUser(HttpRequest req, HttpResponse res) {
 *         User user = userService.findById(req.getPathVariable("id"));
 *         // ...
 *     }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Autowired {
    /**
     * Whether the dependency is required. If true and the dependency
     * cannot be resolved, an exception will be thrown.
     */
    boolean required() default true;
}
