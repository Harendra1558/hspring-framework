package com.project.MultiThreadedWebServer;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import com.project.MultiThreadedWebServer.core.RouteResolver;
import com.project.MultiThreadedWebServer.exception.GlobalExceptionHandler;
import com.project.MultiThreadedWebServer.exception.MethodNotAllowedException;
import com.project.MultiThreadedWebServer.exception.NotFoundException;
import com.project.MultiThreadedWebServer.filter.Filter;
import com.project.MultiThreadedWebServer.filter.FilterChain;
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
 * ✓ Reads raw HTTP bytes and creates HttpRequest objects
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
 * LIFECYCLE OF A REQUEST
 * ═══════════════════════════════════════════════════════════════════════════════
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
 * On error: GlobalExceptionHandler formats a proper error JSON response
 */
public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    /**
     * How long a worker thread waits for the client to send data.
     *
     * Without this, a client that opens a connection and never sends anything
     * (a "slowloris" attack, or just a buggy client) would block a worker
     * thread FOREVER. With 10 such connections, the whole pool is exhausted.
     */
    private static final int SOCKET_READ_TIMEOUT_MS = 10_000;

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
    private Thread acceptorThread;
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
     * @param port              Port to listen on (0 = pick any free port, useful in tests)
     * @param threadPoolSize    Number of worker threads
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
     * 3. Starts the accept loop on a background thread
     * 4. Returns immediately (call awaitTermination() to block)
     *
     * Returning immediately lets tests start a real server, fire HTTP
     * requests at it, and stop it again. The acceptor thread is NOT a
     * daemon thread, so it keeps the JVM alive - just like Tomcat's.
     */
    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        // Named threads make logs and thread dumps readable: "hspring-worker-3"
        AtomicInteger threadCounter = new AtomicInteger(1);
        threadPool = Executors.newFixedThreadPool(threadPoolSize,
                runnable -> new Thread(runnable, "hspring-worker-" + threadCounter.getAndIncrement()));

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            threadPool.shutdownNow();
            throw new IllegalStateException("Failed to start server on port " + port, e);
        }
        running = true;
        acceptorThread = new Thread(this::acceptLoop, "hspring-acceptor");
        acceptorThread.start();

        logger.info("Embedded server listening on http://localhost:{} ({} worker threads)",
                getPort(), threadPoolSize);
    }

    /**
     * Blocks the calling thread until the server is stopped.
     */
    public void awaitTermination() throws InterruptedException {
        Thread acceptor = acceptorThread;
        if (acceptor != null) {
            acceptor.join();
        }
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
     * Handles a single HTTP request — the complete request lifecycle.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * REQUEST LIFECYCLE (matches real Spring Boot's DispatcherServlet flow)
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * ┌──────────────── Client sends HTTP request ────────────────────┐
     * │                                                                │
     * │  1. Parse raw HTTP bytes → HttpRequest object                 │
     * │  2. Pre-filters: logging, auth, CORS, etc.                    │
     * │  3. Route to controller method (RouteResolver)                │
     * │     → Controller returns ResponseEntity                       │
     * │     → Framework auto-serializes body to JSON                  │
     * │  4. Post-filters                                              │
     * │  5. Send HTTP response back to client                         │
     * │                                                                │
     * │  On error → GlobalExceptionHandler formats error JSON         │
     * └──────────────── Connection closed ────────────────────────────┘
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
     * Stops the embedded server gracefully:
     *   1. Stop accepting new connections
     *   2. Let in-flight requests finish (up to 5 seconds)
     *
     * Called by the JVM shutdown hook (set up in HSpringApplication).
     * Safe to call more than once (e.g. by a test AND the shutdown hook).
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        logger.info("Shutting down embedded server...");

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

        logger.info("Embedded server stopped");
    }

    /**
     * The port the server is actually listening on.
     * When constructed with port 0, the OS picks a free port - this returns it.
     */
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }
}
