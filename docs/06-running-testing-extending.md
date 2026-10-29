# 6. Running, Testing & Extending

## Prerequisites

- **JDK 17+** (`java -version` must report 17 or newer; Maven uses `JAVA_HOME`)
- **Maven 3.6+**

> On a machine with several JDKs, point Maven at 17+ for the session:
> `export JAVA_HOME="/path/to/jdk-17"` (bash) or `$env:JAVA_HOME="C:\path\to\jdk-17"` (PowerShell).
> With Java 8 on `JAVA_HOME` the build fails with `Fatal error compiling: invalid flag: --release`.

## Build & run

```bash
mvn clean package                      # compile, run all tests, build the fat JAR
java -jar target/hspring-framework-1.1.0.jar
# → http://localhost:8080
```

Other ways to start it:

```bash
mvn compile exec:java                  # run from target/classes
java -jar target/hspring-framework-1.1.0.jar --server.port=9090   # custom port (CLI argument)
java -Dserver.port=9090 -Dserver.thread-pool-size=20 -jar target/hspring-framework-1.1.0.jar
SERVER_PORT=9090 SERVER_THREAD_POOL_SIZE=20 java -jar target/hspring-framework-1.1.0.jar
```

From an IDE: run `Application.main`.

### Configuration

Every key in [`application.properties`](../src/main/resources/application.properties) can be overridden,
highest priority first:

| Priority 1 | Priority 2 | Priority 3 | Priority 4 |
|---|---|---|---|
| `--key=value` argument | `-Dkey=value` | `KEY` env var (`.`/`-` → `_`, upper case) | `application.properties` |

| Setting | Key | Default |
|---|---|---|
| Port | `server.port` (env `SERVER_PORT` or `PORT`) | 8080 |
| Worker threads | `server.thread-pool-size` | 10 |
| Default role for new users | `user.default-role` | USER |

Logging is configured in [`src/main/resources/logback.xml`](../src/main/resources/logback.xml).

### Demo client

With the server running, `Client` sends 13 raw-socket requests covering every endpoint and error case:

```bash
mvn exec:java -Dexec.mainClass="com.project.MultiThreadedWebServer.Client"
```

### Deploying

The JAR reads `PORT` from the environment, so it runs unchanged on Railway / Render / Heroku-style platforms:

- Build command: `mvn clean package -DskipTests`
- Start command: `java -jar target/hspring-framework-1.1.0.jar`

## Tests

```bash
mvn test
```

| Test class | Kind | Covers |
|---|---|---|
| `core/HttpRequestTest` | unit | Request line, query decoding (UTF-8), case-insensitive headers, byte-exact bodies, size limits, malformed input |
| `core/HttpResponseTest` | unit | Wire format, byte-accurate `Content-Length`, double-send guard, JSON escaping |
| `com.hspring.routing.RouteResolverTest` | unit | Path variables, decoding, literal-beats-variable, allowed methods, exception unwrapping, signature validation, `ResponseEntity`/return-value serialization |
| `com.hspring.container.ApplicationContextTest` | unit | Constructor/field/interface/inherited injection, `@Autowired` constructor choice, bean names, lifecycle, circular/missing/ambiguous dependencies, `@Configuration`/`@Bean`, `@Value`, `@Qualifier` |
| `WebServerIntegrationTest` | integration | Starts the real app via `HSpringApplication.run()` on a random port; CRUD, Unicode, 400/401/404/405, raw malformed requests, lowercase headers, 40 concurrent creates |

Why are some tests under `com.hspring.*`? The integration test component-scans
`com.project.MultiThreadedWebServer`, and that scan includes `target/test-classes`. Test fixtures annotated
`@RestController`/`@Component` therefore live **outside** that package so they don't leak into the real app.
It's the same reason Spring Boot test slices and `@ComponentScan` filters exist.

## Extending the framework

### Add an endpoint

Any `@RestController` under `com.project.MultiThreadedWebServer` is picked up automatically:

```java
package com.project.MultiThreadedWebServer.controller;

@RestController("/api")
public class GreetingController {

    private final UserService userService;

    public GreetingController(UserService userService) {   // constructor injection, no @Autowired needed
        this.userService = userService;
    }

    @GetMapping("/greet/{name}")
    public void greet(HttpRequest request, HttpResponse response) {
        String name = request.getPathVariable("name");
        response.json("{\"message\": \"Hello, " + HttpResponse.escapeJson(name) + "\", \"users\": "
                + userService.countUsers() + "}");
    }
}
```

Handler signatures allowed: `(HttpRequest, HttpResponse)`, `(HttpResponse)`, or `()` returning a JSON `String`.

### Add a service or component

Annotate it with `@Service` or `@Component` and inject it by constructor or `@Autowired` field.
Depending on an **interface** works as long as exactly one component implements it.

### Add a filter

Implement `Filter` and call `server.addFilter(...)`; see [the auth filter example](04-routing-filters-exceptions.md#writing-an-auth-filter).
Filters are registered programmatically (they aren't scanned beans).

### Add an error type

See [Adding a new mapping](04-routing-filters-exceptions.md#adding-a-new-mapping-eg-409-conflict).

## Debugging tips

- Breakpoint in `ApplicationContext.getOrCreateBean` to watch the dependency graph being walked.
- Breakpoint in `RouteResolver.resolve` to see regex matching and path-variable extraction.
- Breakpoint in `WebServer.handleRequest` to follow a request end to end. Thread names tell you which worker you're on.
- `curl -v` shows the raw request and response headers.
