# 3. HTTP Server & Threading

Sources: [`WebServer.java`](../src/main/java/com/project/MultiThreadedWebServer/WebServer.java),
[`core/HttpRequest.java`](../src/main/java/com/project/MultiThreadedWebServer/core/HttpRequest.java),
[`core/HttpResponse.java`](../src/main/java/com/project/MultiThreadedWebServer/core/HttpResponse.java)

## HTTP is just text over TCP

A request on the wire:

```
POST /api/users?notify=true HTTP/1.1\r\n        ← request line: METHOD SP URI SP VERSION
Host: localhost:8080\r\n                          ← headers, one per line
Content-Type: application/json\r\n
Content-Length: 44\r\n
\r\n                                              ← empty line = end of headers
{"name":"Alice","email":"alice@example.com"}    ← body: exactly Content-Length BYTES
```

A response:

```
HTTP/1.1 201 Created\r\n
Content-Type: application/json\r\n
Content-Length: 97\r\n
Connection: close\r\n
Server: HSpring/1.1\r\n
Location: /api/users/4\r\n
\r\n
{"success":true,...}
```

Everything in `HttpRequest` and `HttpResponse` is about producing and consuming exactly these bytes.

## Threading model

```
                      ┌──────────────────────────┐
  connections ──────► │  hspring-acceptor thread │  loop: socket = serverSocket.accept()
                      └────────────┬─────────────┘        threadPool.execute(handle(socket))
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                  ▼                  ▼
        hspring-worker-1   hspring-worker-2  …  hspring-worker-N     (fixed pool, default 10)
        parse → filters → route → controller → respond → close
```

- **One acceptor thread** only accepts connections, so accepting never waits on slow request processing.
- **A fixed pool** (`Executors.newFixedThreadPool`) bounds concurrency. When all workers are busy, new connections
  wait in the executor's queue instead of creating unbounded threads (which would exhaust memory).
- **Named threads** (`hspring-worker-3`) make logs and thread dumps readable.
- **One request per connection**: the server sends `Connection: close` and closes the socket. No keep-alive.

This is the classic **thread-per-request** model, the same as Tomcat's blocking (BIO) connector.

### Thread safety follows from the model

Every bean is a **singleton shared by all workers**, so:

| Shared thing | How it's made safe |
|---|---|
| `UserRepository` storage | `ConcurrentHashMap`; every operation is a single atomic call (`computeIfPresent`, `remove`) |
| ID generation | `AtomicLong.getAndIncrement()`: no two requests get the same id |
| `User` objects | Immutable `record`; updates create a new object |
| `LoggingFilter` start time | `ThreadLocal`, cleared in `afterCompletion` (always runs) |
| Filter list | `CopyOnWriteArrayList`: lock-free iteration on every request, rare writes copy |
| `ObjectMapper` | Thread-safe after configuration, so one shared static instance |
| Routes | Written once at startup, then only read |

> **Classic race (fixed here):** `if (users.containsKey(id)) users.remove(id);` is *check-then-act*.
> Two threads can both pass the check. `users.remove(id) != null` does both steps atomically.

## Parsing a request (`HttpRequest.parse`)

1. **Request line**: split on whitespace → method, URI. Anything malformed throws
   `IllegalArgumentException` → **400 Bad Request**.
2. **Path vs. query**: split the URI at `?`.
3. **Query params**: split on `&` and `=`, then `URLDecoder.decode(…, UTF_8)` both key and value
   (`%C3%A9` → `é`, `+` → space).
4. **Headers**: read lines until an empty line. Names are stored in a
   `TreeMap(String.CASE_INSENSITIVE_ORDER)` because HTTP header names are case-insensitive
   (`content-length` = `Content-Length`). The space after `:` is optional.
5. **Body** (POST/PUT/PATCH): read **exactly `Content-Length` bytes** with `InputStream.readNBytes`, then decode
   as UTF-8.

### Why bytes, not a `BufferedReader`?

`Content-Length` counts **bytes**. A `Reader` counts **characters**. In UTF-8, `é` is 2 bytes and `日` is 3.

```
body = {"name":"日本"}      → 13 characters, but 17 bytes
Content-Length: 17
reader.read(chars, 0, 17)  → waits for 17 *characters*; the client only sends 13 → request hangs
```

The original implementation read characters, and on Windows it also used the platform's default charset
(Cp1252), so non-ASCII text was corrupted as well. Reading raw bytes and decoding with an explicit charset fixes both.
The response side has the mirror-image bug: `Content-Length` must be `body.getBytes(UTF_8).length`, not `body.length()`.

### Defensive limits

| Limit | Value | Protects against |
|---|---|---|
| Request line / header line | 8 KB | Memory exhaustion via giant headers |
| Number of headers | 100 | Header flooding |
| Body size | 1 MB | Huge uploads |
| Socket read timeout | 10 s | **Slowloris**: clients that connect and never finish sending, tying up workers |

Without the read timeout, 10 idle connections would block all 10 workers forever.

## Writing a response (`HttpResponse`)

A fluent builder: `response.status(201).header("Location", "/api/users/4").json(body)`.

`send()` builds the status line and headers, encodes them and the body as UTF-8, writes the bytes, and flushes.
A `headersSent` flag makes a second `send()` throw. You can't change a response after it's on the wire;
that's what servlet containers call a *committed* response.

## Error handling in the connection handler

```java
try {
    response = new HttpResponse(out);       // created BEFORE parsing → malformed requests still get a reply
    request  = HttpRequest.parse(in);
    ... filters, routing ...
} catch (Exception e) {
    exceptionHandler.handleException(request, response, e);   // stream still OPEN here
} finally {
    filterChain.applyAfterCompletion(...);
    clientSocket.close();                    // closed only AFTER the error reply is written
}
```

> **The bug this replaced:** the streams used to be opened in `try (reader; writer) { … } catch (…) { … }`.
> Java closes try-with-resources resources **before** the `catch` block runs, so the error response was written to
> an already-closed `PrintWriter` (which swallows errors silently). Every 4xx/5xx reached the client as an
> **empty reply**. See [Bugs & lessons](07-bugs-and-lessons.md#1-every-error-response-was-empty).

## Configuration

`HSpringApplication` reads the server settings through `PropertiesLoader`, which checks sources in
priority order, like Spring's property sources:

| Setting | CLI arg | System property | Env variable | `application.properties` |
|---|---|---|---|---|
| Port | `--server.port=9090` | `-Dserver.port=9090` | `SERVER_PORT`, then `PORT` | `server.port=8080` |
| Worker threads | `--server.thread-pool-size=20` | `-Dserver.thread-pool-size=20` | `SERVER_THREAD_POOL_SIZE` | `server.thread-pool-size=10` |

`PORT` is what Railway, Render and Heroku inject, so the JAR runs on those platforms unchanged.
Port `0` asks the OS for any free port; `getPort()` returns the real one (used by the integration test).

## Graceful shutdown

`HSpringApplication.run()` registers a JVM shutdown hook (runs on Ctrl+C / SIGTERM) that calls
`HSpringApplication.stop()`:

1. `WebServer.stop()` closes the `ServerSocket` → the acceptor's `accept()` throws, and the loop exits.
2. `threadPool.shutdown()` → no new tasks; in-flight requests finish (up to 5 s, then `shutdownNow()`).
3. `applicationContext.close()` → `@PreDestroy` methods run.

`stop()` is idempotent, so a test and the shutdown hook can both call it safely.

## How real servers differ

| Topic | HSpring | Production servers |
|---|---|---|
| I/O model | Blocking `java.net` sockets, thread per request | Tomcat NIO / Netty: a few event-loop threads multiplex thousands of connections with `java.nio` selectors |
| Java 21+ | Platform threads | **Virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`): blocking code that scales like async. Spring Boot 3.2+: `spring.threads.virtual.enabled=true` |
| Connections | `Connection: close` | Keep-alive and pipelining, HTTP/2 multiplexing |
| Bodies | `Content-Length` only | Also `Transfer-Encoding: chunked`, streaming, multipart |
| Backpressure | Unbounded executor queue | Bounded accept queue, max-connections, 503 when overloaded |

Switching this project to virtual threads on Java 21 is a one-line change in `WebServer.start()`:
replace the fixed pool with `Executors.newVirtualThreadPerTaskExecutor()`.
