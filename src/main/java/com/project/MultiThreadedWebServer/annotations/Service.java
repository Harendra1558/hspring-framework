package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a Service layer component.
 * This is a specialized @Component for business logic classes.
 * 
 * Similar to Spring's @Service.
 * 
 * Example:
 * <pre>
 * @Service
 * public class UserService {
 *     public User findById(Long id) { ... }
 *     public void save(User user) { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {
    /**
     * Optional bean name.
     */
    String value() default "";
}
