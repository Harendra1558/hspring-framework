# 🚀 HSpring Framework

A **lightweight implementation of Spring Boot internals** built from scratch in Java 17. This project demonstrates how Spring Boot works under the hood, making it perfect for learning and understanding the core concepts of the Spring Framework.

> **📚 Educational Purpose:** This project is designed for developers who want to understand what happens "behind the scenes" when you use Spring Boot. Every class is heavily documented with explanations of the concepts.

## 📋 Table of Contents

- [Overview](#-overview)
- [Why This Project?](#-why-this-project)
- [Features](#-features)
- [Project Structure](#-project-structure)
- [Core Concepts Explained](#-core-concepts-explained)
- [How It Works](#-how-it-works)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Learning Path](#-learning-path)
- [License](#-license)

## 🎯 Overview

This project is a **mini Spring Boot framework** that implements the core concepts:

| Concept | What It Does | Why It Matters |
|---------|--------------|----------------|
| **IoC Container** | Manages object creation and lifecycle | You don't manually create objects with `new` |
| **Dependency Injection** | Automatically wires dependencies | Classes don't need to know how to find their dependencies |
| **Web Server** | Handles HTTP connections with threads | Processes multiple requests concurrently |
| **MVC Architecture** | Separates Controllers, Services, Routing | Clean, maintainable code structure |
| **Annotations** | Declarative configuration | Configuration via metadata, not XML |
| **Filters** | Intercepts requests/responses | Cross-cutting concerns (logging, auth) |
| **Exception Handling** | Centralized error management | Consistent error responses |

## 🤔 Why This Project?

When you write a Spring Boot application, you typically write:

```java
@RestController
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

But have you ever wondered:
- **How does `@Autowired` magically inject the service?**
- **How does `@GetMapping` know which method to call?**
- **How does `{id}` get extracted from the URL?**
- **Where does the `UserService` instance come from?**

This project answers all these questions by implementing them from scratch!

## ✨ Features

| Feature | Description | Spring Equivalent |
|---------|-------------|-------------------|
| `ApplicationContext` | IoC Container that manages beans | `ApplicationContext` |
| `@Component` | Marks a class as a managed bean | `@Component` |
| `@Service` | Marks a service layer class | `@Service` |
| `@RestController` | Marks a REST API controller | `@RestController` |
| `@Autowired` | Injects dependencies automatically | `@Autowired` |
| `@GetMapping` | Maps HTTP GET requests | `@GetMapping` |
| `@PostMapping` | Maps HTTP POST requests | `@PostMapping` |
| `@PutMapping` | Maps HTTP PUT requests | `@PutMapping` |
| `@DeleteMapping` | Maps HTTP DELETE requests | `@DeleteMapping` |
| Path Variables | `/users/{id}` pattern matching | `@PathVariable` |
| Query Parameters | `?key=value` parsing | `@RequestParam` |
| `Filter` | Request/Response interceptors | `Filter` / `HandlerInterceptor` |
| `GlobalExceptionHandler` | Centralized exception handling | `@ControllerAdvice` |
| `HttpRequest` | Request wrapper | `HttpServletRequest` |
| `HttpResponse` | Response wrapper | `HttpServletResponse` |

## 📁 Project Structure

```
src/main/java/com/project/MultiThreadedWebServer/
│
├── WebServer.java              # 🚀 MAIN ENTRY POINT
│                               # Like SpringApplication.run()
│                               # Bootstraps the entire framework
│
├── Client.java                 # 🧪 Test client for API testing
│
├── annotations/                # 📝 CUSTOM ANNOTATIONS
│   │                           # These are markers that the framework reads
│   ├── Component.java          # Marks any managed bean
│   ├── Service.java            # Marks service layer (business logic)
│   ├── RestController.java     # Marks REST controllers
│   ├── Autowired.java          # Marks fields for dependency injection
│   ├── PutMapping.java         # Maps PUT requests
│   └── DeleteMapping.java      # Maps DELETE requests
│
├── core/                       # 🔧 FRAMEWORK CORE
│   │                           # The "engine" that makes everything work
│   ├── ApplicationContext.java # IoC Container - creates & manages beans
│   ├── RouteResolver.java      # Maps URLs to controller methods
│   ├── HttpRequest.java        # Wraps incoming HTTP request data
│   └── HttpResponse.java       # Wraps outgoing HTTP response
│
├── controller/                 # 🎮 REST CONTROLLERS
│   │                           # Handle incoming HTTP requests
│   ├── HomeController.java     # Home page & health check
│   └── UserController.java     # User CRUD operations
│
├── service/                    # 💼 SERVICE LAYER
│   │                           # Business logic lives here
│   └── UserService.java        # User-related operations
│
├── filter/                     # 🔍 REQUEST FILTERS
│   │                           # Intercept requests before/after controller
│   ├── Filter.java             # Interface defining filter contract
│   ├── FilterChain.java        # Manages multiple filters
│   └── LoggingFilter.java      # Logs request/response details
│
├── exception/                  # ⚠️ EXCEPTION HANDLING
│   │                           # Centralized error handling
│   ├── GlobalExceptionHandler.java  # Catches all exceptions
│   ├── NotFoundException.java       # 404 errors
│   ├── ValidationException.java     # Validation errors
│   └── UnauthorizedException.java   # 401 errors
│
├── GetMapping.java             # @GetMapping annotation
└── PostMapping.java            # @PostMapping annotation
```

## 🧠 Core Concepts Explained

### 1. IoC Container (Inversion of Control)

**The Problem:**
```java
// Without IoC - YOU create and manage objects
public class UserController {
    private UserService userService = new UserService();  // Tight coupling!
    private EmailService emailService = new EmailService();
    // What if UserService needs EmailService? Complex dependencies!
}
```

**The Solution (IoC):**
```java
// With IoC - The CONTAINER creates and manages objects
@RestController
public class UserController {
    @Autowired
    private UserService userService;  // Container injects this!
}
```

**How our ApplicationContext works:**
```java
public class ApplicationContext {
    private Map<Class<?>, Object> beans = new HashMap<>();
    
    public ApplicationContext(String basePackage) {
        // STEP 1: Scan for annotated classes
        List<Class<?>> classes = scanPackage(basePackage);
        
        // STEP 2: Create instances (beans)
        for (Class<?> clazz : classes) {
            if (hasComponentAnnotation(clazz)) {
                Object instance = clazz.newInstance();
                beans.put(clazz, instance);
            }
        }
        
        // STEP 3: Inject dependencies
        for (Object bean : beans.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Object dependency = beans.get(field.getType());
                    field.set(bean, dependency);  // Inject!
                }
            }
        }
    }
}
```

### 2. Route Resolution (How URLs map to methods)

**The Problem:**
When a request comes in for `GET /users/123`, how does the framework know to call `UserController.getUserById()`?

**The Solution:**
```java
// Pattern: /users/{id}
// Converted to Regex: /users/([^/]+)
// When /users/123 arrives:
//   1. Matches the regex
//   2. Captures "123" as "id"
//   3. Calls getUserById with id="123"

@GetMapping("/users/{id}")
public void getUserById(HttpRequest request, HttpResponse response) {
    String id = request.getPathVariable("id");  // "123"
}
```

### 3. Filter Chain (Cross-cutting concerns)

**The Problem:**
You want to log every request, check authentication, and measure response time. Do you add this to EVERY controller method?

**The Solution:**
```java
// Filters run BEFORE and AFTER every request
public class LoggingFilter implements Filter {
    @Override
    public boolean preHandle(HttpRequest request, HttpResponse response) {
        System.out.println("Request: " + request.getPath());
        return true;  // Continue processing
    }
    
    @Override
    public void postHandle(HttpRequest request, HttpResponse response) {
        System.out.println("Response sent!");
    }
}
```

**Execution Order:**
```
Request → Filter1.pre → Filter2.pre → Controller → Filter2.post → Filter1.post → Response
```

## 🔄 How It Works

### Request Lifecycle (What happens when you hit an endpoint)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  CLIENT                                                                 │
│    │                                                                    │
│    │  HTTP Request: GET /api/users/123                                 │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  THREAD POOL                                                            │
│    │                                                                    │
│    │  Worker thread picks up the connection                            │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  HTTP PARSER                                                            │
│    │                                                                    │
│    │  Creates HttpRequest object with:                                 │
│    │    - method: "GET"                                                │
│    │    - path: "/api/users/123"                                       │
│    │    - headers, body, etc.                                          │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  FILTER CHAIN (preHandle)                                               │
│    │                                                                    │
│    │  LoggingFilter: Logs "Incoming GET /api/users/123"               │
│    │  AuthFilter: Checks authentication (if configured)               │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  ROUTE RESOLVER                                                         │
│    │                                                                    │
│    │  Pattern: /api/users/{id} matches /api/users/123                  │
│    │  Extracts: id = "123"                                             │
│    │  Found: UserController.getUserById()                              │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  CONTROLLER                                                             │
│    │                                                                    │
│    │  UserController.getUserById(request, response)                    │
│    │    └── userService.getUserById(123)  // @Autowired service       │
│    │         └── Returns user data                                     │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  FILTER CHAIN (postHandle)                                              │
│    │                                                                    │
│    │  LoggingFilter: Logs "Response 200 in 5ms"                       │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  HTTP RESPONSE                                                          │
│    │                                                                    │
│    │  Writes to socket:                                                │
│    │    HTTP/1.1 200 OK                                                │
│    │    Content-Type: application/json                                 │
│    │    {"id": 123, "name": "John"}                                    │
│    ▼                                                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  CLIENT                                                                 │
│    │                                                                    │
│    │  Receives JSON response                                           │
│    ▼                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

### Server Startup (What happens when you run the application)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 1: WebServer.run() called                                         │
│          The journey begins...                                          │
└────────────────────────────────┬────────────────────────────────────────┘
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 2: Create ApplicationContext                                      │
│          - Scans "com.project.MultiThreadedWebServer" package          │
│          - Finds: UserController, HomeController, UserService          │
│          - Creates instances of each                                    │
│          - Injects @Autowired dependencies                             │
└────────────────────────────────┬────────────────────────────────────────┘
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 3: Create RouteResolver                                           │
│          For each controller, scan methods:                             │
│          - @GetMapping("/")           → HomeController.home()          │
│          - @GetMapping("/health")     → HomeController.healthCheck()   │
│          - @GetMapping("/api/users")  → UserController.getAllUsers()   │
│          - etc...                                                       │
└────────────────────────────────┬────────────────────────────────────────┘
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 4: Create FilterChain                                             │
│          - Add LoggingFilter                                            │
│          - (Add more filters as needed)                                 │
└────────────────────────────────┬────────────────────────────────────────┘
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 5: Create Thread Pool                                             │
│          - Fixed pool of 10 worker threads                              │
│          - Ready to handle concurrent requests                          │
└────────────────────────────────┬────────────────────────────────────────┘
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 6: Start ServerSocket                                             │
│          - Binds to port 8080                                           │
│          - Enters infinite loop: accept() → handle → repeat             │
│          - Server is now running! 🎉                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

## 🚀 Getting Started

### Prerequisites

- **Java 17** or higher
- **Maven 3.6+**

### Build & Run

```bash
# Clone the repository
git clone https://github.com/Harendra1558/MultiThreadedWebServer.git
cd MultiThreadedWebServer

# Build the project
mvn clean compile

# Run the server
mvn exec:java -Dexec.mainClass="com.project.MultiThreadedWebServer.WebServer"

# In another terminal, run the test client
mvn exec:java -Dexec.mainClass="com.project.MultiThreadedWebServer.Client"
```

### Or Run from IDE
1. Open the project in IntelliJ IDEA / Eclipse / VS Code
2. Run `WebServer.java` as main class
3. Open `http://localhost:8080` in your browser

You should see a beautiful documentation page!

## 📚 API Documentation

### Base URL: `http://localhost:8080`

| Method | Endpoint | Description | Example |
|--------|----------|-------------|---------|
| GET | `/` | Home page with API docs | `curl http://localhost:8080/` |
| GET | `/health` | Health check with system info | `curl http://localhost:8080/health` |
| GET | `/api/users` | List all users | `curl http://localhost:8080/api/users` |
| GET | `/api/users?limit=5` | List users with limit | `curl http://localhost:8080/api/users?limit=5` |
| GET | `/api/users/{id}` | Get user by ID | `curl http://localhost:8080/api/users/1` |
| POST | `/api/users` | Create new user | See below |
| PUT | `/api/users/{id}` | Update user | See below |
| DELETE | `/api/users/{id}` | Delete user | `curl -X DELETE http://localhost:8080/api/users/1` |

### Example Requests

**Create User:**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "email": "john@example.com"}'
```

**Update User:**
```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "John Updated", "email": "john.updated@example.com"}'
```

## 📖 Learning Path

If you're new to this project, read the source files in this order:

### Step 1: Understand Annotations
1. `annotations/Component.java` - What makes a class a "bean"?
2. `annotations/Service.java` - Specialized component for business logic
3. `annotations/RestController.java` - Specialized component for HTTP endpoints
4. `annotations/Autowired.java` - How dependency injection is requested

### Step 2: Understand the IoC Container
5. `core/ApplicationContext.java` - **THE MOST IMPORTANT FILE**
   - How beans are discovered
   - How beans are created
   - How dependencies are injected

### Step 3: Understand HTTP Handling
6. `core/HttpRequest.java` - How HTTP requests are parsed
7. `core/HttpResponse.java` - How HTTP responses are built
8. `core/RouteResolver.java` - How URLs map to methods

### Step 4: Understand the Web Server
9. `WebServer.java` - How everything is bootstrapped and connected

### Step 5: See It In Action
10. `controller/UserController.java` - Real controller using all features
11. `service/UserService.java` - Business logic layer

### Step 6: Understand Cross-cutting Concerns
12. `filter/Filter.java` - Filter interface
13. `filter/FilterChain.java` - How filters are orchestrated
14. `exception/GlobalExceptionHandler.java` - Centralized error handling

## 🛠️ Tech Stack

| Technology | Purpose | Version |
|------------|---------|---------|
| Java | Programming language | 17 |
| Maven | Build & dependency management | 3.6+ |
| Jackson | JSON serialization/deserialization | 2.13.4 |
| SLF4J + Logback | Logging | 2.0.16 / 1.5.15 |
| Java Reflection API | Annotation processing & DI | Built-in |
| Java NIO | Network I/O | Built-in |

## 🤝 Contributing

Contributions are welcome! If you'd like to add features or improve documentation:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## 📝 License

This project is open source and available under the MIT License.

---

**Author:** Harendra  
**GitHub:** [github.com/Harendra1558](https://github.com/Harendra1558)

---

> 💡 **Tip:** The best way to learn is to add a breakpoint in `ApplicationContext.java` and step through the code during startup. Watch how beans are discovered, created, and wired together!
