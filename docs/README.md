# HSpring Documentation

HSpring is a **mini Spring Boot written from scratch** in plain Java 17 (no Spring, no servlet container).
It has an IoC container, dependency injection, annotation-based routing, a filter chain,
global exception handling and a multi-threaded HTTP/1.1 server built directly on `java.net.ServerSocket`.

These docs explain **what each piece does, how it works inside, how it maps to real Spring**,
and how to talk about it in an interview.

## Reading order

| # | Document | What you'll learn |
|---|----------|-------------------|
| 1 | [Architecture overview](01-architecture.md) | The big picture: packages, startup sequence, request lifecycle |
| 2 | [IoC container & dependency injection](02-ioc-and-dependency-injection.md) | Component scanning (folders *and* JARs), constructor/field injection, circular dependencies, bean lifecycle |
| 3 | [HTTP server & threading](03-http-server-and-threading.md) | Sockets, thread pool, parsing HTTP by hand, bytes vs. characters, timeouts, graceful shutdown |
| 4 | [Routing, filters & exception handling](04-routing-filters-exceptions.md) | URL patterns → regex, most-specific-route wins, 404 vs. 405, filter order, `@ControllerAdvice`-style errors |
| 5 | [REST API reference](05-api-reference.md) | Every endpoint with request/response examples and the error format |
| 6 | [Running, testing & extending](06-running-testing-extending.md) | Build, run, configure, test, and add your own controllers/filters |
| 7 | [Bugs found & lessons learned](07-bugs-and-lessons.md) | Real bugs from this codebase: symptom → root cause → fix. Great interview stories |
| 8 | [Interview guide](08-interview-guide.md) | How to pitch the project, 60+ likely questions with answers |

## One-minute summary

```
java -jar hspring-framework-1.1.0.jar
        │
        ▼
WebServer.start()
  1. ApplicationContext   scan package → create beans → inject → @PostConstruct
  2. RouteResolver        @GetMapping("/users/{id}") → regex ^/api/users/([^/]+)$
  3. FilterChain          LoggingFilter (+ any you add)
  4. GlobalExceptionHandler
  5. Thread pool          hspring-worker-1..N
  6. ServerSocket         acceptor thread hands each connection to the pool
        │
        ▼
Per request (on a worker thread):
  parse bytes → HttpRequest → filters.preHandle → route → controller
  → filters.postHandle → filters.afterCompletion → close socket
  (any exception → GlobalExceptionHandler → JSON error with the right status)
```

## Spring ↔ HSpring cheat sheet

| Spring / Spring Boot | HSpring class | Notes |
|---|---|---|
| `SpringApplication.run()` | `WebServer.run()` / `start()` | Bootstraps everything |
| `ApplicationContext` / `DefaultListableBeanFactory` | `core/ApplicationContext` | Singleton beans only |
| `@Component`, `@Service`, `@RestController` | `annotations/*` | Same meaning |
| `@Autowired` (field, constructor) | `annotations/Autowired` | Single-constructor rule supported |
| `@PostConstruct`, `@PreDestroy` | `annotations/PostConstruct`, `PreDestroy` | Called by the container |
| `BeanCreationException`, `BeanCurrentlyInCreationException` | `core/BeanCreationException` | Fail-fast startup |
| `DispatcherServlet` + `RequestMappingHandlerMapping` | `core/RouteResolver` | Regex-based matching |
| `HttpServletRequest` / `HttpServletResponse` | `core/HttpRequest` / `HttpResponse` | Hand-written HTTP/1.1 parser/writer |
| `Filter` / `HandlerInterceptor` | `filter/Filter`, `FilterChain` | preHandle / postHandle / afterCompletion |
| `@ControllerAdvice` + `@ExceptionHandler` | `exception/GlobalExceptionHandler` | Exception type → HTTP status |
| Embedded Tomcat (blocking connector) | `WebServer` + `ExecutorService` | Thread-per-request from a fixed pool |
