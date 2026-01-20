package com.project.MultiThreadedWebServer.core;

import com.project.MultiThreadedWebServer.annotations.DeleteMapping;
import com.project.MultiThreadedWebServer.annotations.PutMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.GetMapping;
import com.project.MultiThreadedWebServer.PostMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                              ROUTE RESOLVER                                  ║
 * ║              (URL Mapping / Request Dispatcher)                              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class is responsible for mapping incoming HTTP requests to controller methods.
 * Similar to Spring's DispatcherServlet + HandlerMapping.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * THE PROBLEM THIS SOLVES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * When a request comes in for "GET /api/users/123", how do we know to call
 * UserController.getUserById()? This class maintains a mapping:
 * 
 *   ┌───────────────────────────────────────────────────────────────────────────┐
 *   │  HTTP Method + URL Pattern                  →   Handler Method           │
 *   ├───────────────────────────────────────────────────────────────────────────┤
 *   │  GET /                                      →   HomeController.home()    │
 *   │  GET /api/users                             →   UserController.getAll()  │
 *   │  GET /api/users/{id}                        →   UserController.getById() │
 *   │  POST /api/users                            →   UserController.create()  │
 *   │  PUT /api/users/{id}                        →   UserController.update()  │
 *   │  DELETE /api/users/{id}                     →   UserController.delete()  │
 *   └───────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HOW PATH VARIABLES WORK
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Pattern:    /api/users/{id}/posts/{postId}
 *                         ↓           ↓
 *              Variable: "id"   Variable: "postId"
 * 
 * Request:    /api/users/123/posts/456
 *                        ↓          ↓
 *                    id="123"   postId="456"
 * 
 * HOW WE MATCH:
 * 1. Convert pattern "/api/users/{id}" to regex "/api/users/([^/]+)"
 *    - {id} becomes ([^/]+) which means "capture one or more non-slash characters"
 * 
 * 2. When request "/api/users/123" arrives, match against regex
 *    - Regex matches!
 *    - Capture group 1 contains "123"
 * 
 * 3. Map capture groups to variable names
 *    - Variable names: ["id"]
 *    - Captured values: ["123"]
 *    - Result: {"id": "123"}
 * 
 * 4. Set path variables on the HttpRequest object
 *    - request.setPathVariable("id", "123")
 * 
 * 5. Controller can now access:
 *    - request.getPathVariable("id") → "123"
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ROUTE REGISTRATION PROCESS (At startup)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * When a controller is registered, we:
 * 
 * 1. Get all methods in the controller class
 * 2. For each method, check for route annotations
 * 3. Extract the URL pattern from the annotation
 * 4. Convert pattern to regex (for path variable matching)
 * 5. Store the route info for later lookup
 * 
 * Example:
 *   @RestController("/api")
 *   public class UserController {
 *       @GetMapping("/users/{id}")
 *       public void getUser(...) { }
 *   }
 *   
 *   Results in:
 *     - HTTP Method: GET
 *     - Pattern: /api/users/{id}
 *     - Regex: ^/api/users/([^/]+)$
 *     - Variables: ["id"]
 *     - Handler: UserController.getUser()
 */
public class RouteResolver {

    private static final Logger logger = LoggerFactory.getLogger(RouteResolver.class);

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * ROUTE INFO RECORD
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * This record holds all information about a single route.
     * Using Java 17 record for conciseness (immutable data carrier).
     * 
     * @param pattern    Original pattern from annotation: "/api/users/{id}"
     * @param regex      Compiled regex for matching: Pattern.compile("^/api/users/([^/]+)$")
     * @param paramNames List of path variable names in order: ["id"]
     * @param controller The controller instance that handles this route
     * @param method     The specific method to invoke
     */
    private record RouteInfo(
            String pattern,
            Pattern regex,
            List<String> paramNames,
            Object controller,
            Method method
    ) {}

    /**
     * Routes organized by HTTP method.
     * 
     * Structure:
     *   "GET"    → [RouteInfo1, RouteInfo2, ...]
     *   "POST"   → [RouteInfo3, RouteInfo4, ...]
     *   "PUT"    → [RouteInfo5, ...]
     *   "DELETE" → [RouteInfo6, ...]
     * 
     * Using ConcurrentHashMap for thread-safety.
     */
    private final Map<String, List<RouteInfo>> routes = new ConcurrentHashMap<>();

    /**
     * Regex pattern to find path variables like {id} in URL patterns.
     * 
     * \\{     → Match literal {
     * ([^/]+) → Capture group: one or more characters that aren't /
     * \\}     → Match literal }
     * 
     * Examples matches:
     *   "{id}"        → captures "id"
     *   "{userId}"    → captures "userId"
     *   "{post-id}"   → captures "post-id"
     */
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^/]+)\\}");

    /**
     * Creates a new RouteResolver with empty route maps.
     */
    public RouteResolver() {
        // Initialize route lists for each HTTP method
        routes.put("GET", new ArrayList<>());
        routes.put("POST", new ArrayList<>());
        routes.put("PUT", new ArrayList<>());
        routes.put("DELETE", new ArrayList<>());
    }

    /**
     * Registers all routes from a controller instance.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * REGISTRATION PROCESS
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * 1. Get base path from @RestController (if present)
     *    @RestController("/api") → basePath = "/api"
     * 
     * 2. Iterate through all declared methods
     * 
     * 3. For each method with a route annotation:
     *    - Combine basePath + annotation path
     *    - Convert to regex pattern
     *    - Store in routes map
     * 
     * @param controller The controller instance to scan
     */
    public void registerController(Object controller) {
        Class<?> clazz = controller.getClass();
        
        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1: Get base path from @RestController annotation
        // @RestController("/api") → basePath = "/api"
        // @RestController → basePath = ""
        // ─────────────────────────────────────────────────────────────────────────
        String basePath = "";
        if (clazz.isAnnotationPresent(RestController.class)) {
            basePath = clazz.getAnnotation(RestController.class).value();
        }

        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ Registering routes from: {}",clazz.getSimpleName());
        logger.info("└─────────────────────────────────────────────────────────┘");
        if (!basePath.isEmpty()) {
            logger.info("  Base path: {}", basePath);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 2: Scan all methods for route annotations
        // ─────────────────────────────────────────────────────────────────────────
        for (Method method : clazz.getDeclaredMethods()) {
            
            // Check for @GetMapping
            if (method.isAnnotationPresent(GetMapping.class)) {
                String path = basePath + method.getAnnotation(GetMapping.class).value();
                registerRoute("GET", path, controller, method);
            }
            
            // Check for @PostMapping
            if (method.isAnnotationPresent(PostMapping.class)) {
                String path = basePath + method.getAnnotation(PostMapping.class).value();
                registerRoute("POST", path, controller, method);
            }
            
            // Check for @PutMapping
            if (method.isAnnotationPresent(PutMapping.class)) {
                String path = basePath + method.getAnnotation(PutMapping.class).value();
                registerRoute("PUT", path, controller, method);
            }
            
            // Check for @DeleteMapping
            if (method.isAnnotationPresent(DeleteMapping.class)) {
                String path = basePath + method.getAnnotation(DeleteMapping.class).value();
                registerRoute("DELETE", path, controller, method);
            }
        }
    }

    /**
     * Registers a single route.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * PATTERN TO REGEX CONVERSION
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * Input:  /api/users/{id}/posts/{postId}
     * 
     * Process:
     * 1. Find all {variableName} patterns
     * 2. Replace each with capturing group ([^/]+)
     * 3. Quote literal parts to escape special regex chars
     * 4. Add anchors ^ and $
     * 
     * Output: ^/api/users/([^/]+)/posts/([^/]+)$
     * 
     * Also extract variable names in order: ["id", "postId"]
     * 
     * @param httpMethod HTTP method (GET, POST, etc.)
     * @param pattern    URL pattern (e.g., "/users/{id}")
     * @param controller Controller instance
     * @param method     Handler method
     */
    private void registerRoute(String httpMethod, String pattern, Object controller, Method method) {
        // ─────────────────────────────────────────────────────────────────────────
        // Extract path variable names and build regex pattern
        // ─────────────────────────────────────────────────────────────────────────
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pattern);
        
        // Build the regex pattern
        StringBuilder regexBuilder = new StringBuilder("^");  // Start anchor
        int lastEnd = 0;
        
        while (matcher.find()) {
            // ─────────────────────────────────────────────────────────────────────
            // For each {variableName} found:
            // 1. Append the literal part before it (quoted to escape special chars)
            // 2. Append ([^/]+) to capture the variable value
            // 3. Record the variable name
            // ─────────────────────────────────────────────────────────────────────
            
            // Append literal text before this variable (e.g., "/api/users/")
            String literalPart = pattern.substring(lastEnd, matcher.start());
            regexBuilder.append(Pattern.quote(literalPart));
            
            // Append capturing group: ([^/]+) matches one or more non-slash chars
            regexBuilder.append("([^/]+)");
            
            // Record the variable name (e.g., "id" from "{id}")
            paramNames.add(matcher.group(1));
            
            lastEnd = matcher.end();
        }
        
        // Append remaining literal part after last variable
        regexBuilder.append(Pattern.quote(pattern.substring(lastEnd)));
        regexBuilder.append("$");  // End anchor
        
        // Compile the regex pattern
        Pattern regex = Pattern.compile(regexBuilder.toString());
        
        // Create RouteInfo and add to routes
        RouteInfo routeInfo = new RouteInfo(pattern, regex, paramNames, controller, method);
        routes.get(httpMethod).add(routeInfo);
        
        // Log the registered route
        logger.info("  {} {} → {}.{}()", 
                httpMethod, 
                pattern, 
                controller.getClass().getSimpleName(), 
                method.getName());
    }

    /**
     * Resolves a request to a handler and executes it.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * RESOLUTION PROCESS
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * 1. Get the HTTP method from request (GET, POST, etc.)
     * 2. Get the list of routes for that method
     * 3. For each route:
     *    a. Match request path against route's regex
     *    b. If match found:
     *       - Extract path variables from capture groups
     *       - Set path variables on request object
     *       - Invoke the handler method
     *       - Return true
     * 4. If no route matches, return false (will trigger 404)
     * 
     * @param request  The HTTP request
     * @param response The HTTP response
     * @return true if a route was found and executed, false otherwise
     */
    public boolean resolve(HttpRequest request, HttpResponse response) {
        String httpMethod = request.getMethod();
        String path = request.getPath();

        // Get routes for this HTTP method
        List<RouteInfo> methodRoutes = routes.get(httpMethod);
        if (methodRoutes == null) {
            logger.warn("Unsupported HTTP method: {}", httpMethod);
            return false;
        }

        // Try to match each registered route
        for (RouteInfo routeInfo : methodRoutes) {
            Matcher matcher = routeInfo.regex().matcher(path);
            
            if (matcher.matches()) {
                // ─────────────────────────────────────────────────────────────────
                // MATCH FOUND! Extract path variables and invoke handler.
                // ─────────────────────────────────────────────────────────────────
                
                // Extract path variables from regex capture groups
                // Group 0 is the entire match; groups 1, 2, ... are captures
                for (int i = 0; i < routeInfo.paramNames().size(); i++) {
                    String paramName = routeInfo.paramNames().get(i);
                    String paramValue = matcher.group(i + 1);  // +1 because group 0 is full match
                    request.setPathVariable(paramName, paramValue);
                }

                // Invoke the handler method
                return invokeHandler(routeInfo, request, response);
            }
        }

        // No matching route found
        logger.debug("No route found for {} {}", httpMethod, path);
        return false;
    }

    /**
     * Invokes the handler method with appropriate parameters.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HANDLER INVOCATION
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * Controller methods can have different signatures:
     * 
     * 1. (HttpRequest, HttpResponse) - Full access to request and response
     *    @GetMapping("/users")
     *    public void getUsers(HttpRequest request, HttpResponse response)
     * 
     * 2. (HttpResponse) - Simple endpoints that only need to send response
     *    @GetMapping("/health")
     *    public void health(HttpResponse response)
     * 
     * We detect the signature and invoke appropriately.
     * 
     * @param routeInfo Route information including controller and method
     * @param request   The HTTP request
     * @param response  The HTTP response
     * @return true if invocation succeeded
     */
    private boolean invokeHandler(RouteInfo routeInfo, HttpRequest request, HttpResponse response) {
        try {
            Method method = routeInfo.method();
            Object controller = routeInfo.controller();

            // Determine parameter types and invoke accordingly
            Class<?>[] paramTypes = method.getParameterTypes();
            
            if (paramTypes.length == 2 && 
                paramTypes[0] == HttpRequest.class && 
                paramTypes[1] == HttpResponse.class) {
                // Signature: method(HttpRequest request, HttpResponse response)
                method.invoke(controller, request, response);
                
            } else if (paramTypes.length == 1 && 
                       paramTypes[0] == HttpResponse.class) {
                // Signature: method(HttpResponse response)
                method.invoke(controller, response);
                
            } else if (paramTypes.length == 0) {
                // Signature: method() - rare but supported
                Object result = method.invoke(controller);
                if (result instanceof String) {
                    response.json((String) result);
                }
            } else {
                logger.warn("Unsupported method signature for: {}.{}", 
                        controller.getClass().getSimpleName(), 
                        method.getName());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error invoking handler method", e);
            // Re-throw so GlobalExceptionHandler can catch it
            throw new RuntimeException("Handler invocation failed", e);
        }
    }

    /**
     * Gets all registered routes for debugging/documentation.
     * 
     * Returns a map like:
     *   "GET" → [
     *     "/api/users → UserController.getAllUsers",
     *     "/api/users/{id} → UserController.getUserById"
     *   ],
     *   "POST" → [...]
     */
    public Map<String, List<String>> getRegisteredRoutes() {
        Map<String, List<String>> result = new HashMap<>();
        
        for (Map.Entry<String, List<RouteInfo>> entry : routes.entrySet()) {
            List<String> patterns = new ArrayList<>();
            for (RouteInfo info : entry.getValue()) {
                patterns.add(info.pattern() + " → " + 
                        info.controller().getClass().getSimpleName() + "." + 
                        info.method().getName() + "()");
            }
            result.put(entry.getKey(), patterns);
        }
        
        return result;
    }
}
