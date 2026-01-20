package com.project.MultiThreadedWebServer.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    
    /** HTTP headers: Host, Content-Type, Authorization, etc. */
    private final Map<String, String> headers;
    
    /** Query parameters parsed from query string */
    private final Map<String, String> queryParams;
    
    /** 
     * Path variables extracted during route matching.
     * For route /users/{id} and request /users/123:
     *   pathVariables = {"id": "123"}
     */
    private final Map<String, String> pathVariables;
    
    /** Request body (for POST, PUT, PATCH requests) */
    private String body;

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
     * @param reader      BufferedReader to read headers and body
     * @throws IOException if reading fails
     */
    public HttpRequest(String requestLine, BufferedReader reader) throws IOException {
        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1: Parse the REQUEST LINE
        // Format: METHOD URI HTTP/VERSION
        // Example: "GET /api/users?limit=5 HTTP/1.1"
        // ─────────────────────────────────────────────────────────────────────────
        String[] parts = requestLine.split(" ");
        this.method = parts.length > 0 ? parts[0].toUpperCase() : "GET";
        this.uri = parts.length > 1 ? parts[1] : "/";

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
        this.headers = parseHeaders(reader);

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
            this.body = readBody(reader);
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
     * Step 3: URL decode the value
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
            int eqIndex = pair.indexOf('=');
            if (eqIndex != -1) {
                String key = pair.substring(0, eqIndex);
                String value = pair.substring(eqIndex + 1);
                params.put(key, decodeUrl(value));
            } else {
                // Handle parameters without value: "?flag"
                params.put(pair, "");
            }
        }
        return params;
    }

    /**
     * Basic URL decoding for common characters.
     * 
     * URLs cannot contain certain characters, so they are "percent-encoded":
     *   Space → %20 or +
     *   @ → %40
     *   # → %23
     *   etc.
     * 
     * This method converts them back to normal characters.
     * 
     * Real implementations use java.net.URLDecoder.decode() which is more complete.
     */
    private String decodeUrl(String value) {
        return value
                .replace("%20", " ")
                .replace("%21", "!")
                .replace("%40", "@")
                .replace("%23", "#")
                .replace("%24", "$")
                .replace("%26", "&")
                .replace("%3D", "=")
                .replace("+", " ");
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
     * 
     * @param reader BufferedReader positioned after the request line
     * @return Map of header names to values
     */
    private Map<String, String> parseHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        
        // Read until empty line (end of headers)
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // Find the ": " separator
            int colonIndex = line.indexOf(": ");
            if (colonIndex != -1) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 2).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    /**
     * Reads the request body based on Content-Length header.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW BODY READING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * The Content-Length header tells us exactly how many bytes to read:
     * 
     *   Content-Length: 45
     *   
     *   {"name": "John", "email": "john@example.com"}
     *   └──────────────── 45 bytes ─────────────────┘
     * 
     * We create a char array of that size and read exactly that many characters.
     * 
     * Why not just readLine()? Because the body might:
     * 1. Contain newlines (it's JSON, could be multi-line)
     * 2. Not end with a newline
     * 3. Be binary data (not applicable for JSON APIs)
     * 
     * @param reader BufferedReader positioned after headers
     * @return The request body as a string
     */
    private String readBody(BufferedReader reader) throws IOException {
        // Get Content-Length header, default to 0 if not present
        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
        if (contentLength == 0) {
            return "";
        }

        // Allocate buffer and read exactly contentLength characters
        char[] bodyChars = new char[contentLength];
        int totalRead = 0;
        
        // Read in chunks until we have all the bytes
        // (A single read() might not return all bytes at once)
        while (totalRead < contentLength) {
            int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
            if (read == -1) break;  // End of stream
            totalRead += read;
        }
        
        return new String(bodyChars, 0, totalRead);
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
     * Gets a header value.
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
