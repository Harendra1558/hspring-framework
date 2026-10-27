package com.project.MultiThreadedWebServer;

import com.project.MultiThreadedWebServer.core.ApplicationContext;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.core.RouteResolver;
import com.project.MultiThreadedWebServer.exception.GlobalExceptionHandler;
import com.project.MultiThreadedWebServer.exception.MethodNotAllowedException;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import com.project.MultiThreadedWebServer.filter.Filter;
import com.project.MultiThreadedWebServer.filter.FilterChain;
import com.project.MultiThreadedWebServer.filter.LoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 *    - Performs dependency injection via @Autowired (constructor + field)
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
 *
 * CONFIGURATION (highest priority first, like Spring's property sources):
 *   1. Command-line argument:   java -jar app.jar 9090
 *   2. System property:         -Dserver.port=9090 -Dserver.threads=20
 *   3. Environment variable:    PORT=9090 SERVER_THREADS=20
 *   4. Default:                 port 8080, 10 threads
 */
public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    public static final String BASE_PACKAGE = "com.project.MultiThreadedWebServer";
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_THREADS = 10;

    /**
     * How long a worker thread waits for the client to send data.
     *
     * Without this, a client that opens a connection and never sends anything
     * (a "slowloris" attack, or just a buggy client) would block a worker
     * thread FOREVER. With 10 such connections, the whole pool is exhausted.
     */
    private static final int SOCKET_READ_TIMEOUT_MS = 10_000;

    // Server configuration
    private final int port;
    private final int threadPoolSize;

    // Core components (similar to Spring's internal components)
    private ApplicationContext applicationContext;
    private RouteResolver routeResolver;
    // Created eagerly so addFilter() works before start() is called
    private final FilterChain filterChain = new FilterChain();
    private GlobalExceptionHandler exceptionHandler;

    // Server state
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private volatile boolean running = false;

    /**
     * Creates a WebServer with default configuration.
     */
    public WebServer() {
        this(DEFAULT_PORT, DEFAULT_THREADS);
    }

    /**
     * Creates a WebServer with custom configuration.
     *
     * @param port           Port to listen on (0 = pick any free port, useful in tests)
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
        int port = resolveInt(args.length > 0 ? args[0] : null, "server.port", "PORT", DEFAULT_PORT);
        int threads = resolveInt(null, "server.threads", "SERVER_THREADS", DEFAULT_THREADS);

        WebServer server = new WebServer(port, threads);

        // Graceful shutdown on Ctrl+C / SIGTERM (Spring Boot registers a similar hook)
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "hspring-shutdown"));

        server.run(BASE_PACKAGE);
    }

    /**
     * Resolves a config value: CLI argument → system property → env variable → default.
     */
    static int resolveInt(String cliValue, String systemProperty, String envVariable, int defaultValue) {
        String[] candidates = {cliValue, System.getProperty(systemProperty), System.getenv(envVariable)};
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                try {
                    return Integer.parseInt(candidate.trim());
                } catch (NumberFormatException e) {
                    logger.warn("Ignoring invalid value '{}' for {}", candidate, systemProperty);
                }
            }
        }
        return defaultValue;
    }

    /**
     * Initializes the server and blocks until it is stopped.
     * This is the main bootstrap method, similar to SpringApplication.run().
     *
     * @param basePackage The base package to scan for components
     */
    public void run(String basePackage) {
        start(basePackage);
        try {
            acceptorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initializes all components, binds the port and starts accepting
     * connections on a background thread. Returns immediately.
     *
     * Splitting start() from run() lets tests start a real server,
     * fire HTTP requests at it, and stop it again.
     *
     * @param basePackage The base package to scan for components
     */
    public void start(String basePackage) {
        long startTime = System.currentTimeMillis();

        printBanner();

        logger.info("Starting HSpring WebServer...");

        // Step 1: Initialize the IoC Container (ApplicationContext)
        // Scanning works both from target/classes AND from inside a JAR file,
        // so no manual bean registration is needed.
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 1: Initializing IoC Container (ApplicationContext)");
        logger.info("═══════════════════════════════════════════════════════════════");
        applicationContext = new ApplicationContext(basePackage);

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
        filterChain.addFilter(new LoggingFilter());
        // Add more filters here (or call server.addFilter()): authFilter, rateLimitFilter, etc.

        // Step 4: Initialize Exception Handler
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 4: Initializing Exception Handler");
        logger.info("═══════════════════════════════════════════════════════════════");
        exceptionHandler = new GlobalExceptionHandler();

        // Step 5: Create Thread Pool
        // Named threads make logs and thread dumps readable: "hspring-worker-3"
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("STEP 5: Creating Thread Pool (size: {})", threadPoolSize);
        logger.info("═══════════════════════════════════════════════════════════════");
        AtomicInteger threadCounter = new AtomicInteger(1);
        threadPool = Executors.newFixedThreadPool(threadPoolSize,
                runnable -> new Thread(runnable, "hspring-worker-" + threadCounter.getAndIncrement()));

        // Step 6: Bind the port and start accepting connections
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start server on port " + port, e);
        }
        running = true;
        acceptorThread = new Thread(this::acceptLoop, "hspring-acceptor");
        acceptorThread.start();

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("HSpring WebServer started in {} ms on port {}", elapsed, getPort());
        logger.info("Server listening on http://localhost:{}", getPort());
        logger.info("═══════════════════════════════════════════════════════════════");
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
                ║   Version: 1.1.0                                          ║
                ║                                                           ║
                ╚═══════════════════════════════════════════════════════════╝
                """;
        System.out.println(banner);
    }

    /**
     * The acceptor loop: one thread accepts connections, worker threads handle them.
     *
     * This is the classic "thread-per-request with a pool" model used by
     * Tomcat's default (blocking) connector.
     */
    private void acceptLoop() {
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
    }

    /**
     * Handles a single HTTP request.
     * This method demonstrates the complete request lifecycle.
     *
     * IMPORTANT DESIGN NOTE:
     * The socket streams are deliberately NOT opened in a try-with-resources
     * block together with the catch clause. Java closes try-with-resources
     * resources BEFORE the catch block runs - so the exception handler would
     * write the error response into an already-closed stream, and the client
     * would receive an empty reply. Instead, the socket itself is closed in
     * the finally block, AFTER the error response has been written.
     *
     * @param clientSocket The client socket connection
     */
    private void handleRequest(Socket clientSocket) {
        HttpRequest request = null;
        HttpResponse response = null;
        Exception occurredException = null;

        try {
            clientSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
            InputStream in = new BufferedInputStream(clientSocket.getInputStream());
            OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());

            // The response exists before parsing, so even a malformed request gets a reply
            response = new HttpResponse(out);

            // Parse the request line, headers and body (returns null on an empty connection)
            request = HttpRequest.parse(in);
            if (request == null) {
                return;
            }

            // Apply pre-handle filters
            if (!filterChain.applyPreHandle(request, response)) {
                // Filter short-circuited the request. If it didn't write a reply itself,
                // answer with 403 so the client isn't left with an empty response.
                if (!response.isHeadersSent()) {
                    response.sendError(HttpResponse.FORBIDDEN, "Request rejected by filter");
                }
                return;
            }

            // Route the request to the appropriate controller
            boolean routeFound = routeResolver.resolve(request, response);

            if (!routeFound) {
                throwNoRouteException(request, response);
            }

            // Apply post-handle filters
            filterChain.applyPostHandle(request, response);

        } catch (SocketTimeoutException e) {
            occurredException = e;
            logger.warn("Client did not send a complete request within {} ms", SOCKET_READ_TIMEOUT_MS);
        } catch (Exception e) {
            occurredException = e;

            // Use exception handler if response not yet sent.
            // The streams are still open here, so the client WILL receive this response.
            if (response != null && !response.isHeadersSent()) {
                exceptionHandler.handleException(request, response, e);
            } else {
                logger.error("Error after response was committed", e);
            }
        } finally {
            // Apply after-completion filters (cleanup) - runs even on errors
            if (request != null && response != null) {
                filterChain.applyAfterCompletion(request, response, occurredException);
            }

            // Close the socket (this also closes its input and output streams)
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    /**
     * Distinguishes "path does not exist" (404) from
     * "path exists but not for this HTTP method" (405).
     * Spring MVC does exactly the same thing.
     *
     * Both are thrown as exceptions so the GlobalExceptionHandler renders
     * them with the same error format as every other error.
     */
    private void throwNoRouteException(HttpRequest request, HttpResponse response) {
        Set<String> allowedMethods = routeResolver.getAllowedMethods(request.getPath());
        if (!allowedMethods.isEmpty()) {
            response.header("Allow", String.join(", ", allowedMethods));
            throw new MethodNotAllowedException(
                    "Method " + request.getMethod() + " is not supported for " + request.getPath());
        }
        throw new NotFoundException("No handler found for " + request.getMethod() + " " + request.getPath());
    }

    /**
     * Adds a custom filter to the filter chain.
     * Can be called before or after start().
     *
     * @param filter The filter to add
     */
    public void addFilter(Filter filter) {
        filterChain.addFilter(filter);
    }

    /**
     * Stops the server gracefully:
     *   1. Stop accepting new connections
     *   2. Let in-flight requests finish (up to 5 seconds)
     *   3. Destroy the ApplicationContext
     *
     * Safe to call more than once (e.g. by a test AND the shutdown hook).
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        logger.info("Shutting down HSpring WebServer...");

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("Error closing server socket", e);
            }
        }

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (applicationContext != null) {
            applicationContext.close();
        }

        logger.info("Server stopped");
    }

    /**
     * The port the server is actually listening on.
     * When constructed with port 0, the OS picks a free port - this returns it.
     */
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    /**
     * Gets the ApplicationContext (IoC Container).
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
