package com.project.MultiThreadedWebServer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.annotations.DeleteMapping;
import com.project.MultiThreadedWebServer.annotations.PutMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.annotations.GetMapping;
import com.project.MultiThreadedWebServer.annotations.PostMapping;
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
 *   │  GET /api/users/{id}                        →   UserController.getById() │
 *   │  POST /api/users                            →   UserController.create()  │
 *   │  DELETE /api/users/{id}                     →   UserController.delete()  │
 *   └───────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SUPPORTED CONTROLLER METHOD SIGNATURES (from least to most Spring-like)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. void method(HttpRequest, HttpResponse)    — Manual control (old style)
 * 2. ResponseEntity<T> method(HttpRequest)     — Spring-like: return ResponseEntity
 * 3. Object method(HttpRequest)                — Spring-like: auto-wrap in 200 OK
 * 
 * When a method returns ResponseEntity or Object, the framework:
 *   → Reads the status code from ResponseEntity (or defaults to 200)
 *   → Auto-serializes the body to JSON using ObjectMapper
 *   → Sends the complete HTTP response
 * 
 * This is EXACTLY what Spring's @ResponseBody / @RestController does!
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
 * 2. When request "/api/users/123" arrives, match against regex
 * 3. Capture group 1 contains "123"
 * 4. Map: {"id" → "123"}
 * 5. Set on request: request.setPathVariable("id", "123")
 */
public class RouteResolver {

    private static final Logger logger = LoggerFactory.getLogger(RouteResolver.class);

    /**
     * Route info record — holds all information about a single route.
     */
    private record RouteInfo(
            String pattern,
            Pattern regex,
            List<String> paramNames,
            Object controller,
            Method method
    ) {}

    /** Routes organized by HTTP method. */
    private final Map<String, List<RouteInfo>> routes = new ConcurrentHashMap<>();

    /** Regex pattern to find path variables like {id} in URL patterns. */
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^/]+)\\}");

    /**
     * ObjectMapper for auto-serializing ResponseEntity bodies to JSON.
     * 
     * In real Spring, this is part of the HttpMessageConverter system.
     * We keep it simple: any return value gets serialized to JSON.
     */
    private ObjectMapper objectMapper;

    /**
     * Creates a new RouteResolver.
     */
    public RouteResolver() {
        routes.put("GET", new ArrayList<>());
        routes.put("POST", new ArrayList<>());
        routes.put("PUT", new ArrayList<>());
        routes.put("DELETE", new ArrayList<>());
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sets the ObjectMapper used for auto-serialization.
     * Called by WebServer after the IoC container creates the @Bean ObjectMapper.
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
     */
    public void registerController(Object controller) {
        Class<?> clazz = controller.getClass();
        
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

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                String path = basePath + method.getAnnotation(GetMapping.class).value();
                registerRoute("GET", path, controller, method);
            }
            if (method.isAnnotationPresent(PostMapping.class)) {
                String path = basePath + method.getAnnotation(PostMapping.class).value();
                registerRoute("POST", path, controller, method);
            }
            if (method.isAnnotationPresent(PutMapping.class)) {
                String path = basePath + method.getAnnotation(PutMapping.class).value();
                registerRoute("PUT", path, controller, method);
            }
            if (method.isAnnotationPresent(DeleteMapping.class)) {
                String path = basePath + method.getAnnotation(DeleteMapping.class).value();
                registerRoute("DELETE", path, controller, method);
            }
        }
    }

    /**
     * Registers a single route — converts URL pattern to regex.
     */
    private void registerRoute(String httpMethod, String pattern, Object controller, Method method) {
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pattern);
        
        StringBuilder regexBuilder = new StringBuilder("^");
        int lastEnd = 0;
        
        while (matcher.find()) {
            String literalPart = pattern.substring(lastEnd, matcher.start());
            regexBuilder.append(Pattern.quote(literalPart));
            regexBuilder.append("([^/]+)");
            paramNames.add(matcher.group(1));
            lastEnd = matcher.end();
        }
        
        regexBuilder.append(Pattern.quote(pattern.substring(lastEnd)));
        regexBuilder.append("$");
        
        Pattern regex = Pattern.compile(regexBuilder.toString());
        RouteInfo routeInfo = new RouteInfo(pattern, regex, paramNames, controller, method);
        routes.get(httpMethod).add(routeInfo);
        
        // Log return type to show which style is used
        String returnType = method.getReturnType().getSimpleName();
        String style = returnType.equals("void") ? "(manual response)" : "(auto-serialized → " + returnType + ")";
        
        logger.info("  {} {} → {}.{}() {}", 
                httpMethod, 
                pattern, 
                controller.getClass().getSimpleName(), 
                method.getName(),
                style);
    }

    /**
     * Resolves a request to a handler and executes it.
     */
    public boolean resolve(HttpRequest request, HttpResponse response) {
        String httpMethod = request.getMethod();
        String path = request.getPath();

        List<RouteInfo> methodRoutes = routes.get(httpMethod);
        if (methodRoutes == null) {
            logger.warn("Unsupported HTTP method: {}", httpMethod);
            return false;
        }

        for (RouteInfo routeInfo : methodRoutes) {
            Matcher matcher = routeInfo.regex().matcher(path);
            
            if (matcher.matches()) {
                // Extract path variables from regex capture groups
                for (int i = 0; i < routeInfo.paramNames().size(); i++) {
                    String paramName = routeInfo.paramNames().get(i);
                    String paramValue = matcher.group(i + 1);
                    request.setPathVariable(paramName, paramValue);
                }

                return invokeHandler(routeInfo, request, response);
            }
        }

        logger.debug("No route found for {} {}", httpMethod, path);
        return false;
    }

    /**
     * Invokes the handler method with appropriate parameters.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HANDLER INVOCATION — THE HEART OF @RestController
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * This method supports MULTIPLE controller method signatures, progressing
     * from "manual" to "Spring-like":
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │  STYLE 1: Manual (old way)                                              │
     * │  void getUser(HttpRequest request, HttpResponse response)              │
     * │  → Developer manually builds JSON and calls response.json()            │
     * │                                                                         │
     * │  STYLE 2: ResponseEntity (Spring way!)                                 │
     * │  ResponseEntity<User> getUser(HttpRequest request)                     │
     * │  → Return ResponseEntity.ok(user), framework does the rest!            │
     * │                                                                         │
     * │  STYLE 3: Direct return (Spring @ResponseBody way!)                    │
     * │  User getUser(HttpRequest request)                                     │
     * │  → Return object directly, framework wraps in 200 OK + JSON            │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * In real Spring, @RestController = @Controller + @ResponseBody.
     * @ResponseBody means "serialize the return value as the response body."
     * Styles 2 and 3 above implement this exact behavior!
     */
    private boolean invokeHandler(RouteInfo routeInfo, HttpRequest request, HttpResponse response) {
        try {
            Method method = routeInfo.method();
            Object controller = routeInfo.controller();
            Class<?>[] paramTypes = method.getParameterTypes();

            Object result;

            // ─────────────────────────────────────────────────────────────────────
            // Invoke the method based on its parameter signature
            // ─────────────────────────────────────────────────────────────────────
            if (paramTypes.length == 2 && 
                paramTypes[0] == HttpRequest.class && 
                paramTypes[1] == HttpResponse.class) {
                // STYLE 1: void method(HttpRequest, HttpResponse) — manual
                method.invoke(controller, request, response);
                return true;  // Developer already handled the response

            } else if (paramTypes.length == 1 && 
                       paramTypes[0] == HttpRequest.class) {
                // STYLE 2 & 3: method(HttpRequest) → returns something
                result = method.invoke(controller, request);

            } else if (paramTypes.length == 1 && 
                       paramTypes[0] == HttpResponse.class) {
                // Legacy: method(HttpResponse)
                method.invoke(controller, response);
                return true;

            } else if (paramTypes.length == 0) {
                // No-arg method
                result = method.invoke(controller);

            } else {
                logger.warn("Unsupported method signature for: {}.{}", 
                        controller.getClass().getSimpleName(), 
                        method.getName());
                return false;
            }

            // ─────────────────────────────────────────────────────────────────────
            // AUTO-SERIALIZE THE RETURN VALUE (The @ResponseBody magic!)
            //
            // This is what makes @RestController special in Spring:
            //   - The return value is automatically converted to JSON
            //   - The HTTP status code is read from ResponseEntity (or defaults to 200)
            //   - The response is sent to the client
            // ─────────────────────────────────────────────────────────────────────
            if (result != null) {
                if (result instanceof ResponseEntity<?> responseEntity) {
                    // ResponseEntity — extract status + body, auto-serialize
                    int statusCode = responseEntity.getStatusCode();
                    Object body = responseEntity.getBody();

                    response.status(statusCode);

                    if (body != null) {
                        if (body instanceof String str) {
                            response.json(str);
                        } else {
                            // AUTO-SERIALIZATION: Object → JSON string
                            // This is what HttpMessageConverter does in real Spring!
                            String json = objectMapper.writeValueAsString(body);
                            response.json(json);
                        }
                    } else {
                        response.status(statusCode).send("");
                    }

                } else if (result instanceof String str) {
                    // Raw string return — send as JSON
                    response.json(str);
                } else {
                    // Any other object — auto-serialize to JSON with 200 OK
                    String json = objectMapper.writeValueAsString(result);
                    response.json(json);
                }
            }

            return true;
            
        } catch (Exception e) {
            logger.error("Error invoking handler method", e);
            throw new RuntimeException("Handler invocation failed", e);
        }
    }

    /**
     * Gets all registered routes for debugging/documentation.
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
