package com.project.MultiThreadedWebServer.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                              HTTP REQUEST                                    ║
 * ║                    (Request Data Wrapper / Parser)                           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class wraps all details of an incoming HTTP request into a convenient object.
 * Similar to Spring's HttpServletRequest or WebRequest.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT IS AN HTTP REQUEST?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * When you type "http://localhost:8080/api/users?limit=5" in a browser,
 * the browser sends a text message like this over the network:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  GET /api/users?limit=5 HTTP/1.1        ← REQUEST LINE                     │
 * │  Host: localhost:8080                   ← HEADERS START                    │
 * │  User-Agent: Mozilla/5.0                                                    │
 * │  Accept: application/json                                                   │
 * │  Content-Type: application/json                                             │
 * │  Content-Length: 45                                                         │
 * │                                          ← EMPTY LINE (end of headers)     │
 * │  {"name": "John", "email": "j@mail.com"} ← BODY (for POST/PUT)            │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * This class PARSES that raw text into structured, easy-to-use data:
 *   - method = "GET"
 *   - path = "/api/users"
 *   - queryParams = {"limit": "5"}
 *   - headers = {"Host": "localhost:8080", "Accept": "application/json", ...}
 *   - body = "{\"name\": \"John\", ...}"
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ANATOMY OF A URL
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 *   http://localhost:8080/api/users/123?limit=5&sort=name
 *   └─────────────────────────────────────────────────────┘
 *                            │
 *                            ▼
 *   ┌──────────┬──────────────────────┬────────────────────┐
 *   │   PATH   │    PATH VARIABLE     │   QUERY PARAMS     │
 *   ├──────────┼──────────────────────┼────────────────────┤
 *   │ /api/users/{id}                 │ ?limit=5&sort=name │
 *   │          │    "123"             │ limit="5"          │
 *   │          │                      │ sort="name"        │
 *   └──────────┴──────────────────────┴────────────────────┘
 * 
 * PATH: The static part of the URL
 * PATH VARIABLE: Dynamic segment extracted by route matching (e.g., {id} = 123)
 * QUERY PARAMS: Key=value pairs after the ? symbol
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HOW TO USE THIS CLASS IN A CONTROLLER
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * @GetMapping("/users/{id}")
 * public void getUser(HttpRequest request, HttpResponse response) {
 *     // Get path variable
 *     String id = request.getPathVariable("id");  // "123"
 *     
 *     // Get query parameter
 *     String limit = request.getQueryParam("limit", "10");  // "5" or default "10"
 *     
 *     // Get header
 *     String contentType = request.getHeader("Content-Type");
 *     
 *     // Get request body (for POST/PUT)
 *     String body = request.getBody();  // JSON string
 * }
 */
public class HttpRequest {

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSED REQUEST DATA
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** HTTP method: GET, POST, PUT, DELETE, etc. */
    private final String method;
    
    /** Full URI including query string: /api/users?limit=5 */
    private final String uri;
    
    /** Path without query string: /api/users */
    private final String path;
    
    /** Query string part: limit=5&sort=name (after the ?) */
    private final String queryString;
    
    /**
     * HTTP headers: Host, Content-Type, Authorization, etc.
     * 
     * Header names are CASE-INSENSITIVE per the HTTP spec (RFC 9110):
     * "Content-Length", "content-length" and "CONTENT-LENGTH" are the same header.
     * A TreeMap with CASE_INSENSITIVE_ORDER gives us that behavior for free.
     */
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    
    /** Query parameters parsed from query string */
    private final Map<String, String> queryParams;
    
    /** 
     * Path variables extracted during route matching.
     * For route /users/{id} and request /users/123:
     *   pathVariables = {"id": "123"}
     */
    private final Map<String, String> pathVariables;
    
    /** Request body (for POST, PUT, PATCH requests) */
    private final String body;

    // ═══════════════════════════════════════════════════════════════════════════
    // SAFETY LIMITS (real servers like Tomcat have the same kind of limits)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Max length of the request line or a single header line (Tomcat default: 8 KB) */
    static final int MAX_LINE_BYTES = 8 * 1024;

    /** Max number of headers - protects against header-flooding attacks */
    static final int MAX_HEADERS = 100;

    /** Max request body size: 1 MB */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    /**
     * Parses an HTTP request directly from the socket's raw byte stream.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * WHY READ BYTES INSTEAD OF USING A BufferedReader?
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * The Content-Length header counts BYTES, not characters.
     * In UTF-8, "é" is 2 bytes and "日" is 3 bytes. If we read the body with
     * a Reader (which counts CHARACTERS), a body like {"name":"日本"} would
     * make us wait for more characters than the client will ever send - the
     * request hangs. So we read exact bytes, then decode them as UTF-8.
     *
     * @param in The socket input stream
     * @return the parsed request, or null if the client sent nothing (closed the connection)
     * @throws IOException              if reading from the socket fails
     * @throws IllegalArgumentException if the request is malformed (→ 400 Bad Request)
     */
    public static HttpRequest parse(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        return new HttpRequest(requestLine, in);
    }

    /**
     * Constructs an HttpRequest by parsing the raw HTTP request.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * PARSING STEPS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Step 1: Parse Request Line
     *   "GET /api/users?limit=5 HTTP/1.1"
     *           ↓
     *   method = "GET"
     *   uri = "/api/users?limit=5"
     *
     * Step 2: Separate Path from Query String
     *   uri = "/api/users?limit=5"
     *           ↓
     *   path = "/api/users"
     *   queryString = "limit=5"
     *
     * Step 3: Parse Query Parameters
     *   "limit=5&sort=name"
     *           ↓
     *   queryParams = {"limit": "5", "sort": "name"}
     *
     * Step 4: Parse Headers
     *   Read line by line until empty line
     *           ↓
     *   headers = {"Host": "localhost", "Content-Type": "application/json", ...}
     *
     * Step 5: Read Body (if POST/PUT/PATCH)
     *   Use Content-Length header to read exact number of bytes
     *           ↓
     *   body = "{\"name\": \"John\"}"
     *
     * @param requestLine The first line of HTTP request (e.g., "GET /users HTTP/1.1")
     * @param in          Input stream positioned right after the request line
     * @throws IOException if reading fails
     */
    public HttpRequest(String requestLine, InputStream in) throws IOException {
        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1: Parse the REQUEST LINE
        // Format: METHOD URI HTTP/VERSION
        // Example: "GET /api/users?limit=5 HTTP/1.1"
        // ─────────────────────────────────────────────────────────────────────────
        String[] parts = requestLine.trim().split("\\s+");
        if (parts.length < 2 || !parts[1].startsWith("/")) {
            throw new IllegalArgumentException("Malformed request line: " + requestLine);
        }
        this.method = parts[0].toUpperCase();
        this.uri = parts[1];

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 2: Separate PATH from QUERY STRING
        // URI: /api/users?limit=5
        //      └─path───┘└query─┘
        // ─────────────────────────────────────────────────────────────────────────
        int queryIndex = uri.indexOf('?');
        if (queryIndex != -1) {
            this.path = uri.substring(0, queryIndex);
            this.queryString = uri.substring(queryIndex + 1);
        } else {
            this.path = uri;
            this.queryString = "";
        }

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 3: Parse QUERY PARAMETERS
        // "limit=5&sort=name" → {"limit": "5", "sort": "name"}
        // ─────────────────────────────────────────────────────────────────────────
        this.queryParams = parseQueryParams(queryString);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 4: Parse HEADERS
        // Read lines until we hit an empty line
        // ─────────────────────────────────────────────────────────────────────────
        parseHeaders(in);

        // ─────────────────────────────────────────────────────────────────────────
        // Initialize PATH VARIABLES (populated later by RouteResolver)
        // ─────────────────────────────────────────────────────────────────────────
        this.pathVariables = new HashMap<>();

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 5: Read BODY (only for methods that have a body)
        // GET and DELETE typically don't have a body
        // POST, PUT, PATCH do have a body
        // ─────────────────────────────────────────────────────────────────────────
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            this.body = readBody(in);
        } else {
            this.body = "";
        }
    }

    /**
     * Parses query string into key-value pairs.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW QUERY STRING PARSING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Input: "name=John&age=25&city=New%20York"
     *              │
     *              ▼
     * Step 1: Split by "&"
     *   ["name=John", "age=25", "city=New%20York"]
     *              │
     *              ▼
     * Step 2: For each pair, split by "="
     *   "name=John" → key="name", value="John"
     *              │
     *              ▼
     * Step 3: URL decode the key and value
     *   "New%20York" → "New York"
     *              │
     *              ▼
     * Output: {"name": "John", "age": "25", "city": "New York"}
     *
     * @param queryString The query string to parse
     * @return Map of parameter names to values
     */
    private Map<String, String> parseQueryParams(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }

        // Split by & to get individual key=value pairs
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eqIndex = pair.indexOf('=');
            if (eqIndex != -1) {
                String key = pair.substring(0, eqIndex);
                String value = pair.substring(eqIndex + 1);
                params.put(decodeUrl(key), decodeUrl(value));
            } else {
                // Handle parameters without value: "?flag"
                params.put(decodeUrl(pair), "");
            }
        }
        return params;
    }

    /**
     * URL decoding ("percent-decoding").
     *
     * URLs cannot contain certain characters, so they are "percent-encoded":
     *   Space → %20 or +
     *   @ → %40
     *   é → %C3%A9   (two UTF-8 bytes)
     *
     * java.net.URLDecoder handles ALL of these, including multi-byte UTF-8.
     *
     * @throws IllegalArgumentException on invalid escapes like "%ZZ" (→ 400 Bad Request)
     */
    static String decodeUrl(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Parses HTTP headers from the request.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW HEADER PARSING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * HTTP headers are lines in format "Name: Value", ending with empty line:
     *
     *   Host: localhost:8080
     *   Content-Type: application/json
     *   Authorization: Bearer abc123
     *   Content-Length: 45
     *   (empty line signals end of headers)
     *
     * We read line by line until we hit an empty line, parsing each into the map.
     * The space after the colon is optional per the spec ("Host:localhost" is valid).
     *
     * @param in Input stream positioned after the request line
     */
    private void parseHeaders(InputStream in) throws IOException {
        String line;
        int count = 0;

        // Read until empty line (end of headers)
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            if (++count > MAX_HEADERS) {
                throw new IllegalArgumentException("Too many headers (max " + MAX_HEADERS + ")");
            }
            // Find the ":" separator
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
    }

    /**
     * Reads the request body based on Content-Length header.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW BODY READING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * The Content-Length header tells us exactly how many BYTES to read:
     *
     *   Content-Length: 45
     *
     *   {"name": "John", "email": "john@example.com"}
     *   └──────────────── 45 bytes ─────────────────┘
     *
     * We read exactly that many bytes, then decode them as UTF-8.
     *
     * Why not just readLine()? Because the body might:
     * 1. Contain newlines (it's JSON, could be multi-line)
     * 2. Not end with a newline
     * 3. Be binary data (not applicable for JSON APIs)
     *
     * NOTE: "Transfer-Encoding: chunked" bodies are not supported - a real
     * server would decode them chunk by chunk.
     *
     * @param in Input stream positioned after headers
     * @return The request body as a string
     */
    private String readBody(InputStream in) throws IOException {
        // Get Content-Length header, default to 0 if not present
        int contentLength;
        try {
            contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Content-Length header");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Invalid Content-Length header");
        }
        if (contentLength > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Request body too large (max " + MAX_BODY_BYTES + " bytes)");
        }
        if (contentLength == 0) {
            return "";
        }

        // readNBytes loops internally until it has all the bytes or hits end-of-stream
        // (a single read() might not return all bytes at once)
        byte[] bodyBytes = in.readNBytes(contentLength);
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads one line terminated by CRLF ("\r\n") or LF, decoded as UTF-8.
     *
     * @return the line without its terminator, or null at end of stream
     */
    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (buffer.size() >= MAX_LINE_BYTES) {
                throw new IllegalArgumentException("Request line or header too long");
            }
            buffer.write(b);
        }
        if (b == -1 && buffer.size() == 0) {
            return null;
        }
        String line = buffer.toString(StandardCharsets.UTF_8);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS - Access the parsed request data
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the HTTP method: GET, POST, PUT, DELETE, etc. */
    public String getMethod() {
        return method;
    }

    /** Returns the full URI including query string: /api/users?limit=5 */
    public String getUri() {
        return uri;
    }

    /** Returns the path without query string: /api/users */
    public String getPath() {
        return path;
    }

    /** Returns the raw query string: limit=5&sort=name */
    public String getQueryString() {
        return queryString;
    }

    /**
     * Gets a query parameter value.
     * 
     * Example:
     *   URL: /users?name=John&limit=10
     *   request.getQueryParam("name") → "John"
     *   request.getQueryParam("limit") → "10"
     *   request.getQueryParam("missing") → null
     */
    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    /**
     * Gets a query parameter with a default value if not present.
     * 
     * Example:
     *   request.getQueryParam("limit", "10") → "10" if limit not in URL
     */
    public String getQueryParam(String name, String defaultValue) {
        return queryParams.getOrDefault(name, defaultValue);
    }

    /** Returns all query parameters as an unmodifiable map. */
    public Map<String, String> getQueryParams() {
        return Collections.unmodifiableMap(queryParams);
    }

    /**
     * Gets a path variable value.
     * 
     * Path variables are parts of the URL that match patterns like {id}.
     * They are populated by RouteResolver during route matching.
     * 
     * Example:
     *   Route pattern: /users/{id}/posts/{postId}
     *   Actual URL: /users/123/posts/456
     *   request.getPathVariable("id") → "123"
     *   request.getPathVariable("postId") → "456"
     */
    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }

    /** Returns all path variables as an unmodifiable map. */
    public Map<String, String> getPathVariables() {
        return Collections.unmodifiableMap(pathVariables);
    }

    /**
     * Sets a path variable (called by RouteResolver during route matching).
     * 
     * This is NOT typically called by user code. The RouteResolver calls this
     * when it matches a URL pattern and extracts variable values.
     */
    public void setPathVariable(String name, String value) {
        pathVariables.put(name, value);
    }

    /**
     * Gets a header value (name lookup is case-insensitive).
     * 
     * Common headers:
     *   - Content-Type: application/json
     *   - Authorization: Bearer abc123
     *   - Accept: application/json
     *   - User-Agent: Mozilla/5.0
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    /** Returns all headers as an unmodifiable map. */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Gets the raw request body as a string.
     * 
     * For POST/PUT requests, this contains the request payload.
     * Typically JSON that needs to be parsed.
     * 
     * Example:
     *   String json = request.getBody();
     *   // json = {"name": "John", "email": "john@example.com"}
     *   
     *   // Parse with Jackson:
     *   User user = objectMapper.readValue(json, User.class);
     */
    public String getBody() {
        return body;
    }

    /**
     * Gets the Content-Type header value.
     * 
     * Common values:
     *   - application/json
     *   - application/x-www-form-urlencoded
     *   - multipart/form-data
     *   - text/plain
     */
    public String getContentType() {
        return headers.getOrDefault("Content-Type", "application/octet-stream");
    }

    /**
     * Returns a string representation for debugging.
     */
    @Override
    public String toString() {
        return String.format("HttpRequest{method='%s', path='%s', queryParams=%s, pathVariables=%s}",
                method, path, queryParams, pathVariables);
    }
}
