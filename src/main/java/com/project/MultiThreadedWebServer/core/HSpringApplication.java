package com.project.MultiThreadedWebServer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.WebServer;
import com.project.MultiThreadedWebServer.annotations.SpringBootApplication;
import com.project.MultiThreadedWebServer.exception.GlobalExceptionHandler;
import com.project.MultiThreadedWebServer.filter.FilterChain;
import com.project.MultiThreadedWebServer.filter.LoggingFilter;
import com.project.MultiThreadedWebServer.controller.HomeController;
import com.project.MultiThreadedWebServer.controller.UserController;
import com.project.MultiThreadedWebServer.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                       HSPRING APPLICATION                                    ║
 * ║           (Equivalent to SpringApplication — The Orchestrator)              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class is the HEART of the bootstrap process.
 * It does EVERYTHING that happens between calling run() and your server
 * being ready to accept requests.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * REAL SPRING BOOT STARTUP FLOW
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * In real Spring Boot, SpringApplication.run() does:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  SpringApplication.run(MyApp.class, args)                                   │
 * │      │                                                                      │
 * │      ├─ 1. Read @SpringBootApplication                                      │
 * │      ├─ 2. Determine base package                                           │
 * │      ├─ 3. Print the banner                                                 │
 * │      ├─ 4. Create ApplicationContext (IoC container)                        │
 * │      │      ├─ Component scanning                                           │
 * │      │      ├─ Bean creation                                                │
 * │      │      ├─ @Value injection                                             │
 * │      │      ├─ @Autowired injection                                         │
 * │      │      └─ @PostConstruct callbacks                                     │
 * │      ├─ 5. Create DispatcherServlet (route resolver)                        │
 * │      ├─ 6. Create filter chain                                              │
 * │      ├─ 7. Create embedded Tomcat (just the server)                         │
 * │      ├─ 8. Register shutdown hook (@PreDestroy)                             │
 * │      └─ 9. Start Tomcat → listening for connections                         │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * In HSpring, HSpringApplication.run() follows the EXACT SAME order!
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY INSIGHT: SEPARATION OF CONCERNS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Notice how responsibilities are split:
 * 
 *   Application.java        → Just @SpringBootApplication + main()
 *   HSpringApplication      → Orchestrates the entire startup (this class)
 *   ApplicationContext       → IoC container (bean creation, DI)
 *   WebServer                → Embedded HTTP server (like Tomcat)
 * 
 * The WebServer ONLY handles TCP connections and HTTP parsing.
 * It does NOT:
 *   - Create beans
 *   - Set up routes
 *   - Know about @SpringBootApplication
 *   - Have a main() method
 * 
 * Just like Tomcat doesn't know about your Spring beans!
 */
public class HSpringApplication {

    private static final Logger logger = LoggerFactory.getLogger(HSpringApplication.class);

    /** The IoC container — available after run() completes */
    private static ApplicationContext applicationContext;

    /** The embedded web server — available after run() completes */
    private static WebServer webServer;

    /**
     * Starts the HSpring application — exactly like SpringApplication.run().
     * 
     * This is the SINGLE method that orchestrates the entire startup:
     * annotation reading → IoC → routes → filters → server → listening
     * 
     * @param primarySource The class annotated with @SpringBootApplication
     * @param args          Command-line arguments
     */
    public static void run(Class<?> primarySource, String[] args) {
        long startTime = System.currentTimeMillis();

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1: Validate @SpringBootApplication
        //
        // The main class MUST have @SpringBootApplication.
        // Without it, we don't know what to scan or configure.
        // ─────────────────────────────────────────────────────────────────────────
        if (!primarySource.isAnnotationPresent(SpringBootApplication.class)) {
            throw new IllegalStateException(
                "Class " + primarySource.getName() + " is not annotated with @SpringBootApplication!\n" +
                "Add @SpringBootApplication to your main class."
            );
        }

        SpringBootApplication annotation = primarySource.getAnnotation(SpringBootApplication.class);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 2: Determine the base package
        //
        // If scanBasePackages is specified → use it
        // Otherwise → use the main class's package (the standard behavior!)
        //
        // This is WHY your main class should be in the ROOT package:
        //   com.myapp/                 ← @SpringBootApplication is here
        //   com.myapp.controller/     ← ✓ scanned (sub-package)
        //   com.myapp.service/        ← ✓ scanned (sub-package)
        //   com.other/                ← ✗ NOT scanned (different root)
        // ─────────────────────────────────────────────────────────────────────────
        String basePackage;
        String[] scanPackages = annotation.scanBasePackages();

        if (scanPackages.length > 0) {
            basePackage = scanPackages[0];
            logger.info("Using explicit scanBasePackages: {}", basePackage);
        } else {
            basePackage = primarySource.getPackage().getName();
            logger.info("Auto-detected base package from main class: {}", basePackage);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 3: Print the startup banner
        //
        // In real Spring Boot, this is the ASCII art you see in the console.
        // You can customize it with banner.txt in resources.
        // ─────────────────────────────────────────────────────────────────────────
        printBanner();
        logger.info("Starting HSpring application: {}", primarySource.getSimpleName());

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 4: Create ApplicationContext (IoC Container)
        //
        // This triggers the full bean lifecycle:
        //   Component scanning → Bean creation → @Value → @Autowired → @PostConstruct
        // ─────────────────────────────────────────────────────────────────────────
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 4: Initializing IoC Container (ApplicationContext)");
        logger.info("═══════════════════════════════════════════════════════════════");
        applicationContext = new ApplicationContext(basePackage);

        // Fallback: If no controllers found (JAR mode), register manually
        if (applicationContext.getControllers().isEmpty()) {
            logger.info("No controllers found via scanning, registering manually...");
            registerComponentsManually();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 5: Create Route Resolver (like DispatcherServlet + HandlerMapping)
        //
        // Wire the ObjectMapper from IoC into RouteResolver.
        // This enables ResponseEntity auto-serialization (Object → JSON).
        // In real Spring, this is HttpMessageConverter infrastructure.
        // ─────────────────────────────────────────────────────────────────────────
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 5: Initializing Route Resolver (DispatcherServlet)");
        logger.info("═══════════════════════════════════════════════════════════════");
        RouteResolver routeResolver = new RouteResolver();

        // Wire the @Bean-created ObjectMapper for ResponseEntity auto-serialization
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        if (objectMapper != null) {
            routeResolver.setObjectMapper(objectMapper);
            logger.info("  → Wired ObjectMapper into RouteResolver for auto-serialization");
        }

        for (Object controller : applicationContext.getControllers()) {
            routeResolver.registerController(controller);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 6: Create Filter Chain
        //
        // Filters run BEFORE and AFTER every request.
        // In real Spring: HandlerInterceptor / Filter / OncePerRequestFilter
        // ─────────────────────────────────────────────────────────────────────────
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 6: Initializing Filter Chain");
        logger.info("═══════════════════════════════════════════════════════════════");
        FilterChain filterChain = new FilterChain();
        filterChain.addFilter(new LoggingFilter());

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 7: Create Exception Handler (like @ControllerAdvice)
        // ─────────────────────────────────────────────────────────────────────────
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 7: Initializing Exception Handler (@ControllerAdvice)");
        logger.info("═══════════════════════════════════════════════════════════════");
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 8: Read server config from application.properties
        // ─────────────────────────────────────────────────────────────────────────
        String portStr = applicationContext.getPropertiesLoader().getProperty("server.port", "8080");
        String poolStr = applicationContext.getPropertiesLoader().getProperty("server.thread-pool-size", "10");
        int port = Integer.parseInt(portStr);
        int threadPoolSize = Integer.parseInt(poolStr);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 9: Create the Embedded Web Server (like Tomcat)
        //
        // Notice: We pass everything the server needs from the OUTSIDE.
        // The WebServer class has NO knowledge of IoC, annotations, or scanning.
        // It's just a TCP server that receives HTTP requests.
        // ─────────────────────────────────────────────────────────────────────────
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 9: Creating Embedded Web Server (port: {}, threads: {})", port, threadPoolSize);
        logger.info("═══════════════════════════════════════════════════════════════");
        webServer = new WebServer(routeResolver, filterChain, exceptionHandler, port, threadPoolSize);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 10: Register JVM Shutdown Hook
        //
        // When you press CTRL+C (or the JVM shuts down):
        //   1. Stop the web server (close socket, shutdown thread pool)
        //   2. Close ApplicationContext → calls @PreDestroy on all beans
        //
        // This ensures graceful shutdown — resources are released properly.
        // ─────────────────────────────────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered...");
            webServer.stop();
            applicationContext.close();  // Calls @PreDestroy on all beans
            logger.info("Application stopped gracefully");
        }));

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("HSpring started in {} ms on port {}", elapsed, port);
        logger.info("═══════════════════════════════════════════════════════════════");

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 11: Start the server (blocking call — enters accept loop)
        //
        // This is the last line. The thread blocks here, listening for
        // incoming HTTP connections until shutdown.
        // ─────────────────────────────────────────────────────────────────────────
        webServer.start();
    }

    /**
     * Prints the startup banner — like Spring Boot's banner.txt
     */
    private static void printBanner() {
        String banner = """
                
                ╔═══════════════════════════════════════════════════════════╗
                ║                                                           ║
                ║   ██╗  ██╗███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗   ║
                ║   ██║  ██║██╔════╝██╔══██╗██╔══██╗██║████╗  ██║██╔════╝   ║
                ║   ███████║███████╗██████╔╝██████╔╝██║██╔██╗ ██║██║  ███╗  ║
                ║   ██╔══██║╚════██║██╔═══╝ ██╔══██╗██║██║╚██╗██║██║   ██║  ║
                ║   ██║  ██║███████║██║     ██║  ██║██║██║ ╚████║╚██████╔╝  ║
                ║   ╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝   ║
                ║                                                           ║
                ║   HSpring Framework - by Harendra                         ║
                ║   Learn Spring Boot Internals                             ║
                ║   Version: 1.0.0                                          ║
                ║                                                           ║
                ╚═══════════════════════════════════════════════════════════╝
                """;
        System.out.println(banner);
    }

    /**
     * Manually registers components when classpath scanning fails (JAR mode).
     * 
     * When running from a JAR, the classpath scanning may not find .class
     * files. This fallback registers the known components manually.
     */
    private static void registerComponentsManually() {
        logger.info("Registering components manually for JAR deployment...");

        UserService userService = new UserService();
        applicationContext.registerBean(UserService.class, userService);

        HomeController homeController = new HomeController();
        applicationContext.registerBean(HomeController.class, homeController);

        UserController userController = new UserController();
        try {
            java.lang.reflect.Field userServiceField = UserController.class.getDeclaredField("userService");
            userServiceField.setAccessible(true);
            userServiceField.set(userController, userService);
        } catch (Exception e) {
            logger.error("Failed to inject UserService into UserController", e);
        }
        applicationContext.registerBean(UserController.class, userController);
    }

    /** Gets the ApplicationContext (IoC Container) — for testing or programmatic access. */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
