package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-arg method to run once, right after the bean's dependencies
 * have been injected. Similar to jakarta.annotation.PostConstruct.
 * 
 * Why not do this work in the constructor? With FIELD injection, @Autowired
 * fields are still null while the constructor runs. By the time
 * @PostConstruct runs, every dependency is in place.
 * 
 * Example usage:
 * <pre>
 * @PostConstruct
 * public void warmUpCache() {
 *     cache.putAll(repository.loadAll());
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostConstruct {
}
