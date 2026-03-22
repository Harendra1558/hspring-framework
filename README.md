# 🚀 HSpring Framework

**Learn Spring Boot Internals by Building One from Scratch.**

This is not a library. This is a **learning tool**. Every file implements a real Spring Boot concept — from `@SpringBootApplication` to `ResponseEntity<T>` — with detailed comments explaining *what* it does, *why* it exists, and *how* the real framework does it differently.

> **Who is this for?**  
> Java developers who can *use* Spring Boot but want to understand *how* it works behind the `@` annotations.

---

## ⚡ Quick Start (2 minutes)

```bash
# 1. Clone
git clone https://github.com/Harendra1558/hspring-framework.git
cd hspring-framework

# 2. Build
mvn clean compile

# 3. Run
mvn exec:java -Dexec.mainClass="com.project.MultiThreadedWebServer.Application"

# 4. Open browser
# → http://localhost:8080
```

Or open the project in **IntelliJ IDEA** → Run `Application.java` → Visit `http://localhost:8080`.

### Try the API

```bash
# Get a user (teaches: Path Variables + ResponseEntity)
curl http://localhost:8080/api/users/1

# Create a user (teaches: Request Body + Validation + 201 Created)
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@mail.com"}'

# Delete a user (teaches: Exception Handling when user not found)
curl -X DELETE http://localhost:8080/api/users/999
```

---

## 📖 How to Learn from This Project

### The Golden Rule

> **Don't just read the code. Debug it.**  
> Put a breakpoint on `ApplicationContext.java` line 118 (the constructor) and step through every method. Watch beans being created, configured, and wired together *in real-time*.

### Learning Order (Read files in this exact sequence)

The project is designed so each file builds on the previous one. Follow this path:

---

### 🔵 Phase 1: "What are annotations?" (10 min)

Start here. Annotations are just labels — they do nothing by themselves.

| # | File | You'll Learn |
|---|------|------------|
| 1 | [`annotations/Component.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Component.java) | How `@Target` and `@Retention` work. Why `RUNTIME` retention is required. |
| 2 | [`annotations/Service.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Service.java) | That `@Service` is just `@Component` with a different name. Both do the same thing. |
| 3 | [`annotations/RestController.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/RestController.java) | That `@RestController` carries a `value()` for the base URL path. |

**Key Takeaway:** Annotations are metadata. The *framework code* reads them using reflection and acts on them.

---

### 🟢 Phase 2: "How does Spring find my classes?" (15 min)

This is where the magic starts — the IoC container.

| # | File | You'll Learn |
|---|------|------------|
| 4 | [`core/ApplicationContext.java`](src/main/java/com/project/MultiThreadedWebServer/core/ApplicationContext.java) | **THE MOST IMPORTANT FILE.** The entire bean lifecycle in 6 steps. Read the constructor line by line. |

**What to focus on in ApplicationContext:**

```
Step 0 → Load application.properties
Step 1 → Component Scanning (find .class files, load via Class.forName)
Step 2 → Bean Creation (call newInstance() via reflection)
Step 3 → @Value Injection (read properties, set fields)
Step 4 → @Autowired Injection (find matching bean, set field)
Step 5 → @PostConstruct (call init methods)
```

**Debug exercise:** Add a breakpoint on Step 1, Step 2, and Step 4. Watch the `beansByType` map grow as beans are created, then watch fields get populated during injection.

---

### 🟡 Phase 3: "How does @Value and @Autowired work?" (15 min)

Now see the injection happen — properties from files, objects from the container.

| # | File | You'll Learn |
|---|------|------------|
| 5 | [`core/PropertiesLoader.java`](src/main/java/com/project/MultiThreadedWebServer/core/PropertiesLoader.java) | How `application.properties` is read from the classpath. |
| 6 | [`annotations/Value.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Value.java) | The `@Value("key")` annotation definition. |
| 7 | [`annotations/Autowired.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Autowired.java) | The `@Autowired` annotation definition. |
| 8 | [`annotations/Qualifier.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Qualifier.java) | How `@Qualifier` resolves ambiguity (type-based vs name-based lookup). |
| 9 | [`service/UserService.java`](src/main/java/com/project/MultiThreadedWebServer/service/UserService.java) | **See it in action:** `@Value`, `@PostConstruct`, `@PreDestroy` all in one class. |

**Key experiment:** In `UserService`, try moving the `createUser()` calls from `@PostConstruct init()` into the constructor. Run the app. You'll see `defaultRole` is `null` in the constructor but `"USER"` in `@PostConstruct`. That's the whole point!

---

### 🟠 Phase 4: "How does @Configuration + @Bean work?" (10 min)

How to register third-party objects (like Jackson's ObjectMapper) as beans.

| # | File | You'll Learn |
|---|------|------------|
| 10 | [`annotations/Configuration.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Configuration.java) | The `@Configuration` annotation. |
| 11 | [`annotations/Bean.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/Bean.java) | The `@Bean` annotation. |
| 12 | [`config/AppConfig.java`](src/main/java/com/project/MultiThreadedWebServer/config/AppConfig.java) | Factory method that creates a configured `ObjectMapper`. The return value becomes a bean. |

**Why this matters:** You can't put `@Component` on classes you don't own (library code). `@Bean` solves this.

---

### 🔴 Phase 5: "How do HTTP requests reach my controller?" (20 min)

URL matching, request parsing, and the `ResponseEntity` return pattern.

| # | File | You'll Learn |
|---|------|------------|
| 13 | [`core/HttpRequest.java`](src/main/java/com/project/MultiThreadedWebServer/core/HttpRequest.java) | How raw HTTP text is parsed into method, path, headers, query params, body. |
| 14 | [`core/HttpResponse.java`](src/main/java/com/project/MultiThreadedWebServer/core/HttpResponse.java) | How response status + headers + body are written back to the socket. |
| 15 | [`core/ResponseEntity.java`](src/main/java/com/project/MultiThreadedWebServer/core/ResponseEntity.java) | Spring's `ResponseEntity<T>` — wraps body + status code. Controllers return this instead of writing manually. |
| 16 | [`core/RouteResolver.java`](src/main/java/com/project/MultiThreadedWebServer/core/RouteResolver.java) | How `/api/users/{id}` becomes a regex, how path variables are extracted, and how `ResponseEntity` return values are auto-serialized to JSON. |

**Key concept:** The `RouteResolver.invokeHandler()` method checks if the controller returns `ResponseEntity` or `void`. If it returns `ResponseEntity`, the framework auto-serializes the body to JSON. This is how Spring's `@ResponseBody` works!

---

### 🟣 Phase 6: "How does the whole app start?" (15 min)

The entry point — equivalent to `SpringApplication.run()`.

| # | File | You'll Learn |
|---|------|------------|
| 17 | [`annotations/SpringBootApplication.java`](src/main/java/com/project/MultiThreadedWebServer/annotations/SpringBootApplication.java) | The meta-annotation that combines `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`. |
| 18 | [`Application.java`](src/main/java/com/project/MultiThreadedWebServer/Application.java) | Your main class. Just `@SpringBootApplication` + `main()` — exactly like real Spring Boot. |
| 19 | [`core/HSpringApplication.java`](src/main/java/com/project/MultiThreadedWebServer/core/HSpringApplication.java) | The orchestrator. Creates IoC container, routes, filters, and embedded server — like `SpringApplication`. |
| 20 | [`WebServer.java`](src/main/java/com/project/MultiThreadedWebServer/WebServer.java) | Pure embedded server (like Tomcat). Only handles TCP/HTTP. No `main()`, no IoC, no annotations. |

**Compare with real Spring Boot:**
```
 Real Spring Boot                         HSpring
 ─────────────────                        ───────
 MyApp.java                        →   Application.java
   @SpringBootApplication                  @SpringBootApplication
   main() { SpringApplication.run() }      main() { HSpringApplication.run() }

 SpringApplication                  →   HSpringApplication
   Creates ApplicationContext              Creates ApplicationContext
   Creates TomcatWebServer                 Creates WebServer

 TomcatWebServer                    →   WebServer
   Just handles HTTP                       Just handles HTTP
   No app logic, no DI                     No app logic, no DI
```

---

### ⚪ Phase 7: "What about controllers?" (10 min)

Now see everything come together in actual endpoints.

| # | File | You'll Learn |
|---|------|------------|
| 21 | [`model/User.java`](src/main/java/com/project/MultiThreadedWebServer/model/User.java) | Why POJOs are better than `Map<String, Object>`. Type-safe domain modeling. |
| 22 | [`controller/UserController.java`](src/main/java/com/project/MultiThreadedWebServer/controller/UserController.java) | **The Spring way:** methods return `ResponseEntity.ok(body)` and `ResponseEntity.created(body)`. Framework auto-serializes. |
| 23 | [`controller/HomeController.java`](src/main/java/com/project/MultiThreadedWebServer/controller/HomeController.java) | The `void(req, resp)` style for HTML responses. Shows `@Value` injection for app name/version. |

**Notice the difference:**
```java
// HomeController (HTML — uses void style)
public void home(HttpRequest req, HttpResponse resp) {
    resp.html("<h1>Hello</h1>");
}

// UserController (JSON — uses ResponseEntity style, like real Spring!)
public ResponseEntity<?> getUser(HttpRequest req) {
    User user = service.getUserById(id);
    return ResponseEntity.ok(user);  // Auto-serialized to JSON!
}
```

---

### ⚫ Phase 8: "Filters and Error Handling" (10 min)

Cross-cutting concerns — things that happen on *every* request.

| # | File | You'll Learn |
|---|------|------------|
| 24 | [`filter/Filter.java`](src/main/java/com/project/MultiThreadedWebServer/filter/Filter.java) | The filter interface — `preHandle()` and `postHandle()`. |
| 25 | [`filter/FilterChain.java`](src/main/java/com/project/MultiThreadedWebServer/filter/FilterChain.java) | How multiple filters are executed in order (and reversed for post-handle). |
| 26 | [`filter/LoggingFilter.java`](src/main/java/com/project/MultiThreadedWebServer/filter/LoggingFilter.java) | Logs request duration using `ThreadLocal` for thread-safety. |
| 27 | [`exception/GlobalExceptionHandler.java`](src/main/java/com/project/MultiThreadedWebServer/exception/GlobalExceptionHandler.java) | Like `@ControllerAdvice` — catches exceptions and returns proper error JSON. |

---

## 🧠 All 16 Spring Boot Concepts in One Table

| # | Concept | HSpring File | Real Spring Equivalent |
|---|---------|-------------|----------------------|
| 1 | `@SpringBootApplication` | `annotations/SpringBootApplication.java` | `@SpringBootApplication` |
| 2 | `SpringApplication.run()` | `core/HSpringApplication.java` | `SpringApplication` |
| 3 | IoC Container | `core/ApplicationContext.java` | `ApplicationContext` |
| 4 | Component Scanning | `ApplicationContext.scanPackage()` | `@ComponentScan` |
| 5 | `@Autowired` | `annotations/Autowired.java` | `@Autowired` |
| 6 | `@Qualifier` | `annotations/Qualifier.java` | `@Qualifier` |
| 7 | `@Value` | `annotations/Value.java` | `@Value` |
| 8 | `@Configuration` + `@Bean` | `config/AppConfig.java` | `@Configuration` + `@Bean` |
| 9 | `@PostConstruct` | `annotations/PostConstruct.java` | `@PostConstruct` |
| 10 | `@PreDestroy` | `annotations/PreDestroy.java` | `@PreDestroy` |
| 11 | `ResponseEntity<T>` | `core/ResponseEntity.java` | `ResponseEntity` |
| 12 | Auto-serialization | `RouteResolver.invokeHandler()` | `@ResponseBody` + `HttpMessageConverter` |
| 13 | Route Mapping | `core/RouteResolver.java` | `DispatcherServlet` + `HandlerMapping` |
| 14 | Filter Chain | `filter/FilterChain.java` | `Filter` / `HandlerInterceptor` |
| 15 | Exception Handler | `exception/GlobalExceptionHandler.java` | `@ControllerAdvice` |
| 16 | Externalized Config | `application.properties` | `application.properties` |
| 17 | Embedded Server | `WebServer.java` | `TomcatWebServer` / `JettyWebServer` |

---

## 📁 Project Structure

```
src/main/java/com/project/MultiThreadedWebServer/
│
├── Application.java                   # 🚀 YOUR MAIN CLASS — @SpringBootApplication + main()
├── WebServer.java                     # 🌐 EMBEDDED SERVER — like Tomcat (only HTTP/TCP code)
│
├── annotations/                       # 📝 All custom annotations
│   ├── SpringBootApplication.java     # Meta-annotation (combines scanning + config)
│   ├── Component.java                 # General managed bean
│   ├── Service.java                   # Service layer marker
│   ├── RestController.java            # REST controller marker
│   ├── Configuration.java             # Java config class marker
│   ├── Bean.java                      # Factory method marker
│   ├── Autowired.java                 # Dependency injection
│   ├── Qualifier.java                 # Bean disambiguation
│   ├── Value.java                     # Property injection
│   ├── PostConstruct.java             # Init after injection
│   ├── PreDestroy.java                # Cleanup on shutdown
│   ├── GetMapping.java                # HTTP GET route
│   ├── PostMapping.java               # HTTP POST route
│   ├── PutMapping.java                # HTTP PUT route
│   └── DeleteMapping.java             # HTTP DELETE route
│
├── core/                              # 🔧 Framework engine (the internals)
│   ├── HSpringApplication.java        # THE ORCHESTRATOR — like SpringApplication.run()
│   ├── ApplicationContext.java        # IoC container — THE HEART
│   ├── PropertiesLoader.java          # Reads application.properties
│   ├── RouteResolver.java             # URL → controller method mapping
│   ├── ResponseEntity.java            # Response wrapper (status + body)
│   ├── HttpRequest.java               # Parses incoming HTTP
│   └── HttpResponse.java              # Builds outgoing HTTP
│
├── config/                            # ⚙️ @Configuration classes
│   └── AppConfig.java                 # Defines ObjectMapper @Bean
│
├── model/                             # 📦 Domain objects
│   └── User.java                      # User POJO
│
├── controller/                        # 🎮 REST controllers
│   ├── UserController.java            # Returns ResponseEntity (Spring way!)
│   └── HomeController.java            # Returns HTML (shows @Value)
│
├── service/                           # 💼 Business logic
│   └── UserService.java               # @Value + @PostConstruct + @PreDestroy
│
├── filter/                            # 🔍 Request interceptors
│   ├── Filter.java                    # Filter interface
│   ├── FilterChain.java               # Filter orchestration
│   └── LoggingFilter.java             # Logs request/response
│
└── exception/                         # ⚠️ Error handling
    ├── GlobalExceptionHandler.java     # Like @ControllerAdvice
    ├── NotFoundException.java          # 404 error
    ├── ValidationException.java        # 400 error
    └── UnauthorizedException.java      # 401 error

src/main/resources/
└── application.properties              # server.port, app.name, user.default-role
```

---

## 🛠️ Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 17 | Language (records, text blocks, pattern matching) |
| Maven | Build & dependency management |
| Jackson | JSON serialization (`ObjectMapper`) |
| SLF4J + Logback | Logging |
| Java Reflection API | Annotation processing, DI, bean creation |
| Java ServerSocket | Raw TCP networking (no Tomcat) |

---

## 🤝 Contributing

Want to add a new Spring Boot concept? Follow the rules:

1. **One concept per file** — don't combine multiple ideas
2. **No duplicate features** — if GET already exists, don't add another GET
3. **Heavy documentation** — explain *why*, not just *what*
4. **Keep it simple** — this is for learning, not production

---

**Author:** [Harendra](https://github.com/Harendra1558)

> 💡 **Best way to learn:** Put a breakpoint on `ApplicationContext.java` line 118 → step through every method → watch beans being created, configured, and wired together in real-time!
