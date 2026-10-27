package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-arg method to run when the ApplicationContext shuts down.
 * Similar to jakarta.annotation.PreDestroy.
 * 
 * Beans are destroyed in REVERSE creation order, so a bean is cleaned up
 * before the beans it depends on are.
 * 
 * Example usage:
 * <pre>
 * @PreDestroy
 * public void closeConnections() {
 *     connectionPool.close();
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreDestroy {
}
