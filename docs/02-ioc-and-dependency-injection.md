# 2. IoC Container & Dependency Injection

Source: [`core/ApplicationContext.java`](../src/main/java/com/project/MultiThreadedWebServer/core/ApplicationContext.java)

## The idea in one paragraph

**Inversion of Control** means your classes stop creating their own collaborators (`new UserService()`)
and instead *declare* what they need. A **container** creates every object (a *bean*), works out the order,
and hands each bean its dependencies. That hand-over is **Dependency Injection**.
The payoff: loose coupling, easy testing (pass a fake into the constructor), and one place that controls
object lifecycles.

```java
// Without IoC: tight coupling, hard to test
public class UserController {
    private final UserService userService = new UserService(new UserRepository());
}

// With IoC: the container supplies the dependency
@RestController
public class UserController {
    @Autowired
    private UserService userService;
}
```

## The phases

```
new ApplicationContext(properties, "com.project.MultiThreadedWebServer")
 │
 ├─ 0. CONFIG      properties are loaded before any bean exists (PropertiesLoader)
 │
 ├─ 1. SCAN        find every .class under the package (folders AND jar files)
 │                 keep the ones annotated @Component / @Service / @RestController / @Configuration,
 │                 and remember every @Bean method on the @Configuration classes
 │
 ├─ 2. CREATE      for each component: pick a constructor, create its arguments first
 │                 (recursively), then instantiate. Call each @Bean method the same way.
 │                 Detect constructor cycles.
 │
 ├─ 3. VALUES      set every @Value field from the properties
 │
 ├─ 4. INJECT      set every @Autowired field (including inherited fields)
 │
 └─ 5. INITIALIZE  call @PostConstruct methods
                   ... later, close() calls @PreDestroy in reverse creation order
```

### Phase 1: component scanning

```java
Enumeration<URL> resources = classLoader.getResources("com/project/MultiThreadedWebServer");
```

`getResources` (plural) returns **every** classpath location that contains the package.
During `mvn test` that's both `target/classes` and `target/test-classes`. Each URL is one of two kinds:

| URL protocol | Example | How it's scanned |
|---|---|---|
| `file:` | `file:/…/target/classes/com/project/…` | Walk the directory tree with `java.io.File` |
| `jar:` | `jar:file:/…/app.jar!/com/project/…` | Open the `JarFile` and iterate its entries |

Each `.class` path becomes a class name (`com/project/Foo.class` → `com.project.Foo`) and is loaded with
`Class.forName(name, false, classLoader)`. The `false` means *don't run static initializers yet*;
we only want to read annotations. Interfaces and abstract classes are skipped because they can't be instantiated.

> **Interview point:** the original version only handled `file:` URLs, so running the packaged JAR found
> **zero** classes and the code fell back to hard-coded manual registration. Supporting `jar:` URLs fixed it.
> Real Spring goes further: it reads class files with ASM bytecode parsing so it doesn't load classes at all.

### Phase 2: bean creation and constructor injection

`getOrCreateBean(type)` is a depth-first walk of the dependency graph:

```
getOrCreateBean(UserService)
  └─ constructor UserService(UserRepository)
       └─ getOrCreateBean(UserRepository) → no deps → created ✓
  └─ new UserService(userRepository) ✓
```

Because dependencies are always created before the beans that need them, the creation order is a
**topological sort** of the graph. `creationOrder` records it so `close()` can destroy beans in reverse.

**Which constructor?** The same rules as Spring:

1. The constructor annotated `@Autowired`, if there is one (more than one is an error).
2. Otherwise, if the class has **exactly one** constructor, use it. No annotation needed; this is why modern
   Spring code rarely writes `@Autowired` on constructors.
3. Otherwise the no-arg constructor (or fail with a clear message).

**Which bean satisfies a type?** (`resolveImplementation`)

| Situation | Result |
|---|---|
| The requested type *is* a component | that class |
| Exactly one component implements/extends it (e.g. an interface) | that implementation |
| None | `null` → error if required |
| More than one | `BeanCreationException` (Spring: `NoUniqueBeanDefinitionException`, resolved with `@Primary`/`@Qualifier`) |

### Circular dependency detection

```java
@Service class ChickenService { ChickenService(EggService e) {} }
@Service class EggService     { EggService(ChickenService c) {} }
```

Neither can be built first. The container keeps a `currentlyInCreation` set (a `LinkedHashSet`, so it remembers
order). If it's asked to create a class that's already in that set, it has found a cycle and reports the path:

```
BeanCreationException: Circular dependency detected: ChickenService → EggService → ChickenService
Break the cycle, or use @Autowired field injection on one side.
```

Without the check, the recursion would end in a `StackOverflowError`.

**Why do field cycles work?** Fields are injected in phase 3, after *all* beans already exist,
so `PingService.pong` and `PongService.ping` can point at each other. Spring behaves the same way:
it resolves setter/field cycles through "early singleton references". Spring Boot 2.6+ still **forbids** them
by default (`spring.main.allow-circular-references=false`), because a cycle usually signals a design problem.

### @Configuration and @Bean

You can't put `@Component` on a class you don't own, such as Jackson's `ObjectMapper`.
A `@Bean` method is a factory the container calls instead:

```java
@Configuration
public class AppConfig {
    @Bean
    public ObjectMapper objectMapper() { ... }   // bean name = method name: "objectMapper"
}
```

`@Bean` methods are bean definitions just like component classes: the container calls one when it gets to it
**or when another bean needs its type**, so `UserController` can take the `ObjectMapper` as a field or a
constructor parameter. Parameters of a `@Bean` method are resolved from the container, like constructor
parameters. (Real Spring also proxies `@Configuration` classes so that calling one `@Bean` method from another
returns the singleton; HSpring doesn't, so take dependencies as parameters instead.)

### Phase 3: @Value injection

```java
@Value("user.default-role")
private String defaultRole;      // ← "USER" from application.properties
```

The key is looked up through `PropertiesLoader` (`--key=value` argument → `-D` system property →
environment variable → `application.properties`) and converted to the field type
(`String`, `int`, `long`, `double`, `boolean`). A missing key fails startup, like Spring's
"Could not resolve placeholder". `@Value` fields are set **after** the constructor runs, so use them in
`@PostConstruct`, not in the constructor (`UserService` shows this).

### Phase 4: field injection

For each bean, walk the class **and its superclasses** (so an `@Autowired` field declared in an abstract base
controller is injected too). For each `@Autowired` field:

```java
field.setAccessible(true);      // bypass "private"
field.set(bean, dependency);    // ← this line *is* dependency injection
```

`@Autowired(required = false)` leaves the field `null` when no bean matches; otherwise it's a startup error.

**`@Qualifier("name")`** switches the lookup from by-type to by-name. It solves the "two beans of the same type"
problem, on fields and on constructor or `@Bean` method parameters:

```java
public AlertService(@Qualifier("smsNotifier") Notifier notifier) { ... }
```

### Phase 5: lifecycle callbacks

- `@PostConstruct`: runs once, after injection. `UserService.loadSampleData()` seeds three users here.
  Why not in the constructor? With field injection, fields are still `null` while the constructor runs.
- `@PreDestroy`: runs on `close()` in **reverse** creation order, so a bean shuts down before the beans it depends on.

Spring's full lifecycle has more hooks (`BeanPostProcessor`, `InitializingBean`, `Aware` interfaces),
but these two are the ones you use day to day.

## Constructor vs. field injection

The demo deliberately uses both:

| | Constructor injection (`UserService`) | Field injection (`UserController`) |
|---|---|---|
| Fields can be `final` | ✅ immutable, safely published to other threads | ❌ |
| Object can't exist half-built | ✅ | ❌ fields are `null` until phase 4 |
| Unit test without the container | ✅ `new UserService(fakeRepo)` | ❌ needs reflection |
| Hides "too many dependencies" smell | ❌ long constructor makes it obvious | ✅ (bad) |
| Circular dependency | fails fast | silently allowed |

**Recommendation (and Spring's):** use constructor injection by default.

## Bean names and lookup

- Default name: simple class name with a lower-case first letter (`UserService` → `userService`).
- Override: `@Service("greeter")` / `@Component("name")`. Duplicate names are a startup error.
- `@Bean` methods: the method name, or `@Bean("name")`.
- `getBean(Class)`: any bean assignable to the type; if several match, a single bean of exactly that class
  wins, otherwise it's ambiguous → error (use `@Qualifier` at injection points).
- `getBean(String)`: by name.

All beans are **singletons**: one instance shared by every request thread. That's why beans must be
stateless or thread-safe (see `UserRepository`, which uses `ConcurrentHashMap` and `AtomicLong`).

## Error messages you might see

| Message | Cause |
|---|---|
| `Circular dependency detected: A → B → A` | Constructor cycle |
| `No bean found for required @Autowired dependency: X` | `X` isn't annotated or isn't under the scanned package |
| `Expected a single bean of type X but found 2` | Two implementations of an interface |
| `X has several constructors - mark one with @Autowired…` | Constructor choice is ambiguous |
| `Duplicate bean name 'x'` | Two components with the same name |
| `Property 'x' not found for @Value on …` | Key missing from `application.properties` (and not set any other way) |

## Try it

The tests in [`ApplicationContextTest`](../src/test/java/com/hspring/container/ApplicationContextTest.java)
cover each scenario with tiny fixture packages under `src/test/java/com/hspring/testbeans/`
(`ok`, `cycle`, `fieldcycle`, `missing`, `ambiguous`). Set a breakpoint in `getOrCreateBean` and debug
`circularConstructorDependencyFailsFastWithClearMessage` to watch the cycle being detected.
