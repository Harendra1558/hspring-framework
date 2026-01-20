package com.project.MultiThreadedWebServer.core;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                              HTTP RESPONSE                                   ║
 * ║                    (Response Builder / Writer)                               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class provides a fluent API for building and sending HTTP responses.
 * Similar to Spring's HttpServletResponse or ResponseEntity.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT IS AN HTTP RESPONSE?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * After your controller processes a request, you need to send data back.
 * An HTTP response looks like this:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  HTTP/1.1 200 OK                        ← STATUS LINE                      │
 * │  Content-Type: application/json         ← HEADERS                          │
 * │  Content-Length: 45                                                         │
 * │  Connection: close                                                          │
 * │  Server: MiniSpring/1.0                                                     │
 * │                                          ← EMPTY LINE (end of headers)     │
 * │  {"id": 1, "name": "John", "email": "j@mail.com"}  ← BODY                  │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * This class helps you build this response step by step.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HTTP STATUS CODES (The most common ones)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 2xx SUCCESS:
 *   200 OK             - Request succeeded, here's the data
 *   201 Created        - Resource was created successfully
 *   204 No Content     - Success, but nothing to return
 * 
 * 3xx REDIRECTION:
 *   301 Moved Permanently - Resource moved, use new URL
 *   302 Found         - Temporary redirect
 *   304 Not Modified  - Cached version is still valid
 * 
 * 4xx CLIENT ERRORS:
 *   400 Bad Request   - Invalid request syntax/data
 *   401 Unauthorized  - Need to login first
 *   403 Forbidden     - Logged in but not allowed
 *   404 Not Found     - Resource doesn't exist
 *   405 Method Not Allowed - Wrong HTTP method
 * 
 * 5xx SERVER ERRORS:
 *   500 Internal Server Error - Something went wrong on server
 *   502 Bad Gateway   - Upstream server error
 *   503 Service Unavailable - Server temporarily down
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HOW TO USE THIS CLASS IN A CONTROLLER
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * EXAMPLE 1: Simple JSON Response
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @GetMapping("/users/{id}")                                                 │
 * │  public void getUser(HttpRequest request, HttpResponse response) {         │
 * │      User user = userService.findById(id);                                  │
 * │      String json = objectMapper.writeValueAsString(user);                   │
 * │      response.json(json);  // Sends 200 OK with JSON                       │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * EXAMPLE 2: Created Response (201)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @PostMapping("/users")                                                     │
 * │  public void createUser(HttpRequest request, HttpResponse response) {      │
 * │      User user = parseFromBody(request);                                    │
 * │      userService.save(user);                                                │
 * │      response.status(201).json(toJson(user));  // 201 Created              │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * EXAMPLE 3: Error Response
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @GetMapping("/users/{id}")                                                 │
 * │  public void getUser(HttpRequest request, HttpResponse response) {         │
 * │      User user = userService.findById(id);                                  │
 * │      if (user == null) {                                                    │
 * │          response.sendError(404, "User not found");                        │
 * │          return;                                                            │
 * │      }                                                                       │
 * │      response.json(toJson(user));                                           │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * EXAMPLE 4: Fluent API (Method Chaining)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  response                                                                    │
 * │      .status(200)                                                           │
 * │      .contentType("application/json")                                       │
 * │      .header("X-Custom-Header", "value")                                    │
 * │      .send("{\"message\": \"Hello!\"}");                                    │
 * └─────────────────────────────────────────────────────────────────────────────┘
 */
public class HttpResponse {

    /** The writer connected to the client socket */
    private final PrintWriter writer;
    
    /** HTTP status code (200, 404, 500, etc.) */
    private int statusCode = 200;
    
    /** Status message (OK, Not Found, Internal Server Error, etc.) */
    private String statusMessage = "OK";
    
    /** Content-Type header value */
    private String contentType = "application/json";
    
    /** Additional headers to include in response */
    private final Map<String, String> headers = new HashMap<>();
    
    /** Flag to prevent sending headers twice */
    private boolean headersSent = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMON HTTP STATUS CODES (Constants for convenience)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** 200 - Standard success response */
    public static final int OK = 200;
    
    /** 201 - Resource was created */
    public static final int CREATED = 201;
    
    /** 204 - Success but no content to return */
    public static final int NO_CONTENT = 204;
    
    /** 400 - Client sent invalid data */
    public static final int BAD_REQUEST = 400;
    
    /** 401 - Authentication required */
    public static final int UNAUTHORIZED = 401;
    
    /** 403 - Authenticated but not authorized */
    public static final int FORBIDDEN = 403;
    
    /** 404 - Resource not found */
    public static final int NOT_FOUND = 404;
    
    /** 405 - HTTP method not supported for this endpoint */
    public static final int METHOD_NOT_ALLOWED = 405;
    
    /** 500 - Something went wrong on the server */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * Creates an HttpResponse wrapper for the given writer.
     * 
     * @param writer PrintWriter connected to the client socket's output stream
     */
    public HttpResponse(PrintWriter writer) {
        this.writer = writer;
        
        // Set default headers
        headers.put("Connection", "close");       // Close connection after response
        headers.put("Server", "HSpring/1.0");  // Identify our server
    }

    /**
     * Sets the HTTP status code.
     * 
     * Usage:
     *   response.status(201)         // Created
     *   response.status(404)         // Not Found
     *   response.status(HttpResponse.CREATED)  // Using constant
     * 
     * @param statusCode HTTP status code (200, 404, 500, etc.)
     * @return this HttpResponse for method chaining
     */
    public HttpResponse status(int statusCode) {
        this.statusCode = statusCode;
        this.statusMessage = getStatusMessage(statusCode);
        return this;  // Return this for fluent API
    }

    /**
     * Sets the HTTP status code with a custom message.
     * 
     * Usage:
     *   response.status(418, "I'm a teapot")
     * 
     * @param statusCode    HTTP status code
     * @param statusMessage Custom status message
     * @return this HttpResponse for method chaining
     */
    public HttpResponse status(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        return this;
    }

    /**
     * Sets the content type of the response.
     * 
     * Common values:
     *   - "application/json"  → JSON data
     *   - "text/html"         → HTML page
     *   - "text/plain"        → Plain text
     *   - "text/xml"          → XML data
     * 
     * @param contentType MIME type
     * @return this HttpResponse for method chaining
     */
    public HttpResponse contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * Sets a response header.
     * 
     * Usage:
     *   response.header("X-Custom-Header", "value")
     *   response.header("Cache-Control", "no-cache")
     *   response.header("Set-Cookie", "session=abc123")
     * 
     * @param name  Header name
     * @param value Header value
     * @return this HttpResponse for method chaining
     */
    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Sends the response with the given body.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW RESPONSE SENDING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * This method writes the complete HTTP response to the socket:
     * 
     * 1. STATUS LINE:    HTTP/1.1 200 OK\r\n
     * 2. HEADERS:        Content-Type: application/json\r\n
     *                    Content-Length: 45\r\n
     *                    Connection: close\r\n
     * 3. EMPTY LINE:     \r\n
     * 4. BODY:           {"name": "John"}
     * 
     * Note: \r\n is CRLF (Carriage Return + Line Feed), required by HTTP spec.
     * 
     * @param body The response body as a string
     */
    public void send(String body) {
        // Prevent double-sending (would corrupt the response)
        if (headersSent) {
            throw new IllegalStateException("Headers already sent! Cannot send response twice.");
        }
        headersSent = true;

        // ─────────────────────────────────────────────────────────────────────────
        // Write STATUS LINE: HTTP/1.1 200 OK
        // ─────────────────────────────────────────────────────────────────────────
        writer.printf("HTTP/1.1 %d %s\r\n", statusCode, statusMessage);

        // ─────────────────────────────────────────────────────────────────────────
        // Write HEADERS
        // ─────────────────────────────────────────────────────────────────────────
        writer.printf("Content-Type: %s\r\n", contentType);
        writer.printf("Content-Length: %d\r\n", body.getBytes().length);
        
        // Write custom headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            writer.printf("%s: %s\r\n", entry.getKey(), entry.getValue());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Write EMPTY LINE (signals end of headers)
        // ─────────────────────────────────────────────────────────────────────────
        writer.print("\r\n");

        // ─────────────────────────────────────────────────────────────────────────
        // Write BODY
        // ─────────────────────────────────────────────────────────────────────────
        writer.print(body);
        
        // Ensure everything is written to the socket
        writer.flush();
    }

    /**
     * Sends a JSON response.
     * 
     * Convenience method that sets Content-Type to application/json.
     * 
     * Usage:
     *   response.json("{\"name\": \"John\"}");
     *   response.json(objectMapper.writeValueAsString(myObject));
     * 
     * @param jsonBody JSON string to send
     */
    public void json(String jsonBody) {
        contentType("application/json");
        send(jsonBody);
    }

    /**
     * Sends an HTML response.
     * 
     * Convenience method that sets Content-Type to text/html.
     * 
     * Usage:
     *   response.html("<html><body><h1>Hello!</h1></body></html>");
     * 
     * @param htmlBody HTML string to send
     */
    public void html(String htmlBody) {
        contentType("text/html; charset=UTF-8");
        send(htmlBody);
    }

    /**
     * Sends a plain text response.
     * 
     * Usage:
     *   response.text("Hello, World!");
     * 
     * @param text Plain text to send
     */
    public void text(String text) {
        contentType("text/plain; charset=UTF-8");
        send(text);
    }

    /**
     * Sends an error response with appropriate body format.
     * 
     * This method:
     * 1. Sets the status code
     * 2. Generates an error body (JSON or HTML based on content type)
     * 3. Sends the response
     * 
     * Usage:
     *   response.sendError(404, "User not found");
     *   response.sendError(400, "Invalid email format");
     *   response.sendError(500, "Database connection failed");
     * 
     * @param statusCode   HTTP status code (404, 400, 500, etc.)
     * @param errorMessage Error message to include in the body
     */
    public void sendError(int statusCode, String errorMessage) {
        status(statusCode);
        
        // Choose body format based on content type
        String body;
        if (contentType.contains("json")) {
            body = String.format("{\"error\": \"%s\", \"status\": %d}", errorMessage, statusCode);
        } else if (contentType.contains("html")) {
            body = String.format(
                    "<html><head><title>%s</title></head>" +
                    "<body><h1>%d %s</h1><p>%s</p></body></html>",
                    errorMessage, statusCode, statusMessage, errorMessage);
        } else {
            body = "Error: " + errorMessage;
        }
        
        send(body);
    }

    /**
     * Sends a redirect response.
     * 
     * This tells the browser to navigate to a different URL.
     * 
     * Usage:
     *   response.redirect("/login");
     *   response.redirect("https://example.com");
     * 
     * @param location URL to redirect to
     */
    public void redirect(String location) {
        status(302, "Found");
        header("Location", location);
        send("");  // Redirect responses have empty body
    }

    /**
     * Returns the standard status message for a status code.
     * 
     * HTTP status codes have standardized messages:
     *   200 → "OK"
     *   404 → "Not Found"
     *   500 → "Internal Server Error"
     * 
     * @param statusCode The HTTP status code
     * @return The standard message for that code
     */
    private String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 415 -> "Unsupported Media Type";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Unknown";
        };
    }

    /** Returns the current status code. */
    public int getStatusCode() {
        return statusCode;
    }

    /** Returns whether headers have already been sent. */
    public boolean isHeadersSent() {
        return headersSent;
    }
}
