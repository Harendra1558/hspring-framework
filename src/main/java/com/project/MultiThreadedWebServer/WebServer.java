package com.project.MultiThreadedWebServer;

import com.project.MultiThreadedWebServer.core.ApplicationContext;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.core.RouteResolver;
import com.project.MultiThreadedWebServer.exception.GlobalExceptionHandler;
import com.project.MultiThreadedWebServer.filter.Filter;
import com.project.MultiThreadedWebServer.filter.FilterChain;
import com.project.MultiThreadedWebServer.filter.LoggingFilter;
import com.project.MultiThreadedWebServer.controller.HomeController;
import com.project.MultiThreadedWebServer.controller.UserController;
import com.project.MultiThreadedWebServer.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                         HSPRING WEB SERVER                                   ║
 * ║              Harendra's Spring Boot Implementation                           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * HSpring is a lightweight implementation of Spring Boot internals, created by
 * Harendra to demonstrate how the Spring Framework works under the hood.
 * 
 * This class demonstrates how Spring Boot works internally:
 * 
 * 1. APPLICATION CONTEXT (IoC Container)
 *    - Scans for @Component, @Service, @RestController annotations
 *    - Creates and manages bean instances
 *    - Performs dependency injection via @Autowired
 * 
 * 2. ROUTE RESOLVER (DispatcherServlet + HandlerMapping)
 *    - Maps URL patterns to controller methods
 *    - Supports path variables: /users/{id}
 *    - Handles GET, POST, PUT, DELETE methods
 * 
 * 3. FILTER CHAIN (Interceptors)
 *    - Pre-processing: logging, authentication, rate limiting
 *    - Post-processing: response modification, cleanup
 * 
 * 4. EXCEPTION HANDLER (ControllerAdvice)
 *    - Centralized exception handling
 *    - Consistent error responses
 * 
 * 5. HTTP REQUEST/RESPONSE WRAPPERS
 *    - Parsed request with easy access to headers, body, params
 *    - Fluent response builder with status, headers, body
 * 
 * LIFECYCLE OF A REQUEST:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  Client Request                                                     │
 * │       ↓                                                             │
 * │  ServerSocket.accept() → New Socket Connection                     │
 * │       ↓                                                             │
 * │  ThreadPool → Allocate Worker Thread                               │
 * │       ↓                                                             │
 * │  Parse HTTP → Create HttpRequest object                            │
 * │       ↓                                                             │
 * │  FilterChain.preHandle() → Logging, Auth, etc.                     │
 * │       ↓                                                             │
 * │  RouteResolver.resolve() → Find matching route                     │
 * │       ↓                                                             │
 * │  Controller Method → Business logic execution                       │
 * │       ↓                                                             │
 * │  FilterChain.postHandle() → Post-processing                        │
 * │       ↓                                                             │
 * │  HttpResponse.send() → Write response to socket                    │
 * │       ↓                                                             │
 * │  Socket.close() → Connection closed                                │
 * └─────────────────────────────────────────────────────────────────────┘
 */
public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    // Server configuration
    private final int port;
    private final int threadPoolSize;

    // Core components (similar to Spring's internal components)
    private ApplicationContext applicationContext;
    private RouteResolver routeResolver;
    private FilterChain filterChain;
    private GlobalExceptionHandler exceptionHandler;

    // Server state
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    /**
     * Creates a WebServer with default configuration.
     */
    public WebServer() {
        this(8080, 10);
    }

    /**
     * Creates a WebServer with custom configuration.
     * 
     * @param port           Port to listen on
     * @param threadPoolSize Number of worker threads
     */
    public WebServer(int port, int threadPoolSize) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Main entry point - starts the web server.
     * Similar to SpringApplication.run()
     */
    public static void main(String[] args) {
        WebServer server = new WebServer();
        server.run("com.project.MultiThreadedWebServer");
    }

    /**
     * Initializes and starts the web server.
     * This is the main bootstrap method, similar to SpringApplication.run().
     * 
     * @param basePackage The base package to scan for components
     */
    public void run(String basePackage) {
        long startTime = System.currentTimeMillis();
        
        printBanner();
        
        logger.info("Starting HSpring WebServer...");
        
        // Step 1: Initialize the IoC Container (ApplicationContext)
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 1: Initializing IoC Container (ApplicationContext)");
        logger.info("═══════════════════════════════════════════════════════════════");
        applicationContext = new ApplicationContext(basePackage);
        
        // Fallback: If no controllers found (JAR mode), register manually
        if (applicationContext.getControllers().isEmpty()) {
            logger.info("No controllers found via scanning, registering manually...");
            registerComponentsManually();
        }

        // Step 2: Initialize Route Resolver and register controllers
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 2: Initializing Route Resolver");
        logger.info("═══════════════════════════════════════════════════════════════");
        routeResolver = new RouteResolver();
        for (Object controller : applicationContext.getControllers()) {
            routeResolver.registerController(controller);
        }

        // Step 3: Initialize Filter Chain
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 3: Initializing Filter Chain");
        logger.info("═══════════════════════════════════════════════════════════════");
        filterChain = new FilterChain();
        filterChain.addFilter(new LoggingFilter());
        // Add more filters here: authFilter, rateLimitFilter, etc.

        // Step 4: Initialize Exception Handler
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 4: Initializing Exception Handler");
        logger.info("═══════════════════════════════════════════════════════════════");
        exceptionHandler = new GlobalExceptionHandler();

        // Step 5: Create Thread Pool
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 5: Creating Thread Pool (size: {})", threadPoolSize);
        logger.info("═══════════════════════════════════════════════════════════════");
        threadPool = Executors.newFixedThreadPool(threadPoolSize);

        // Step 6: Start the server
        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("HSpring WebServer started in {} ms on port {}", elapsed, port);
        logger.info("═══════════════════════════════════════════════════════════════");
        
        startServer();
    }

    /**
     * Prints a fancy startup banner.
     */
    private void printBanner() {
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
     * Manually registers components when classpath scanning fails (e.g., in JAR mode).
     * 
     * This is a fallback for when the application runs from a JAR file,
     * where file-based scanning doesn't work because classes are inside the archive.
     */
    private void registerComponentsManually() {
        logger.info("Registering components manually for JAR deployment...");
        
        // Register services first (they have no dependencies)
        UserService userService = new UserService();
        applicationContext.registerBean(UserService.class, userService);
        logger.info("  ✓ Registered @Service: UserService");
        
        // Register controllers (may depend on services)
        HomeController homeController = new HomeController();
        applicationContext.registerBean(HomeController.class, homeController);
        logger.info("  ✓ Registered @RestController: HomeController");
        
        UserController userController = new UserController();
        // Manually inject UserService into UserController
        try {
            java.lang.reflect.Field userServiceField = UserController.class.getDeclaredField("userService");
            userServiceField.setAccessible(true);
            userServiceField.set(userController, userService);
            logger.info("  → Injected UserService into UserController.userService");
        } catch (Exception e) {
            logger.error("Failed to inject UserService into UserController", e);
        }
        applicationContext.registerBean(UserController.class, userController);
        logger.info("  ✓ Registered @RestController: UserController");
    }

    /**
     * Starts the server socket and accepts connections.
     */
    private void startServer() {
        running = true;
        
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(0); // No timeout for accept()
            
            logger.info("Server listening on http://localhost:{}", port);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleRequest(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        logger.error("Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start server on port {}", port, e);
        }
    }

    /**
     * Handles a single HTTP request.
     * This method demonstrates the complete request lifecycle.
     * 
     * @param clientSocket The client socket connection
     */
    private void handleRequest(Socket clientSocket) {
        HttpRequest request = null;
        HttpResponse response = null;
        Exception occuredException = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Parse the request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            // Create HttpRequest object (parses headers and body)
            request = new HttpRequest(requestLine, reader);
            response = new HttpResponse(writer);

            // Apply pre-handle filters
            if (!filterChain.applyPreHandle(request, response)) {
                // Filter short-circuited the request
                return;
            }

            // Route the request to the appropriate controller
            boolean routeFound = routeResolver.resolve(request, response);

            if (!routeFound) {
                response.status(HttpResponse.NOT_FOUND)
                        .json("{\"error\": \"Not Found\", \"message\": \"No handler found for " + 
                              request.getMethod() + " " + request.getPath() + "\", \"status\": 404}");
            }

            // Apply post-handle filters
            filterChain.applyPostHandle(request, response);

        } catch (Exception e) {
            occuredException = e;
            logger.error("Error handling request", e);
            
            // Use exception handler if response not yet sent
            if (response != null && !response.isHeadersSent()) {
                exceptionHandler.handleException(request, response, e);
            }
        } finally {
            // Apply after-completion filters (cleanup)
            if (request != null && response != null) {
                filterChain.applyAfterCompletion(request, response, occuredException);
            }
            
            // Close the socket
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    /**
     * Adds a custom filter to the filter chain.
     * 
     * @param filter The filter to add
     */
    public void addFilter(Filter filter) {
        if (filterChain != null) {
            filterChain.addFilter(filter);
        }
    }

    /**
     * Stops the server gracefully.
     */
    public void stop() {
        running = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("Error closing server socket", e);
            }
        }
        
        if (threadPool != null) {
            threadPool.shutdown();
        }
        
        if (applicationContext != null) {
            applicationContext.close();
        }
        
        logger.info("Server stopped");
    }

    /**
     * Gets the ApplicationContext (IoC Container).
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
