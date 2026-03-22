package com.project.MultiThreadedWebServer;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.core.RouteResolver;
import com.project.MultiThreadedWebServer.exception.GlobalExceptionHandler;
import com.project.MultiThreadedWebServer.filter.Filter;
import com.project.MultiThreadedWebServer.filter.FilterChain;
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
 * ║           (Embedded HTTP Server — Like Tomcat / Jetty / Netty)              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class is ONLY the HTTP server — it has NO application logic, NO IoC,
 * NO routing setup, NO main() method. It's just a network server.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * REAL SPRING BOOT ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * In real Spring Boot, the server (Tomcat) is SEPARATE from your application:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Application.java (@SpringBootApplication)                                  │
 * │      ↓                                                                      │
 * │  SpringApplication.run()                                                    │
 * │      ↓                                                                      │
 * │  Creates ApplicationContext (IoC, DI, scanning)                             │
 * │      ↓                                                                      │
 * │  Creates TomcatWebServer (just server code)                                 │
 * │      ↓                                                                      │
 * │  Tomcat.start() → listening for HTTP on port 8080                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * In HSpring:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Application.java (@SpringBootApplication)                                  │
 * │      ↓                                                                      │
 * │  HSpringApplication.run()                                                   │
 * │      ↓                                                                      │
 * │  Creates ApplicationContext (IoC, DI, scanning)                             │
 * │      ↓                                                                      │
 * │  Creates WebServer (this class — just server code!)                         │
 * │      ↓                                                                      │
 * │  WebServer.start() → listening for HTTP on port 8080                       │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT THIS CLASS DOES (AND DOESN'T DO)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ✓ Opens a ServerSocket and listens for TCP connections
 * ✓ Uses a thread pool to handle requests concurrently
 * ✓ Reads raw HTTP text and creates HttpRequest objects
 * ✓ Applies filters (pre/post processing)
 * ✓ Delegates to RouteResolver for URL → controller matching
 * ✓ Handles exceptions via GlobalExceptionHandler
 * 
 * ✗ Does NOT create beans or manage the IoC container
 * ✗ Does NOT set up routes or register controllers
 * ✗ Does NOT have a main() method
 * ✗ Does NOT contain @SpringBootApplication
 * 
 * These things are done by HSpringApplication.run(), which creates this
 * WebServer and gives it everything it needs (RouteResolver, FilterChain, etc.)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * REQUEST LIFECYCLE (handled by this class)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. Socket.accept()           → Accept TCP connection
 * 2. Parse raw HTTP            → Create HttpRequest object
 * 3. FilterChain.preHandle()   → Run pre-filters (logging, auth, etc.)
 * 4. RouteResolver.resolve()   → Find and invoke controller method
 * 5. FilterChain.postHandle()  → Run post-filters
 * 6. Socket.close()            → Close connection
 * 
 * On error: GlobalExceptionHandler formats a proper error JSON response
 */
public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    // ═══════════════════════════════════════════════════════════════════════════
    // Dependencies — injected by HSpringApplication during startup
    // These are NOT created here. This class receives them from outside,
    // just like Tomcat receives the DispatcherServlet from Spring.
    // ═══════════════════════════════════════════════════════════════════════════
    private final RouteResolver routeResolver;
    private final FilterChain filterChain;
    private final GlobalExceptionHandler exceptionHandler;
    private final int port;
    private final int threadPoolSize;

    // Server state
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    /**
     * Creates the embedded web server with all its dependencies.
     * 
     * Notice: The server receives everything it needs from the OUTSIDE.
     * It doesn't create its own RouteResolver or FilterChain.
     * HSpringApplication.run() creates these and passes them in.
     * 
     * This is the same pattern as Tomcat — Spring creates Tomcat and
     * gives it the DispatcherServlet (which knows about routes).
     * 
     * @param routeResolver     Resolves URLs to controller methods
     * @param filterChain       Chain of request/response filters
     * @param exceptionHandler  Handles exceptions centrally
     * @param port              Port to listen on (from application.properties)
     * @param threadPoolSize    Number of worker threads (from application.properties)
     */
    public WebServer(RouteResolver routeResolver,
                     FilterChain filterChain,
                     GlobalExceptionHandler exceptionHandler,
                     int port,
                     int threadPoolSize) {
        this.routeResolver = routeResolver;
        this.filterChain = filterChain;
        this.exceptionHandler = exceptionHandler;
        this.port = port;
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Starts the embedded HTTP server.
     * 
     * This is the equivalent of Tomcat.start() — it:
     * 1. Creates the thread pool for concurrent request handling
     * 2. Opens a ServerSocket on the configured port
     * 3. Enters the accept loop (blocks waiting for connections)
     * 4. Dispatches each connection to a worker thread
     */
    public void start() {
        running = true;
        threadPool = Executors.newFixedThreadPool(threadPoolSize);

        try {
            serverSocket = new ServerSocket(port);
            logger.info("Embedded server listening on http://localhost:{}", port);

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
            logger.error("Failed to start embedded server on port {}", port, e);
        }
    }

    /**
     * Handles a single HTTP request — the complete request lifecycle.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * REQUEST LIFECYCLE (matches real Spring Boot's DispatcherServlet flow)
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * ┌──────────────── Client sends HTTP request ────────────────────┐
     * │                                                                │
     * │  1. Parse raw HTTP text → HttpRequest object                  │
     * │  2. Pre-filters: logging, auth, CORS, etc.                    │
     * │  3. Route to controller method (RouteResolver)                │
     * │     → Controller returns ResponseEntity                       │
     * │     → Framework auto-serializes body to JSON                  │
     * │  4. Post-filters                                              │
     * │  5. Send HTTP response back to client                         │
     * │                                                                │
     * │  On error → GlobalExceptionHandler formats error JSON         │
     * └──────────────── Connection closed ────────────────────────────┘
     */
    private void handleRequest(Socket clientSocket) {
        HttpRequest request = null;
        HttpResponse response = null;
        Exception occuredException = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Parse raw HTTP text into structured HttpRequest
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            request = new HttpRequest(requestLine, reader);
            response = new HttpResponse(writer);

            // Pre-filters (logging, auth, etc.)
            if (!filterChain.applyPreHandle(request, response)) {
                return;
            }

            // Route to controller method
            boolean routeFound = routeResolver.resolve(request, response);

            if (!routeFound) {
                response.status(HttpResponse.NOT_FOUND)
                        .json("{\"error\": \"Not Found\", \"message\": \"No handler found for " +
                              request.getMethod() + " " + request.getPath() + "\", \"status\": 404}");
            }

            // Post-filters
            filterChain.applyPostHandle(request, response);

        } catch (Exception e) {
            occuredException = e;
            logger.error("Error handling request", e);

            if (response != null && !response.isHeadersSent()) {
                exceptionHandler.handleException(request, response, e);
            }
        } finally {
            if (request != null && response != null) {
                filterChain.applyAfterCompletion(request, response, occuredException);
            }

            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    /**
     * Adds a custom filter to the filter chain.
     */
    public void addFilter(Filter filter) {
        filterChain.addFilter(filter);
    }

    /**
     * Stops the embedded server gracefully.
     * Called by the JVM shutdown hook (set up in HSpringApplication).
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

        logger.info("Embedded server stopped");
    }
}
