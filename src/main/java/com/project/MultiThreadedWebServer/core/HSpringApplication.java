package com.project.MultiThreadedWebServer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.WebServer;
import com.project.MultiThreadedWebServer.annotations.SpringBootApplication;
import com.project.MultiThreadedWebServer.exception.GlobalExceptionHandler;
import com.project.MultiThreadedWebServer.filter.FilterChain;
import com.project.MultiThreadedWebServer.filter.LoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

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
 * │      ├─ 3. Load configuration + print the banner                            │
 * │      ├─ 4. Create ApplicationContext (IoC container)                        │
 * │      │      ├─ Component scanning                                           │
 * │      │      ├─ Bean creation (constructors + @Bean methods)                 │
 * │      │      ├─ @Value injection                                             │
 * │      │      ├─ @Autowired injection                                         │
 * │      │      └─ @PostConstruct callbacks                                     │
 * │      ├─ 5. Create DispatcherServlet (route resolver)                        │
 * │      ├─ 6. Create filter chain                                              │
 * │      ├─ 7. Create exception handler                                         │
 * │      ├─ 8. Create embedded Tomcat (just the server)                         │
 * │      ├─ 9. Register shutdown hook (@PreDestroy)                             │
 * │      └─ 10. Start Tomcat → listening for connections                        │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * In HSpring, HSpringApplication.run() follows the EXACT SAME order!
 *
 * Like SpringApplication.run(), it RETURNS once the server is listening.
 * The JVM stays alive because the server's acceptor thread is not a daemon
 * thread - exactly how Tomcat keeps a Spring Boot app running after main() ends.
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

    /** The IoC container */
    private final ApplicationContext applicationContext;

    /** The embedded web server */
    private final WebServer webServer;

    private boolean stopped = false;

    private HSpringApplication(ApplicationContext applicationContext, WebServer webServer) {
        this.applicationContext = applicationContext;
        this.webServer = webServer;
    }

    /**
     * Starts the HSpring application — exactly like SpringApplication.run().
     *
     * This is the SINGLE method that orchestrates the entire startup:
     * annotation reading → IoC → routes → filters → server → listening
     *
     * @param primarySource The class annotated with @SpringBootApplication
     * @param args          Command-line arguments, e.g. --server.port=9090
     * @return the running application (use stop() to shut it down)
     */
    public static HSpringApplication run(Class<?> primarySource, String... args) {
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
        // STEP 2: Determine the base packages
        //
        // If scanBasePackages is specified → use them
        // Otherwise → use the main class's package (the standard behavior!)
        //
        // This is WHY your main class should be in the ROOT package:
        //   com.myapp/                 ← @SpringBootApplication is here
        //   com.myapp.controller/     ← ✓ scanned (sub-package)
        //   com.myapp.service/        ← ✓ scanned (sub-package)
        //   com.other/                ← ✗ NOT scanned (different root)
        // ─────────────────────────────────────────────────────────────────────────
        String[] basePackages = annotation.scanBasePackages();
        if (basePackages.length > 0) {
            logger.info("Using explicit scanBasePackages: {}", String.join(", ", basePackages));
        } else {
            basePackages = new String[]{primarySource.getPackage().getName()};
            logger.info("Auto-detected base package from main class: {}", basePackages[0]);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 3: Load configuration and print the startup banner
        //
        // --key=value arguments override application.properties, like Spring Boot.
        // In real Spring Boot, the banner is the ASCII art you see in the console.
        // You can customize it with banner.txt in resources.
        // ─────────────────────────────────────────────────────────────────────────
        PropertiesLoader properties = new PropertiesLoader(parseArguments(args));
        printBanner(properties.getProperty("app.version", ""));
        logger.info("Starting HSpring application: {}", primarySource.getSimpleName());

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 4: Create ApplicationContext (IoC Container)
        //
        // This triggers the full bean lifecycle:
        //   Component scanning → Bean creation → @Value → @Autowired → @PostConstruct
        //
        // Scanning works both from target/classes AND from inside a JAR file,
        // so no manual bean registration is needed.
        // ─────────────────────────────────────────────────────────────────────────
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 4: Initializing IoC Container (ApplicationContext)");
        logger.info("═══════════════════════════════════════════════════════════════");
        ApplicationContext applicationContext = new ApplicationContext(properties, basePackages);

        try {
            // ─────────────────────────────────────────────────────────────────────
            // STEP 5: Create Route Resolver (like DispatcherServlet + HandlerMapping)
            //
            // Wire the ObjectMapper from IoC into RouteResolver.
            // This enables ResponseEntity auto-serialization (Object → JSON).
            // In real Spring, this is HttpMessageConverter infrastructure.
            // ─────────────────────────────────────────────────────────────────────
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

            // ─────────────────────────────────────────────────────────────────────
            // STEP 6: Create Filter Chain
            //
            // Filters run BEFORE and AFTER every request.
            // In real Spring: HandlerInterceptor / Filter / OncePerRequestFilter
            // Add more with app.getWebServer().addFilter(...)
            // ─────────────────────────────────────────────────────────────────────
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("STEP 6: Initializing Filter Chain");
            logger.info("═══════════════════════════════════════════════════════════════");
            FilterChain filterChain = new FilterChain();
            filterChain.addFilter(new LoggingFilter());

            // ─────────────────────────────────────────────────────────────────────
            // STEP 7: Create Exception Handler (like @ControllerAdvice)
            // ─────────────────────────────────────────────────────────────────────
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("STEP 7: Initializing Exception Handler (@ControllerAdvice)");
            logger.info("═══════════════════════════════════════════════════════════════");
            GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

            // ─────────────────────────────────────────────────────────────────────
            // STEP 8: Create the Embedded Web Server (like Tomcat)
            //
            // Notice: We pass everything the server needs from the OUTSIDE.
            // The WebServer class has NO knowledge of IoC, annotations, or scanning.
            // It's just a TCP server that receives HTTP requests.
            // ─────────────────────────────────────────────────────────────────────
            int port = parseIntProperty(properties, "server.port", 8080);
            int threadPoolSize = parseIntProperty(properties, "server.thread-pool-size", 10);
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("STEP 8: Creating Embedded Web Server (port: {}, threads: {})", port, threadPoolSize);
            logger.info("═══════════════════════════════════════════════════════════════");
            WebServer webServer = new WebServer(routeResolver, filterChain, exceptionHandler, port, threadPoolSize);
            HSpringApplication application = new HSpringApplication(applicationContext, webServer);

            // ─────────────────────────────────────────────────────────────────────
            // STEP 9: Register JVM Shutdown Hook
            //
            // When you press CTRL+C (or the JVM shuts down):
            //   1. Stop the web server (close socket, finish in-flight requests)
            //   2. Close ApplicationContext → calls @PreDestroy on all beans
            //
            // This ensures graceful shutdown — resources are released properly.
            // ─────────────────────────────────────────────────────────────────────
            Runtime.getRuntime().addShutdownHook(new Thread(application::stop, "hspring-shutdown"));

            // ─────────────────────────────────────────────────────────────────────
            // STEP 10: Start the server (returns once the port is bound)
            // ─────────────────────────────────────────────────────────────────────
            webServer.start();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("HSpring started in {} ms on port {}", elapsed, webServer.getPort());
            logger.info("═══════════════════════════════════════════════════════════════");
            return application;

        } catch (RuntimeException e) {
            // Startup failed after the container was built: still run @PreDestroy
            applicationContext.close();
            throw e;
        }
    }

    /**
     * Stops the application gracefully:
     *   1. Stop the web server (in-flight requests may finish)
     *   2. Close the ApplicationContext → @PreDestroy callbacks
     *
     * Safe to call more than once (e.g. by a test AND the shutdown hook).
     */
    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        logger.info("Stopping HSpring application...");
        webServer.stop();
        applicationContext.close();  // Calls @PreDestroy on all beans
        logger.info("Application stopped gracefully");
    }

    /**
     * Parses Spring Boot style arguments: --server.port=9090 → {"server.port": "9090"}.
     * Anything else is ignored with a warning.
     */
    static Map<String, String> parseArguments(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (arg.startsWith("--") && equals > 2) {
                result.put(arg.substring(2, equals), arg.substring(equals + 1));
            } else {
                logger.warn("Ignoring argument '{}' - use --key=value, e.g. --server.port=9090", arg);
            }
        }
        return result;
    }

    private static int parseIntProperty(PropertiesLoader properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Property '" + key + "' must be a number, but was '" + value + "'");
        }
    }

    /**
     * Prints a fancy startup banner.
     */
    private static void printBanner(String version) {
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
                ║   Version: %-10s                                     ║
                ║                                                           ║
                ╚═══════════════════════════════════════════════════════════╝
                """.formatted(version);
        System.out.println(banner);
    }

    /** Gets the ApplicationContext (IoC Container) — for testing or programmatic access. */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /** Gets the embedded web server, e.g. to add filters or read the actual port. */
    public WebServer getWebServer() {
        return webServer;
    }
}
