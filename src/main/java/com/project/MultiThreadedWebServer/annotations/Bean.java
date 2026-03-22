package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                                @BEAN                                         ║
 * ║              (Factory Method for Bean Creation)                              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Marks a method inside a @Configuration class as a bean factory.
 * The method's return value becomes a managed bean in the IoC container.
 * Similar to Spring's @Bean.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HOW @BEAN WORKS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Configuration                                                              │
 * │  public class AppConfig {                                                    │
 * │                                                                              │
 * │      @Bean                                                                   │
 * │      public ObjectMapper objectMapper() {       ← Method name = bean name  │
 * │          ObjectMapper mapper = new ObjectMapper();   ← You control creation │
 * │          mapper.enable(INDENT_OUTPUT);                ← Custom setup!       │
 * │          return mapper;                              ← Return value = bean  │
 * │      }                                                                       │
 * │  }                                                                           │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * After processing, the container has:
 *   beans = { ObjectMapper.class → the configured ObjectMapper instance }
 * 
 * Now any class can inject it:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Service                                                                    │
 * │  public class UserService {                                                  │
 * │      @Autowired                                                              │
 * │      private ObjectMapper objectMapper;  // Gets the configured instance!   │
 * │  }                                                                           │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * REAL-WORLD USES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * - DataSource: Configure database connection pool
 * - RestTemplate/WebClient: Configure HTTP client
 * - ObjectMapper: Configure JSON serialization settings
 * - PasswordEncoder: Choose BCrypt, SHA-256, etc.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bean {
    /**
     * Optional bean name. If not specified, the method name is used.
     */
    String value() default "";
}
