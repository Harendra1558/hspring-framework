# 7. Bugs Found & Lessons Learned

A review of the v1.0.0 code found the bugs below. Each was reproduced against the running server before
fixing, and each is now covered by a test. In an interview, *"tell me about a tricky bug you fixed"*
is almost guaranteed, and these are concrete, technical stories.

Format: **Symptom → Root cause → Fix → Lesson.**

---

## 1. Every error response was empty

**Symptom.** `GET /api/users/999`, a POST with a missing email, and `GET /api/users/abc` all returned
**no response at all**: the connection just closed. Clients saw "empty reply from server".

**Root cause.** Two separate bugs stacked on each other:

```java
try (BufferedReader reader = ...; PrintWriter writer = ...) {
    ... routeResolver.resolve(request, response) ...
} catch (Exception e) {
    exceptionHandler.handleException(request, response, e);   // writes to `writer`
}
```

1. **try-with-resources closes resources *before* `catch` runs.** By the time the exception handler wrote the
   error, the `PrintWriter` was closed. `PrintWriter` never throws on write errors (it only sets an internal flag),
   so the failure was completely silent.
2. Even with the writer open, the response would have been wrong: `RouteResolver` caught the
   `InvocationTargetException` from `Method.invoke` and wrapped it in `new RuntimeException("Handler invocation failed")`.
   The controller also wrapped `NotFoundException` in another `RuntimeException`. The global handler only saw a
   generic `RuntimeException`, so **every error would have been a 500**.

**Fix.** Open the streams without try-with-resources and close the socket in `finally`, *after* the error reply.
Unwrap `InvocationTargetException` and rethrow the original cause. Controllers throw domain exceptions and never
wrap them.

**Lessons.**
- Know the exact semantics of try-with-resources: resources are closed when the `try` block exits, before `catch`/`finally`.
- `PrintWriter` swallows `IOException`s; check `checkError()` or write to an `OutputStream` instead.
- Reflection wraps exceptions; frameworks must unwrap them. Spring does it in `InvocableHandlerMethod`.
- Don't wrap exceptions you can't add information to.

---

## 2. The packaged JAR found zero components

**Symptom.** Running from the IDE worked; running `java -jar` logged `Error scanning package` and
`Found 0 classes`. A previous commit "fixed" it by hard-coding `new UserService()`, `new UserController()` and
reflection-based field injection in `WebServer.registerComponentsManually()`, which defeats the point of an IoC
container. Any new controller would silently not be registered.

**Root cause.** Scanning did `new File(resource.toURI())`. Inside a JAR, the resource URL is
`jar:file:/…/app.jar!/com/project/…`. That's not a file-system path, so `File` can't read it.

**Fix.** Use `ClassLoader.getResources()` (all classpath roots) and handle both `file:` (walk the directory) and
`jar:` (iterate `JarFile` entries) URLs. The manual registration was deleted.

**Lesson.** Test the artifact you ship, not just the IDE run. Classpath resources aren't always files.

---

## 3. Non-ASCII text was corrupted, or hung the request

**Symptom.** Creating a user named `Zoë 日本` stored `Zo� ??`.

**Root cause.**
- `InputStreamReader`/`PrintWriter` were created **without a charset**, so they used the platform default
  (Windows-1252 on Java 17/Windows), not UTF-8.
- The body was read as `Content-Length` **characters**, but `Content-Length` counts **bytes**. A multi-byte body
  makes the server wait for characters that never arrive.
- The response's `Content-Length` used `body.getBytes()` (platform charset) while the writer encoded differently,
  so the header could disagree with the bytes actually sent.

**Fix.** Parse the request from the raw `InputStream`: read exactly N bytes, then decode as UTF-8. Write the response
as UTF-8 bytes, with `Content-Length` computed from those same bytes.

**Lesson.** Always name the charset. Bytes and characters are different units; protocols almost always count bytes.

---

## 4. Lost updates and check-then-act races in the user store

**Symptom.** None visible in light testing; it shows up under concurrent load.

**Root cause.**
- Users were `HashMap<String,Object>` objects stored in a `ConcurrentHashMap`. `updateUser` **mutated** the inner,
  non-thread-safe `HashMap` while other threads could be serializing the same map to JSON →
  possible `ConcurrentModificationException` or torn reads.
- `createUser` returned the **internal** map, so callers could mutate stored state.
- `deleteUser` did `if (!users.containsKey(id)) throw …; users.remove(id);`. Two concurrent deletes can both pass
  the check (check-then-act).

**Fix.** An immutable `record User`; updates go through `ConcurrentHashMap.computeIfPresent` (atomic per key);
delete uses `remove(id) != null` (one atomic step). Storage moved into `UserRepository`.

**Lesson.** A thread-safe *container* doesn't make its *contents* thread-safe. Prefer immutability and
single atomic operations over multi-step logic.

---

## 5. `ThreadLocal` leak in `LoggingFilter`

**Root cause.** The start time was stored in a `ThreadLocal` and removed in `postHandle`, which is **skipped when
the controller throws**. Workers are pooled, so the stale value stayed on that thread.

**Fix.** Do the timing and `remove()` in `afterCompletion`, which always runs (it's called from `finally`).

**Lesson.** With thread pools, every `ThreadLocal.set` needs a `remove` in a `finally`-equivalent path. This is a
classic source of memory leaks and data bleeding between users (e.g. security context, MDC).

---

## 6. Filters couldn't reject requests properly

**Root cause.** `FilterChain.applyPreHandle` caught every exception and returned `false`, and the server then
returned without writing anything. An auth filter throwing `UnauthorizedException` produced an empty reply instead of 401.

**Fix.** Let filter exceptions propagate to the `GlobalExceptionHandler`. If a filter returns `false` without
responding, send a default 403.

---

## 7. Smaller issues fixed

| Issue | Fix |
|---|---|
| Header lookup was case-sensitive (`content-length` ignored) | Case-insensitive `TreeMap` |
| Headers without a space after `:` were dropped | Split on the first `:` |
| URL decoding handled only 7 hand-picked escapes | `URLDecoder.decode(…, UTF_8)`; path variables decoded too |
| `?limit=abc` → 500 | 400 validation error |
| Empty/invalid PUT body → 500 | 400 validation error |
| `sendError` produced invalid JSON if the message had a quote | Proper JSON escaping |
| Malformed request line → no response | 400 Bad Request |
| No socket read timeout → idle clients could pin every worker (slowloris) | 10 s `SO_TIMEOUT` |
| No limits on header/body size | 8 KB lines, 100 headers, 1 MB body |
| Wrong method on a valid path → 404 | 405 + `Allow` header |
| `/users/{id}` could shadow `/users/me` depending on reflection order | Routes sorted most-specific first |
| Unsupported handler signature → silent 404 at request time | Fails at startup |
| `@Autowired` claimed to support methods and constructors; only fields worked | Constructor injection implemented; method target removed |
| Bean creation failures were logged and ignored → half-wired app | Fail-fast `BeanCreationException` |
| `addFilter()` before startup was silently ignored | `FilterChain` created eagerly |
| `stop()` didn't wait for in-flight requests; no shutdown hook | `awaitTermination` + JVM shutdown hook |
| Port hard-coded to 8080 | CLI arg / `-Dserver.port` / `PORT` env |
| `GetMapping`/`PostMapping` in the root package, unlike the other annotations | Moved to `annotations/` |
| Jackson 2.13.4.2 (known CVEs) | Upgraded to 2.17.2 |
| No automated tests | 48 JUnit 5 tests incl. a real-server integration test |
