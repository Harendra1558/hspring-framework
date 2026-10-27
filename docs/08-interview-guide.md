# 8. Interview Guide

How to present this project, and the questions it invites, with answers grounded in this codebase.
Each answer gives a short version you can say out loud, then pointers to the code.

---

## Pitching the project

### 30-second version

> "I built a mini Spring Boot from scratch in Java 17 to understand what the framework does for me.
> It has an IoC container that scans packages, including inside a JAR, and does constructor and field
> injection with circular-dependency detection and `@PostConstruct`/`@PreDestroy`. On top of that there's
> annotation-based routing with path variables, a filter chain, a `@ControllerAdvice`-style exception handler,
> and a multi-threaded HTTP/1.1 server I wrote directly on `ServerSocket` with a fixed thread pool.
> It's covered by 48 JUnit tests, including an integration test that boots the real server on a random port."

### 2-minute version: add the *story*

1. **Why:** "Spring felt like magic: how does `@Autowired` find the bean? How does `{id}` get extracted?"
2. **What:** walk through the startup sequence (scan → create → inject → init → routes → filters → thread pool → accept loop).
3. **Hard part (pick one bug from [doc 7](07-bugs-and-lessons.md)):** "Every error response was empty. Two causes:
   try-with-resources closes the writer *before* the catch block, and reflection wrapped exceptions so 404s became 500s."
4. **Trade-offs:** thread-per-request vs. NIO/virtual threads, and singleton beans requiring thread safety.
5. **What's next:** argument resolvers (`@PathVariable`), keep-alive, virtual threads.

### Questions to expect from the interviewer *about the project*

- "Why didn't you just use Spring?" → *To learn the internals; I use Spring in production. This project is about understanding it.*
- "What was the hardest part?" → The empty-response bug, or circular dependency detection.
- "How did you test it?" → Unit tests per component, plus an integration test on port 0 with `java.net.http.HttpClient`, including 40 concurrent creates.
- "What would break first under load?" → see [Scaling](#performance--scaling).

---

## IoC & Dependency Injection

**Q: What is Inversion of Control? How is it different from Dependency Injection?**
IoC is the principle: a framework controls object creation and flow instead of your code. DI is one way to
implement IoC: dependencies are *given* to an object instead of the object creating them.
(Other IoC forms: template method, event callbacks, service locator.)

**Q: Walk me through how your container creates beans.**
Scan the classpath for annotated classes → for each, pick a constructor and recursively get/create its parameter
beans → instantiate → inject `@Autowired` fields → call `@PostConstruct`. See `ApplicationContext.getOrCreateBean`.

**Q: How does component scanning work? How do you find classes inside a JAR?**
`ClassLoader.getResources(packagePath)` returns URLs. A `file:` URL is walked as a directory; a `jar:` URL is opened
via `JarURLConnection.getJarFile()` and its entries are filtered by prefix. Each class is loaded with
`Class.forName(name, false, loader)` (no static init), then checked for annotations.
Spring instead reads bytecode with ASM, so it never loads non-component classes.

**Q: Constructor vs. field vs. setter injection: which and why?**
Constructor by default: `final` fields, no half-built objects, easy unit tests, cycles fail fast.
Field injection hides dependencies and needs reflection to test. Setter injection fits optional or
reconfigurable dependencies. The project demos both: `UserService` (constructor) and `UserController` (field).

**Q: When does Spring use a constructor without `@Autowired`?**
When the class has exactly one constructor (Spring 4.3+). My `selectConstructor` implements the same rule.

**Q: How do you detect circular dependencies?**
A `currentlyInCreation` set. Before creating class X, if X is already in the set, we've looped back:
throw with the path `A → B → A`. Without it you'd get a `StackOverflowError`.

**Q: Why can field-injection cycles work but constructor cycles can't?**
With fields, both objects can be instantiated first and wired afterwards. With constructors, A needs a finished B
to exist and B needs a finished A, so there's no valid first step. Spring resolves field/setter cycles with an
"early singleton reference" (three-level cache), but Spring Boot 2.6+ disallows cycles by default.

**Q: Spring's three-level cache, briefly?**
`singletonObjects` (finished beans), `earlySingletonObjects` (created but not fully populated),
`singletonFactories` (factories that can produce an early reference, possibly an AOP proxy).
A half-built bean is exposed early so a cyclic field dependency can reference it.

**Q: What happens if two beans implement the same interface?**
Injection by that interface is ambiguous → my container throws (`Expected a single bean of type …`).
Spring throws `NoUniqueBeanDefinitionException`; fix with `@Primary`, `@Qualifier`, or injecting `List<T>`.

**Q: What's the bean lifecycle?**
Instantiate → populate properties (DI) → `Aware` callbacks → `BeanPostProcessor.before` → `@PostConstruct` /
`afterPropertiesSet` / init-method → `BeanPostProcessor.after` (AOP proxies are created here) → in use →
`@PreDestroy` / `destroy()` on shutdown. HSpring implements instantiate → inject → `@PostConstruct` … `@PreDestroy`.

**Q: Why are beans destroyed in reverse creation order?**
Dependencies are created before dependents, so reversing means a bean shuts down while its dependencies are still alive.

**Q: Singleton beans in a multi-threaded server: what's the risk?**
One instance serves all requests concurrently, so mutable fields are shared state. Keep beans stateless,
or make state thread-safe (`ConcurrentHashMap`, atomics, immutable values). Spring has the same constraint.

**Q: What are `@Component`, `@Service`, `@Repository`, `@Controller`?**
All mark beans for scanning. `@Service`/`@Controller` are semantic. `@Repository` adds exception translation;
`@RestController` = `@Controller` + `@ResponseBody`.

**Q: What does `@SpringBootApplication` do?**
`@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` (of the annotated class's package).
Auto-configuration conditionally registers beans (`@ConditionalOnClass`, `@ConditionalOnMissingBean`) from
`AutoConfiguration.imports`.

**Q: How would you add `@Transactional` or other AOP to your container?**
After creating a bean, wrap it in a proxy: a JDK dynamic proxy (`java.lang.reflect.Proxy`) for interfaces, or
CGLIB/ByteBuddy subclassing for classes. The proxy opens the transaction, calls the target, and commits or rolls back.
This is why self-invocation (`this.method()`) bypasses `@Transactional` in Spring: the call never goes through the proxy.

---

## Reflection & Annotations

**Q: How do runtime annotations work?**
`@Retention(RUNTIME)` keeps them in the class file and readable via reflection
(`isAnnotationPresent`, `getAnnotation`). `@Target` restricts where they can go.
`SOURCE` retention (e.g. `@Override`) is discarded by the compiler; `CLASS` retention is kept in bytecode but invisible to reflection.

**Q: How do you inject a `private` field?**
`field.setAccessible(true)` then `field.set(bean, value)`. On Java 9+ modules this needs the package to be
`open`; classpath code is in the unnamed module, so it works.

**Q: Why unwrap `InvocationTargetException`?**
`Method.invoke` wraps whatever the target throws. If you don't unwrap, error handling sees the wrapper,
not `NotFoundException`, and every error becomes a 500. This was a real bug here.

**Q: Downsides of reflection?**
Slower than direct calls (mostly a startup cost; JIT inlines `Method.invoke` after warm-up), bypasses compile-time
checks, breaks with obfuscation, and needs extra config for GraalVM native images.
Spring Boot 3 AOT generates code to avoid runtime reflection for native images.

---

## Web layer / Spring MVC

**Q: How does a request reach your controller method?**
Worker thread parses bytes → `HttpRequest` → `FilterChain.preHandle` → `RouteResolver` finds the first regex that
matches for the method → fills path variables → `Method.invoke` → controller writes the `HttpResponse`.

**Q: What does `DispatcherServlet` do in real Spring?**
Front controller: `HandlerMapping` finds the handler, `HandlerAdapter` invokes it (resolving arguments like
`@PathVariable`/`@RequestBody` via `HandlerMethodArgumentResolver`s), `HttpMessageConverter`s serialize the return
value, `HandlerExceptionResolver`s handle errors, and interceptors run around it all.

**Q: How do you match `/users/{id}`?**
At startup, compile the pattern to `^/users/([^/]+)$` and remember variable names in order. At request time,
`matcher.matches()` and map group *i* to name *i*.

**Q: `/users/me` vs. `/users/{id}`: who wins?**
The most specific: literal before variable. I sort routes by number of variables, then by length, because
`getDeclaredMethods()` order is unspecified.

**Q: 404 vs. 405?**
404: no route has this path. 405: the path exists but not for this method; the response must include `Allow`.

**Q: Filter vs. HandlerInterceptor vs. AOP in Spring?**
Servlet `Filter`: container level, before Spring MVC, sees every request (security, CORS, encoding).
`HandlerInterceptor`: inside MVC, knows the target handler (`preHandle`/`postHandle`/`afterCompletion`).
AOP: method level, on any bean (transactions, caching, auditing). My `Filter` interface is modeled on `HandlerInterceptor`.

**Q: Why does `postHandle` not run when the handler throws, and where do you clean up?**
`postHandle` is for successful handling. `afterCompletion` always runs, so cleanup (`ThreadLocal.remove`, timers)
belongs there. Moving the `ThreadLocal` cleanup there fixed a leak.

**Q: How does `@ControllerAdvice` work?**
`ExceptionHandlerExceptionResolver` finds the closest matching `@ExceptionHandler` by exception type (most specific
class first) and renders its result. My `GlobalExceptionHandler` is a hand-written type → status map.

**Q: Why not return exception messages for 500s?**
They can leak internals (SQL, file paths, class names). Log the details server-side; return a generic message.

---

## HTTP & Networking

**Q: Describe an HTTP/1.1 request on the wire.**
Request line (`METHOD URI VERSION`), header lines, an empty line (`\r\n\r\n`), then a body of `Content-Length` bytes
(or chunked). Responses: status line, headers, empty line, body.

**Q: Why read the body as bytes?**
`Content-Length` counts bytes; readers count characters. Multi-byte UTF-8 would make the server wait forever or
truncate. Always decode with an explicit charset.

**Q: Are header names case-sensitive?**
No (RFC 9110). I store them in a `TreeMap(String.CASE_INSENSITIVE_ORDER)`. HTTP/2 even requires lowercase names.

**Q: What is keep-alive and why don't you support it?**
Reusing one TCP connection for many requests saves handshakes. Supporting it requires a request loop per connection,
correct framing (Content-Length/chunked) and idle timeouts. I send `Connection: close` to keep the model simple.

**Q: What's a slowloris attack and how do you mitigate it?**
Clients open connections and send bytes very slowly, holding threads. Mitigation: socket read timeout
(`setSoTimeout(10s)`), limits on header size and count, max connections. NIO servers are more resistant because an
idle connection doesn't hold a thread.

**Q: Idempotent and safe methods?**
Safe (no state change): GET, HEAD, OPTIONS. Idempotent (same effect if repeated): GET, PUT, DELETE, HEAD, OPTIONS.
POST isn't idempotent, which is why creating a user returns 201 + `Location`.

**Q: Which status codes does your API use and why?**
200 OK, 201 Created (+ `Location`), 400 validation/malformed, 401 auth filter, 403 rejected by filter,
404 unknown resource/path, 405 wrong method, 500 unexpected.
PUT returning 200 with the updated body (instead of 204) is a design choice.

---

## Concurrency & Multithreading

**Q: Explain your threading model.**
One acceptor thread blocks on `accept()` and submits each socket to a fixed `ExecutorService` of N workers.
Each worker handles one request end to end. Bounded threads; excess connections queue.

**Q: Why a thread pool instead of `new Thread()` per request?**
Thread creation is expensive (~1 MB stack each, OS scheduling). Unbounded threads under load cause OOM or
thrashing. Pools reuse threads and cap concurrency.

**Q: What's the problem with `Executors.newFixedThreadPool` under heavy load?**
Its queue (`LinkedBlockingQueue`) is unbounded, so memory grows without backpressure. Production code uses a
`ThreadPoolExecutor` with a bounded queue and a rejection policy (e.g. respond 503).

**Q: How would virtual threads (Java 21) change this?**
Replace the pool with `Executors.newVirtualThreadPerTaskExecutor()`. Blocking I/O parks the virtual thread, not the
carrier OS thread, so you can have hundreds of thousands of concurrent blocking requests with simple code.
Watch for pinning (`synchronized` around blocking I/O in Java 21–23) and `ThreadLocal` memory per request.

**Q: Blocking I/O vs. NIO (Netty/Tomcat NIO)?**
Blocking: a thread per connection, simple code. NIO: a selector multiplexes many sockets on a few event-loop
threads, scales to many idle connections, but needs a non-blocking programming model (WebFlux).

**Q: Is `ConcurrentHashMap` enough to make the repository thread-safe?**
Only if each logical operation is a *single* map call. `containsKey` then `remove` is a check-then-act race.
Use atomic methods: `computeIfPresent`, `putIfAbsent`, `remove(key) != null`, `merge`.
Values must also be immutable or thread-safe; that's why `User` is a record.

**Q: How do you generate unique IDs concurrently?**
`AtomicLong.getAndIncrement()`: a lock-free CAS loop. The integration test fires 40 parallel POSTs and asserts 40 distinct IDs.

**Q: What does `volatile` do on your `running` flag?**
Guarantees visibility: when `stop()` sets `running = false` on one thread, the acceptor thread sees it.
It does *not* make compound actions atomic.

**Q: What is `ThreadLocal` and what's the danger with pools?**
A per-thread variable. Pooled threads live forever, so a value not removed leaks memory and can bleed into the next
request (security context, MDC). Always `remove()` in `finally`/`afterCompletion`.

**Q: Why `CopyOnWriteArrayList` for filters?**
Every request iterates the list; additions are rare. COW gives lock-free, never-`ConcurrentModificationException`
iteration at the cost of copying on write.

**Q: How do you shut down gracefully?**
JVM shutdown hook → close the server socket (stop accepting) → `executor.shutdown()` + `awaitTermination(5s)` →
`shutdownNow()` if still busy → `@PreDestroy`. Spring Boot: `server.shutdown=graceful`.

---

## Java language features used

| Feature | Where | Why |
|---|---|---|
| Records | `User`, `RouteResolver.RouteInfo` | Immutable data carriers; `equals`/`hashCode`/`toString` for free |
| Switch expressions | `HttpResponse.getStatusMessage`, `escapeJson` | Concise, exhaustive |
| Pattern matching for `instanceof` | `GlobalExceptionHandler`, `RouteResolver` | No casts |
| Text blocks | Banner, home page HTML | Multi-line strings |
| `Stream.toList()` (16+) | `UserRepository.findAll` | Unmodifiable list |
| `InputStream.readNBytes` | `HttpRequest.readBody` | Reads exactly N bytes |
| `java.net.http.HttpClient` | Integration test | Modern HTTP client (11+) |

---

## Testing

**Q: How do you test a server without fixed ports?**
Bind port 0 → the OS picks a free port → `server.getPort()` returns it. No conflicts in parallel CI runs.

**Q: Why are some test classes in a different package?**
The integration test scans `com.project.MultiThreadedWebServer`, which includes `test-classes`. Fixture components
there would be picked up by the real app (a test fixture with a bad handler signature would even break startup),
so fixtures live under `com.hspring.*`.

**Q: Unit vs. integration tests here?**
Unit: parser, response writer, router, container, each in isolation with in-memory streams.
Integration: real sockets and threads, real component scan; catches wiring bugs unit tests miss (like the empty-response bug).

---

## Performance & scaling

**Q: What breaks first under load?**
1. The worker pool (10 threads): slow requests queue up in the unbounded executor queue.
2. `Connection: close`: a TCP handshake per request.
3. Logging: `LoggingFilter` writes ~10 INFO lines per request synchronously.
4. Everything is in memory, so one instance only (no horizontal scaling).

**Q: How would you scale it?**
Virtual threads or NIO; keep-alive; async logging; a real database plus statelessness to run several instances
behind a load balancer; a bounded queue with 503 for backpressure.

---

## What would you improve next?

| Improvement | How |
|---|---|
| `@PathVariable`, `@RequestParam`, `@RequestBody` parameters | Inspect `Method.getParameters()`; resolve each with a resolver strategy (Spring's `HandlerMethodArgumentResolver`); compile with `-parameters` for names |
| Return values → JSON | If a handler returns an object, serialize it with Jackson (`HttpMessageConverter`) |
| `@Qualifier` / `@Primary` | Disambiguate in `resolveImplementation` |
| `@Configuration` + `@Bean` methods | Treat annotated methods as factories; call them with resolved arguments |
| `@Value` + `application.properties` | Load properties; inject into fields/constructor params |
| AOP / `@Transactional` | Wrap beans in JDK dynamic proxies after creation |
| Keep-alive | Loop on the socket until `Connection: close` or idle timeout |
| Virtual threads | One-line executor change on Java 21 |
| Scanned filters | Make `Filter` implementations `@Component`s and auto-register them |
| Persistence | A JDBC `UserRepository` behind the same API, with HikariCP |

---

## Rapid-fire

- **Bean scope default in Spring?** Singleton. Others: prototype, request, session, application.
- **Prototype bean injected into a singleton?** Injected once → use `ObjectProvider`/`@Lookup` for a fresh one per call.
- **`@Autowired` on an interface with no implementation?** Startup failure (`NoSuchBeanDefinitionException`); here `BeanCreationException`.
- **Where does `@PostConstruct` come from?** `jakarta.annotation` (formerly `javax.annotation`); processed by `CommonAnnotationBeanPostProcessor`.
- **What does `Connection: close` mean?** The server closes the TCP connection after this response.
- **What's in a fat JAR?** Your classes plus all dependencies (maven-shade here; Spring Boot nests JARs with its own loader).
- **Why `Class.forName(name, false, loader)`?** Load without running static initializers.
- **What's `Pattern.quote`?** Escapes a literal string for use in a regex (`\Q…\E`).
- **Why `LinkedHashMap` for JSON responses?** Predictable key order in the output.
