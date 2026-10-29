package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                           @CONFIGURATION                                     ║
 * ║              (Java-based Bean Definition Class)                              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Marks a class as a configuration class that contains @Bean factory methods.
 * Similar to Spring's @Configuration.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * TWO WAYS TO DEFINE BEANS IN SPRING
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WAY 1: Component Scanning (what you already know)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Service                                                                    │
 * │  public class UserService {  // Auto-discovered and registered as a bean   │
 * │      ...                                                                    │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * WAY 2: @Configuration + @Bean (what THIS annotation enables)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Configuration                                                              │
 * │  public class AppConfig {                                                    │
 * │                                                                              │
 * │      @Bean                                                                   │
 * │      public ObjectMapper objectMapper() {                                    │
 * │          ObjectMapper mapper = new ObjectMapper();                           │
 * │          mapper.enable(SerializationFeature.INDENT_OUTPUT);                  │
 * │          return mapper;  // This specific instance becomes a bean           │
 * │      }                                                                       │
 * │  }                                                                           │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHEN TO USE @CONFIGURATION + @BEAN vs @COMPONENT?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Use @Component/@Service when:
 *   - You OWN the class (it's YOUR code)
 *   - Simple instantiation (no special setup needed)
 * 
 * Use @Configuration + @Bean when:
 *   - You DON'T own the class (third-party library like ObjectMapper)
 *   - You need custom initialization logic
 *   - You want to conditionally create beans
 *   - You want to create multiple beans of the same type
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Configuration {
}
