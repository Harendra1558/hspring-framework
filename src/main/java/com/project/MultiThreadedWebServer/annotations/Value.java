package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                              @VALUE                                          ║
 * ║              (Property Injection / Configuration Binding)                    ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Injects values from application.properties into bean fields.
 * Similar to Spring's @Value annotation.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHY EXTERNALIZED CONFIGURATION?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Hardcoding values in Java code is BAD:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  public class WebServer {                                                    │
 * │      private int port = 8080;       // Hardcoded! Must recompile to change │
 * │      private int poolSize = 10;     // Hardcoded! What if prod needs 50?   │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Externalized configuration is GOOD:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  # application.properties (easy to change without recompiling)              │
 * │  server.port=9090                                                           │
 * │  server.thread-pool-size=50                                                 │
 * │                                                                             │
 * │  public class WebServer {                                                    │
 * │      @Value("server.port")                                                  │
 * │      private int port;              // Loaded from properties file!         │
 * │                                                                             │
 * │      @Value("server.thread-pool-size")                                      │
 * │      private int poolSize;          // Loaded from properties file!         │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SUPPORTED VALUE TYPES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * The framework automatically converts String property values to:
 *   - String   → Used as-is
 *   - int      → Integer.parseInt()
 *   - long     → Long.parseLong()
 *   - boolean  → Boolean.parseBoolean()
 *   - double   → Double.parseDouble()
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * EXAMPLE
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * application.properties:
 *   app.name=HSpring
 *   app.version=1.0.0
 *   app.max-users=1000
 * 
 * Java class:
 * <pre>
 * @Service
 * public class UserService {
 *     @Value("app.name")
 *     private String appName;
 * 
 *     @Value("app.max-users")
 *     private int maxUsers;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Value {
    /**
     * The property key to look up in application.properties.
     * Example: "server.port", "app.name"
     */
    String value();
}
