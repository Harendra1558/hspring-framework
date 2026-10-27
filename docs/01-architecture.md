# 1. Architecture Overview

## What the project is

HSpring re-implements the core ideas of Spring Boot so you can **read and step through them**:

- An **IoC container** that discovers classes by annotation, creates them, and wires them together.
- A **web layer** that maps HTTP requests to controller methods using annotations.
- An **embedded HTTP server** that accepts TCP connections and processes requests on a thread pool.
- **Cross-cutting concerns**: a filter chain and a global exception handler.

It ships with a small demo app: a user CRUD REST API (`/api/users`), a home page (`/`) and a health check (`/health`).

## Package layout

```
com.project.MultiThreadedWebServer
├── WebServer.java              Entry point + server (like SpringApplication + embedded Tomcat)
├── Client.java                 Raw-socket demo client that exercises every endpoint
│
├── annotations/                The "metadata" the framework reads with reflection
│   ├── Component, Service, RestController     → "manage this class as a bean"
│   ├── Autowired                              → "inject a dependency here"
│   ├── PostConstruct, PreDestroy              → lifecycle callbacks
│   └── GetMapping, PostMapping, PutMapping, DeleteMapping → routing
│
├── core/                       The framework engine
│   ├── ApplicationContext      IoC container (scan → create → inject → init)
│   ├── BeanCreationException   Startup failures (missing/ambiguous/circular deps)
│   ├── RouteResolver           URL pattern → controller method dispatch
│   ├── HttpRequest             Parses raw bytes into method/path/headers/body
│   └── HttpResponse            Builds and writes the HTTP response bytes
│
├── filter/                     Request interceptors
│   ├── Filter                  preHandle / postHandle / afterCompletion
│   ├── FilterChain             Runs filters in order (and reverse order on the way out)
│   └── LoggingFilter           Logs each request and its duration
│
├── exception/                  Centralized error handling
│   ├── GlobalExceptionHandler  Exception type → HTTP status + JSON body
│   └── NotFoundException, ValidationException, UnauthorizedException
│
└── (demo application)
    ├── controller/             HomeController, UserController
    ├── service/                UserService       (business logic)
    ├── repository/             UserRepository    (thread-safe in-memory storage)
    └── model/                  User              (immutable record)
```

The **framework** (`annotations`, `core`, `filter`, `exception`, `WebServer`) knows nothing about users.
The **demo app** (`controller`, `service`, `repository`, `model`) is discovered at runtime by component scanning,
exactly as your classes are discovered by Spring Boot.

## Component diagram

```mermaid
flowchart LR
    Client((HTTP client)) -->|TCP| Acceptor[Acceptor thread<br/>ServerSocket.accept]
    Acceptor -->|submit| Pool[Worker thread pool<br/>hspring-worker-N]
    Pool --> Parse[HttpRequest.parse]
    Parse --> Filters[FilterChain]
    Filters --> Router[RouteResolver]
    Router -->|reflection invoke| Controller[UserController / HomeController]
    Controller --> Service[UserService]
    Service --> Repo[UserRepository]
    Controller -->|json| Response[HttpResponse]
    Router -. exception .-> GEH[GlobalExceptionHandler]
    Filters -. exception .-> GEH
    GEH --> Response
    Response -->|bytes| Client

    subgraph Container[ApplicationContext - IoC container]
        Controller
        Service
        Repo
    end
```

## Startup sequence

What happens when you run `java -jar target/hspring-framework-1.1.0.jar`:

```mermaid
sequenceDiagram
    participant Main as WebServer.main
    participant Ctx as ApplicationContext
    participant RR as RouteResolver
    participant FC as FilterChain
    participant SS as ServerSocket

    Main->>Main: resolve port/threads (CLI arg → -D property → env var → default)
    Main->>Main: register JVM shutdown hook (graceful stop)
    Main->>Ctx: new ApplicationContext("com.project.MultiThreadedWebServer")
    Ctx->>Ctx: 1. scan classpath (directories and JARs) for .class files
    Ctx->>Ctx: 2. create beans, constructor-injecting dependencies first (recursive)
    Ctx->>Ctx: 3. inject @Autowired fields
    Ctx->>Ctx: 4. call @PostConstruct methods (UserService seeds 3 users)
    Main->>RR: registerController(each @RestController)
    RR->>RR: read @GetMapping etc., compile each path to a regex
    Main->>FC: addFilter(LoggingFilter)
    Main->>Main: create GlobalExceptionHandler and fixed thread pool
    Main->>SS: bind port, start "hspring-acceptor" thread
    Main->>Main: run() joins the acceptor thread (blocks until stop)
```

The startup log shows each step:

```
Found 20 classes to analyze
  ✓ Registered @RestController: HomeController
  ✓ Registered @RestController: UserController
  ✓ Registered @Component: UserRepository as 'userRepository'
  ✓ Registered @Service: UserService as 'userService' (constructor-injected 1 dependencies)
  → Injected UserService into UserController.userService
  ⚙ Called @PostConstruct UserService.loadSampleData()
ApplicationContext ready with 4 beans
  GET /api/users/{id} → UserController.getUserById()
  ...
```

## Request lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant W as Worker thread
    participant F as FilterChain
    participant R as RouteResolver
    participant H as Controller method
    participant E as GlobalExceptionHandler

    C->>W: bytes over TCP
    W->>W: HttpRequest.parse (request line, headers, body by Content-Length)
    W->>F: applyPreHandle (in order)
    alt a filter returns false
        W-->>C: 403 (unless the filter already responded)
    end
    W->>R: resolve(request, response)
    alt route matches
        R->>R: extract path variables from regex groups
        R->>H: Method.invoke(controller, request, response)
        H-->>C: response.json(...)
    else path exists for other methods
        W-->>C: 405 + Allow header
    else no route
        W-->>C: 404
    end
    W->>F: applyPostHandle (reverse order)
    Note over W,E: any exception in parse / filter / controller
    W->>E: handleException → 400 / 401 / 404 / 500 JSON
    W->>F: applyAfterCompletion (always, in finally)
    W->>W: close socket
```

## Key design decisions

| Decision | Why |
|---|---|
| **Annotations + reflection** for wiring and routing | It's how Spring works; you see the mechanism instead of the magic |
| **Fixed thread pool**, one request per connection (`Connection: close`) | Simple and predictable; mirrors Tomcat's blocking connector model |
| **Separate acceptor thread** + `start()`/`run()` split | Lets tests start a real server on port 0 and stop it |
| **Byte-level HTTP parsing in UTF-8** | `Content-Length` counts bytes; reading characters breaks on non-ASCII bodies |
| **Exceptions flow to one handler** | Controllers stay clean; consistent error JSON |
| **Fail-fast container** (`BeanCreationException`) | A broken wiring stops startup instead of failing on the first request |
| **Immutable `User` record + atomic map operations** | Many worker threads share the same data |

## What is intentionally *not* implemented

Knowing the limits is part of understanding the design (and a common interview follow-up):

- HTTP keep-alive, chunked transfer encoding, HTTPS, HTTP/2.
- Bean scopes other than singleton; `@Qualifier`/`@Primary`; AOP proxies; `@Transactional`.
- Argument resolution (`@PathVariable`, `@RequestBody` parameters): handlers receive `HttpRequest`/`HttpResponse` instead.
- Externalized configuration files (`application.properties`); only port/thread count are configurable.
- Real persistence; `UserRepository` is in-memory.

See [Interview guide → "What would you improve?"](08-interview-guide.md#what-would-you-improve-next) for how you'd add each one.
