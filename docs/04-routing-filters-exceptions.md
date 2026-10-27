# 4. Routing, Filters & Exception Handling

## Routing (`core/RouteResolver`)

Source: [`RouteResolver.java`](../src/main/java/com/project/MultiThreadedWebServer/core/RouteResolver.java)

### Registration (startup)

For every `@RestController` bean, `registerController` reads the class-level base path
(`@RestController("/api")`) and every method annotated with `@GetMapping`, `@PostMapping`, `@PutMapping`
or `@DeleteMapping`. Each route is compiled to a regex:

```
@RestController("/api") + @GetMapping("/users/{id}/posts/{postId}")
pattern:   /api/users/{id}/posts/{postId}
regex:     ^\Q/api/users/\E([^/]+)\Q/posts/\E([^/]+)$
variables: [id, postId]
```

- `{name}` becomes the capture group `([^/]+)`: one or more characters that aren't `/`.
- Literal parts are wrapped in `Pattern.quote` (`\Q…\E`), so a `.` or `+` in a path isn't treated as regex syntax.
- `^…$` anchors prevent partial matches (`/api/users/1/extra` doesn't match `/api/users/{id}`).

Routes are stored per HTTP method: `Map<String, List<RouteInfo>>`, where `RouteInfo` is a Java record of
`(pattern, regex, paramNames, controller, method)`.

**Fail-fast signature check:** a handler must take `(HttpRequest, HttpResponse)`, `(HttpResponse)` or no arguments.
Anything else throws at startup, not on the first request.

### Most specific route wins

With both `/users/me` and `/users/{id}`, the literal route must win for `/users/me`.
`Class.getDeclaredMethods()` returns methods in **no guaranteed order**, so after each registration the list
is sorted: **fewer path variables first**, then **longer pattern first**.
Spring's `AntPathMatcher`/`PathPattern` comparators apply the same principle.

### Dispatch (per request)

1. Look up the route list for the request's method.
2. Find the first regex that `matches()` the path.
3. Copy capture groups into `request.setPathVariable(name, URL-decoded value)`.
4. `Method.invoke(controller, request, response)`.

### Unwrapping `InvocationTargetException`

`Method.invoke` wraps anything the handler throws in an `InvocationTargetException`.
`RouteResolver` unwraps it and rethrows the **original** exception:

```java
} catch (InvocationTargetException e) {
    Throwable cause = e.getCause();
    if (cause instanceof RuntimeException re) throw re;   // e.g. NotFoundException
    if (cause instanceof Error err) throw err;
    throw new IllegalStateException("Handler threw a checked exception", cause);
}
```

The original code wrapped it a second time in `RuntimeException("Handler invocation failed")`, so the
exception handler never saw a `NotFoundException` and every error became a 500.

### 404 vs. 405

If no route matches, `WebServer` asks `routeResolver.getAllowedMethods(path)`:

- **Empty**: no route has this path → throws `NotFoundException` → `404 Not Found`.
- **Non-empty**: the path exists for other methods → sets an `Allow: GET, POST` header and throws
  `MethodNotAllowedException` → `405 Method Not Allowed`, as required by RFC 9110 and as Spring MVC does.

Throwing (instead of writing the response directly) means these errors use the same JSON format as every
other error. Spring does the same with `NoHandlerFoundException` and `HttpRequestMethodNotSupportedException`.

```
$ curl -i -X DELETE localhost:8080/api/users
HTTP/1.1 405 Method Not Allowed
Allow: GET, POST
```

## Filters (`filter/`)

Sources: [`Filter.java`](../src/main/java/com/project/MultiThreadedWebServer/filter/Filter.java),
[`FilterChain.java`](../src/main/java/com/project/MultiThreadedWebServer/filter/FilterChain.java),
[`LoggingFilter.java`](../src/main/java/com/project/MultiThreadedWebServer/filter/LoggingFilter.java)

```java
public interface Filter {
    boolean preHandle(HttpRequest req, HttpResponse res);                     // false = stop here
    default void postHandle(HttpRequest req, HttpResponse res) {}             // after controller, success only
    default void afterCompletion(HttpRequest req, HttpResponse res, Exception ex) {}  // ALWAYS
    default int getOrder() { return 0; }                                      // lower runs first
}
```

This mirrors Spring MVC's `HandlerInterceptor`. Order of execution for filters A (order 0) and B (order 10):

```
A.preHandle → B.preHandle → controller → B.postHandle → A.postHandle → B.afterCompletion → A.afterCompletion
```

Like an onion: the first in is the last out.

| Hook | Runs when | Typical use |
|---|---|---|
| `preHandle` | before routing | auth, rate limiting, request logging, start timers |
| `postHandle` | after the handler, only if no exception | add headers, metrics |
| `afterCompletion` | always (`finally` block) | cleanup: clear `ThreadLocal`s, stop timers, log errors |

**Rules worth knowing:**

- A filter that **throws** (e.g. `throw new UnauthorizedException("…")`) is handled by the
  `GlobalExceptionHandler` → `401`. The original chain swallowed filter exceptions, so this was impossible.
- A filter that returns **false** without writing a response gets a default `403 Forbidden`, so the client is
  never left with an empty reply.
- `LoggingFilter` stores the start time in a `ThreadLocal` and clears it in `afterCompletion`. Clearing it in
  `postHandle` (as the original did) leaks the value whenever a handler throws, because worker threads are reused.

### Writing an auth filter

```java
public class ApiKeyFilter implements Filter {
    @Override
    public boolean preHandle(HttpRequest request, HttpResponse response) {
        if (request.getPath().startsWith("/api/") && !"secret".equals(request.getHeader("X-API-Key"))) {
            throw new UnauthorizedException("Missing or invalid API key");
        }
        return true;
    }

    @Override
    public int getOrder() { return 10; }   // after LoggingFilter (0)
}

// before or after server.start(...)
server.addFilter(new ApiKeyFilter());
```

`WebServerIntegrationTest` registers exactly this kind of filter and asserts the 401.

## Exception handling (`exception/GlobalExceptionHandler`)

Source: [`GlobalExceptionHandler.java`](../src/main/java/com/project/MultiThreadedWebServer/exception/GlobalExceptionHandler.java)

Controllers **throw**; one central handler turns exceptions into HTTP responses, which is the
`@ControllerAdvice` pattern.

| Exception | Status | Logged as |
|---|---|---|
| `ValidationException(field, msg)` | 400 + `"field"` | WARN, one line |
| `IllegalArgumentException` (e.g. malformed HTTP) | 400 | WARN, one line |
| `UnauthorizedException` | 401 | WARN, one line |
| `NotFoundException` (incl. unknown paths) | 404 | WARN, one line |
| `MethodNotAllowedException` | 405 (+ `Allow` header) | WARN, one line |
| anything else | 500, generic message | ERROR, full stack trace |

Error body (shaped like Spring Boot's default error attributes):

```json
{"error": "Validation Error", "message": "Valid email is required", "field": "email", "status": 400, "path": "/api/users"}
```

Design notes:

- **4xx vs. 5xx logging:** client mistakes are expected traffic; logging a stack trace for every 404 buries
  real failures. Only 5xx gets a stack trace.
- **500s hide details:** the client gets `"An unexpected error occurred"`, never an exception message or stack
  trace, because those can leak internals.
- **Order matters** in the `instanceof` chain: check the most specific types first.
- All values are passed through `HttpResponse.escapeJson`, so a message containing `"` can't break the JSON.

### Adding a new mapping, e.g. 409 Conflict

1. `public class ConflictException extends RuntimeException { … }`
2. In `GlobalExceptionHandler.handleException`, add a branch:
   `else if (exception instanceof ConflictException) { send(response, 409, "Conflict", …); }`
3. Add `case 409 -> "Conflict";` to `HttpResponse.getStatusMessage`.
4. Throw it from your service.
