package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a Spring-like Component.
 * Classes annotated with @Component will be automatically
 * instantiated and managed by the ApplicationContext (IoC Container).
 * 
 * Similar to Spring's @Component.
 * 
 * Example:
 * <pre>
 * @Component
 * public class EmailService {
 *     public void sendEmail(String to, String message) { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {
    /**
     * Optional bean name. If not specified, the class name
     * (with first letter lowercase) will be used.
     */
    String value() default "";
}
