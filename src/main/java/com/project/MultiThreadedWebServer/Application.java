package com.project.MultiThreadedWebServer;

import com.project.MultiThreadedWebServer.annotations.SpringBootApplication;
import com.project.MultiThreadedWebServer.core.HSpringApplication;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                           APPLICATION                                        ║
 * ║              (Your Spring Boot Main Class)                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This is the ENTRY POINT of the application — exactly like every Spring Boot app.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * IN REAL SPRING BOOT
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 *   @SpringBootApplication
 *   public class MyApplication {
 *       public static void main(String[] args) {
 *           SpringApplication.run(MyApplication.class, args);
 *       }
 *   }
 * 
 * That's it! ONE annotation + ONE line of code.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT @SPRINGBOOTAPPLICATION DOES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * @SpringBootApplication is a META-ANNOTATION (3-in-1):
 * 
 *   @Configuration          → This class can define @Bean methods
 *   @EnableAutoConfiguration → Auto-configure based on classpath
 *   @ComponentScan          → Scan this package + sub-packages for beans
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHY THE MAIN CLASS MUST BE IN THE ROOT PACKAGE
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * @ComponentScan scans from the annotated class's package DOWNWARD:
 * 
 *   com.project.MultiThreadedWebServer/           ← Application.java is HERE
 *   com.project.MultiThreadedWebServer.controller/ ← ✓ scanned
 *   com.project.MultiThreadedWebServer.service/    ← ✓ scanned
 *   com.project.MultiThreadedWebServer.core/       ← ✓ scanned
 *   com.other.package/                             ← ✗ NOT scanned
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT HAPPENS WHEN YOU CALL HSpringApplication.run()
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. Validates @SpringBootApplication is present
 * 2. Auto-detects base package from this class's package
 * 3. Prints the startup banner
 * 4. Creates ApplicationContext (IoC container)
 *    → Component scanning
 *    → Bean creation
 *    → @Value injection
 *    → @Autowired injection
 *    → @PostConstruct callbacks
 * 5. Sets up Route Resolver (URL → controller mapping)
 * 6. Sets up Filter Chain
 * 7. Sets up Exception Handler
 * 8. Creates the embedded WebServer (like Tomcat)
 * 9. Starts listening for HTTP connections
 * 
 * Notice: The WebServer is just the HTTP server — it does NOT contain
 * application logic, IoC, or routing. That's all in HSpringApplication.
 * Just like Tomcat is separate from your Spring Boot application code!
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        HSpringApplication.run(Application.class, args);
    }
}
