package com.project.MultiThreadedWebServer.core;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Bean;
import com.project.MultiThreadedWebServer.annotations.Component;
import com.project.MultiThreadedWebServer.annotations.Configuration;
import com.project.MultiThreadedWebServer.annotations.PostConstruct;
import com.project.MultiThreadedWebServer.annotations.PreDestroy;
import com.project.MultiThreadedWebServer.annotations.Qualifier;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.annotations.Service;
import com.project.MultiThreadedWebServer.annotations.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                         APPLICATION CONTEXT                                   ║
 * ║                    (IoC Container / DI Container)                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * This is the HEART of the framework - the IoC (Inversion of Control) Container.
 * Similar to Spring's ApplicationContext.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT IS IOC (INVERSION OF CONTROL)?
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Traditional approach (without IoC):
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  public class UserController {                                              │
 * │      // YOU create the dependencies manually                               │
 * │      private UserService userService = new UserService();                  │
 * │      private EmailService emailService = new EmailService();               │
 * │                                                                             │
 * │      // Problems:                                                           │
 * │      // 1. Tight coupling - hard to test                                   │
 * │      // 2. Hard to swap implementations                                    │
 * │      // 3. Complex dependency chains are hard to manage                    │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * IoC approach (what Spring does):
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @RestController                                                            │
 * │  public class UserController {                                              │
 * │      @Autowired  // The CONTAINER injects this!                            │
 * │      private UserService userService;                                       │
 * │                                                                             │
 * │      // Benefits:                                                           │
 * │      // 1. Loose coupling - easy to test (inject mocks)                    │
 * │      // 2. Easy to swap implementations                                    │
 * │      // 3. Container manages complex dependency chains                     │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPLETE BEAN LIFECYCLE (STEP BY STEP)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * STEP 0: LOAD CONFIGURATION
 *   → application.properties (+ system properties, env vars, --args)
 *
 * STEP 1: COMPONENT SCANNING
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Scan the specified package for classes with these annotations:            │
 * │    - @Component      → General managed component                           │
 * │    - @Service        → Business logic layer                                │
 * │    - @RestController → HTTP endpoint handler                               │
 * │    - @Configuration  → Holds @Bean factory methods                         │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * STEP 2: BEAN CREATION (+ CONSTRUCTOR INJECTION + @BEAN METHODS)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  For each component class / @Bean method found:                            │
 * │    1. Pick its constructor; create its parameters first (recursively)      │
 * │    2. Create an instance using reflection: constructor.newInstance(args)   │
 * │       (or call the @Bean method: config.objectMapper())                    │
 * │    3. Store it for later retrieval                                         │
 * │    4. Detect circular constructor dependencies (A → B → A) and fail fast   │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * STEP 3: @VALUE INJECTION
 *   → Inject property values into @Value annotated fields
 *
 * STEP 4: FIELD INJECTION (@Autowired + @Qualifier)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  UserController has:                                                        │
 * │    @Autowired                                                               │
 * │    private UserService userService;  // Need to inject this!              │
 * │                                                                             │
 * │  Find the UserService bean in our container and SET the field:             │
 * │    field.set(userControllerInstance, userServiceInstance)                  │
 * │                                                                             │
 * │  @Qualifier("name") switches the lookup from by-TYPE to by-NAME.           │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * STEP 5: LIFECYCLE CALLBACKS
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @PostConstruct methods run once all dependencies are injected.            │
 * │  @PreDestroy methods run on close(), in reverse creation order.            │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * TERMINOLOGY
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * BEAN: An object managed by the container. Created once, reused everywhere.
 *       Think of it as a "singleton" managed by the framework.
 *
 * CONTAINER: This class! It "contains" and manages all the beans.
 *
 * INJECTION: The act of setting a dependency into an object.
 *            Instead of object creating its dependency, we "inject" it.
 *
 * WIRING: The process of connecting beans together via injection.
 *         "Wiring UserService into UserController"
 */
public class ApplicationContext {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationContext.class);

    /**
     * Storage for beans indexed by name.
     *
     * Example contents:
     *   "userService" → UserService@abc123
     *   "userController" → UserController@def456
     *   "objectMapper" → ObjectMapper@789abc   (from a @Bean method)
     *
     * The name is derived from the class name with first letter lowercase,
     * the @Bean method name, or set explicitly: @Component("customName").
     */
    private final Map<String, Object> beansByName = new ConcurrentHashMap<>();

    /**
     * Special list tracking only controllers (for route registration).
     * Controllers need special handling because RouteResolver needs them.
     */
    private final List<Object> controllers = new ArrayList<>();

    /**
     * Every bean in the order it was created.
     * Dependencies are always created BEFORE the beans that need them,
     * so destroying in REVERSE order shuts dependents down first.
     */
    private final List<Object> creationOrder = new ArrayList<>();

    /**
     * All classes found by component scanning: the "bean definitions".
     * A bean is only instantiated when we get to it (or when another bean needs it).
     */
    private final Set<Class<?>> componentClasses = new LinkedHashSet<>();

    /**
     * All @Bean factory methods found on @Configuration classes.
     * Also bean definitions: the method is only called when we get to it.
     */
    private final List<Method> beanMethods = new ArrayList<>();

    /**
     * Beans whose constructor (or @Bean method) is currently being resolved.
     *
     * If we're asked to create a bean that is already in this set, we've found
     * a CIRCULAR DEPENDENCY: A needs B, B needs A → neither can be built first.
     * LinkedHashSet keeps insertion order so we can print the cycle: A → B → A
     */
    private final Set<Class<?>> currentlyInCreation = new LinkedHashSet<>();

    /**
     * Properties loader — resolves values for @Value injection.
     */
    private final PropertiesLoader propertiesLoader;

    /**
     * Creates the ApplicationContext, loading application.properties from the classpath.
     *
     * @param basePackages The packages to scan (e.g., "com.project.MultiThreadedWebServer")
     * @throws BeanCreationException if any bean cannot be created (fail fast, like Spring)
     */
    public ApplicationContext(String... basePackages) {
        this(new PropertiesLoader(), basePackages);
    }

    /**
     * Creates the ApplicationContext and scans the given base packages for components.
     * This is where all the magic happens!
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * LIFECYCLE: This runs ONCE at application startup
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Timeline:
     *   T=0ms   Constructor called
     *   T=5ms   Package scanning complete (found N classes)
     *   T=10ms  Bean creation complete (created M beans, constructor injection done)
     *   T=15ms  @Value + field injection complete
     *   T=15ms  @PostConstruct methods called
     *   T=15ms  Context is READY - beans can be used!
     *
     * @param propertiesLoader Configuration used for @Value injection
     * @param basePackages     The packages to scan (e.g., "com.project.MultiThreadedWebServer")
     * @throws BeanCreationException if any bean cannot be created (fail fast, like Spring)
     */
    public ApplicationContext(PropertiesLoader propertiesLoader, String... basePackages) {
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║      Initializing IoC Container (ApplicationContext)     ║");
        logger.info("╚══════════════════════════════════════════════════════════╝");
        logger.info("Base packages: {}", String.join(", ", basePackages));

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 0: LOAD CONFIGURATION
        // Properties must be available BEFORE any bean is created
        // ═══════════════════════════════════════════════════════════════════════
        this.propertiesLoader = propertiesLoader;

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 1: COMPONENT SCANNING
        // Find all .class files in the packages and their subpackages
        // (works for both target/classes directories AND packaged JAR files)
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 1: Scanning for components...                      │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        List<Class<?>> classes = new ArrayList<>();
        for (String basePackage : basePackages) {
            classes.addAll(scanPackage(basePackage));
        }
        logger.info("Found {} classes to analyze", classes.size());
        for (Class<?> clazz : classes) {
            if (isComponent(clazz)) {
                componentClasses.add(clazz);
            }
            if (clazz.isAnnotationPresent(Configuration.class)) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Bean.class)) {
                        beanMethods.add(method);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 2: BEAN CREATION + CONSTRUCTOR INJECTION + @BEAN METHODS
        // For each component class, create an instance. If its constructor
        // needs other beans, those are created first (recursively).
        // Then call every @Bean method that hasn't been called yet.
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 2: Creating beans...                               │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        for (Class<?> clazz : componentClasses) {
            getOrCreateBean(clazz);
        }
        for (Method method : beanMethods) {
            getOrCreateFactoryBean(method);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 3: @VALUE INJECTION
        // Inject property values from application.properties
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 3: Injecting @Value properties...                  │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        for (Object bean : creationOrder) {
            injectValues(bean);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 4: FIELD INJECTION (@Autowired + @Qualifier)
        // For each bean, find @Autowired fields and inject dependencies.
        // All beans exist by now, so even field-level cycles (A ↔ B) work.
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 4: Injecting @Autowired dependencies...            │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        injectDependencies();

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 5: LIFECYCLE CALLBACKS
        // Call @PostConstruct methods - every dependency is injected by now
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 5: Calling @PostConstruct methods...               │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        invokeLifecycleMethods(creationOrder, PostConstruct.class);

        logger.info("═══════════════════════════════════════════════════════════");
        logger.info("ApplicationContext ready with {} beans", creationOrder.size());
        logger.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Checks if a class should be managed by the container.
     *
     * A class is a "component" if it has any of these annotations:
     *   - @Component      → General purpose bean
     *   - @Service        → Business logic layer
     *   - @RestController → HTTP endpoint handler
     *   - @Configuration  → Holds @Bean methods (in Spring, it's a @Component too)
     *
     * In real Spring, there are more: @Repository, @Controller, etc.
     *
     * @param clazz The class to check
     * @return true if the class should be managed as a bean
     */
    private boolean isComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class) ||
               clazz.isAnnotationPresent(Service.class) ||
               clazz.isAnnotationPresent(RestController.class) ||
               clazz.isAnnotationPresent(Configuration.class);
    }

    /**
     * Returns the bean for the requested type, creating it (and, recursively,
     * everything its constructor needs) if it doesn't exist yet.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW RECURSIVE CREATION WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     *   getOrCreateBean(UserController)
     *     └── constructor needs UserService
     *           └── getOrCreateBean(UserService)
     *                 └── constructor needs UserRepository
     *                       └── getOrCreateBean(UserRepository) → created ✓
     *                 └── UserService created ✓
     *     └── UserController created ✓
     *
     * This is a depth-first walk of the dependency graph - effectively a
     * topological sort. Spring's DefaultListableBeanFactory works the same way.
     *
     * @param requestedType A concrete component class OR an interface/superclass
     * @return the bean, or null if no component or @Bean method matches the type
     */
    private Object getOrCreateBean(Class<?> requestedType) {
        // A component class is looked up by EXACT type, so that a subclass bean
        // can never stand in for its (also annotated) parent class
        Object existing = componentClasses.contains(requestedType)
                ? findExactBean(requestedType)
                : findExistingBean(requestedType);
        if (existing != null) {
            return existing;
        }

        Object definition = resolveDefinition(requestedType);
        if (definition instanceof Method factoryMethod) {
            return getOrCreateFactoryBean(factoryMethod);
        }
        Class<?> implementation = (Class<?>) definition;
        if (implementation == null) {
            return null;
        }

        beginCreation(implementation);
        try {
            return createBean(implementation);
        } finally {
            currentlyInCreation.remove(implementation);
        }
    }

    /**
     * Marks a bean type as "in creation", failing if it already is.
     *
     * ─────────────────────────────────────────────────────────────────────────────
     * CIRCULAR DEPENDENCY DETECTION
     * If this type is already "in creation" further up the call stack,
     * following this path would recurse forever.
     * ─────────────────────────────────────────────────────────────────────────────
     */
    private void beginCreation(Class<?> type) {
        if (currentlyInCreation.contains(type)) {
            StringBuilder cycle = new StringBuilder();
            boolean inCycle = false;
            for (Class<?> c : currentlyInCreation) {
                if (c == type) inCycle = true;
                if (inCycle) cycle.append(c.getSimpleName()).append(" → ");
            }
            cycle.append(type.getSimpleName());
            throw new BeanCreationException("Circular dependency detected: " + cycle
                    + "\nBreak the cycle, or use @Autowired field injection on one side.");
        }
        currentlyInCreation.add(type);
    }

    /**
     * Creates a bean instance and registers it in the container.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW BEAN CREATION WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * 1. Pick the constructor (see selectConstructor)
     * 2. Resolve each constructor parameter to a bean (CONSTRUCTOR INJECTION)
     * 3. Call constructor.newInstance(args...) to create the object
     * 4. Store it under its name: "className" → Instance
     * 5. If it's a controller, also add to controllers list
     *
     * Example:
     *   Input: UserService.class
     *   Output:
     *     beansByName.put("userService", new UserService(userRepository))
     *
     * @param clazz The class to instantiate
     */
    private Object createBean(Class<?> clazz) {
        Constructor<?> constructor = selectConstructor(clazz);
        Object[] args = resolveArguments(constructor, clazz.getSimpleName());

        Object instance;
        try {
            constructor.setAccessible(true);
            // Use reflection to create the instance.
            // This is equivalent to: Object instance = new UserController(userService);
            instance = constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("Constructor of " + clazz.getSimpleName() + " threw an exception",
                    e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException("Failed to instantiate " + clazz.getName(), e);
        }

        // Generate bean name (class name with lowercase first letter)
        String beanName = getBeanName(clazz);
        register(beanName, instance);

        // Track controllers separately - RouteResolver needs them
        String injected = args.length == 0 ? "" : " (constructor-injected " + args.length + " dependencies)";
        if (clazz.isAnnotationPresent(RestController.class)) {
            controllers.add(instance);
            logger.info("  ✓ Registered @RestController: {}{}", clazz.getSimpleName(), injected);
        } else if (clazz.isAnnotationPresent(Service.class)) {
            logger.info("  ✓ Registered @Service: {} as '{}'{}", clazz.getSimpleName(), beanName, injected);
        } else if (clazz.isAnnotationPresent(Configuration.class)) {
            logger.info("  ⚙ Registered @Configuration: {}{}", clazz.getSimpleName(), injected);
        } else {
            logger.info("  ✓ Registered @Component: {} as '{}'{}", clazz.getSimpleName(), beanName, injected);
        }
        return instance;
    }

    /**
     * Returns the bean produced by a @Bean method, calling the method if needed.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW @CONFIGURATION + @BEAN PROCESSING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Given:
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │  @Configuration                                                         │
     * │  public class AppConfig {                                               │
     * │      @Bean                                                              │
     * │      public ObjectMapper objectMapper() {                               │
     * │          return new ObjectMapper();                                      │
     * │      }                                                                  │
     * │  }                                                                      │
     * └─────────────────────────────────────────────────────────────────────────┘
     *
     * Steps:
     * 1. Get (or create) the AppConfig bean
     * 2. Resolve the method's parameters from the container (like a constructor)
     * 3. Call objectMapper() → gets an ObjectMapper instance
     * 4. Register that instance under the name "objectMapper"
     *
     * Now anyone can @Autowired ObjectMapper - or take it as a constructor parameter!
     */
    private Object getOrCreateFactoryBean(Method method) {
        String beanName = getBeanName(method);
        Object existing = beansByName.get(beanName);
        if (existing != null) {
            return existing;
        }

        Class<?> beanType = method.getReturnType();
        beginCreation(beanType);
        try {
            Object config = getOrCreateBean(method.getDeclaringClass());
            Object[] args = resolveArguments(method, method.getName() + "()");
            Object instance;
            try {
                method.setAccessible(true);
                instance = method.invoke(config, args);
            } catch (InvocationTargetException e) {
                throw new BeanCreationException("@Bean method " + method.getName() + "() threw an exception",
                        e.getCause());
            } catch (IllegalAccessException e) {
                throw new BeanCreationException("Cannot call @Bean method " + method, e);
            }
            if (instance == null) {
                throw new BeanCreationException("@Bean method " + method + " returned null");
            }

            register(beanName, instance);
            logger.info("  ✓ Registered @Bean: {} → {}()", beanType.getSimpleName(), method.getName());
            return instance;
        } finally {
            currentlyInCreation.remove(beanType);
        }
    }

    /**
     * Resolves every parameter of a constructor or @Bean method from the container.
     * A parameter annotated with @Qualifier("name") is looked up by NAME.
     */
    private Object[] resolveArguments(Executable executable, String owner) {
        Parameter[] parameters = executable.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Qualifier qualifier = parameters[i].getAnnotation(Qualifier.class);
            Class<?> type = parameters[i].getType();
            args[i] = qualifier != null
                    ? getQualifiedBean(qualifier.value(), type)
                    : getOrCreateBean(type);
            if (args[i] == null) {
                throw new BeanCreationException("Cannot create " + owner
                        + ": no bean of type " + type.getName()
                        + " for parameter " + i
                        + "\nMake sure the class has @Component, @Service, or @RestController annotation.");
            }
        }
        return args;
    }

    /** Adds a fully constructed bean to the container. */
    private void register(String beanName, Object instance) {
        if (beansByName.containsKey(beanName)) {
            throw new BeanCreationException("Duplicate bean name '" + beanName + "' for "
                    + instance.getClass().getName());
        }
        beansByName.put(beanName, instance);
        creationOrder.add(instance);
    }

    /**
     * Chooses which constructor the container calls. Same rules as Spring:
     *
     *   1. A constructor annotated with @Autowired      → use it
     *   2. Exactly ONE constructor (any parameters)      → use it (no annotation needed!)
     *   3. Otherwise                                     → the no-arg constructor
     *
     * Rule 2 is why modern Spring code rarely writes @Autowired at all:
     *
     *   @Service
     *   public class OrderService {
     *       private final PaymentService paymentService;   // final = immutable
     *       public OrderService(PaymentService paymentService) { ... }
     *   }
     */
    private Constructor<?> selectConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        List<Constructor<?>> autowired = new ArrayList<>();
        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(Autowired.class)) {
                autowired.add(constructor);
            }
        }
        if (autowired.size() > 1) {
            throw new BeanCreationException(clazz.getSimpleName() + " has more than one @Autowired constructor");
        }
        if (autowired.size() == 1) {
            return autowired.get(0);
        }
        if (constructors.length == 1) {
            return constructors[0];
        }
        try {
            return clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new BeanCreationException(clazz.getSimpleName()
                    + " has several constructors - mark one with @Autowired or add a no-arg constructor");
        }
    }

    /**
     * Finds which bean definition should satisfy a requested type.
     *
     *   - Requested type IS a component                       → itself
     *   - Exactly one component / @Bean method matches it     → that one
     *   - None                                                → null
     *   - More than one                                       → error (ambiguous!)
     *
     * The last case is Spring's NoUniqueBeanDefinitionException, which Spring
     * lets you resolve with @Primary or @Qualifier.
     *
     * @return a component Class, a @Bean Method, or null
     */
    private Object resolveDefinition(Class<?> requestedType) {
        if (componentClasses.contains(requestedType)) {
            return requestedType;
        }
        List<Object> candidates = new ArrayList<>();
        for (Class<?> candidate : componentClasses) {
            if (requestedType.isAssignableFrom(candidate)) {
                candidates.add(candidate);
            }
        }
        for (Method method : beanMethods) {
            if (requestedType.isAssignableFrom(method.getReturnType())) {
                candidates.add(method);
            }
        }
        if (candidates.size() > 1) {
            throw new BeanCreationException("Expected a single bean of type " + requestedType.getName()
                    + " but found " + candidates.size() + ": " + candidates
                    + "\nUse @Qualifier(\"beanName\") to pick one.");
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Generates a bean name from the class.
     *
     * Rules:
     * 1. Check if annotation has explicit name: @Service("myService")
     * 2. If not, use class name with lowercase first letter
     *
     * Examples:
     *   UserService.class → "userService"
     *   HTTPClient.class → "hTTPClient" (first letter lowercased)
     *   @Service("customName") → "customName"
     */
    private String getBeanName(Class<?> clazz) {
        // Check for explicit name in annotations
        if (clazz.isAnnotationPresent(Component.class)) {
            String name = clazz.getAnnotation(Component.class).value();
            if (!name.isEmpty()) return name;
        }
        if (clazz.isAnnotationPresent(Service.class)) {
            String name = clazz.getAnnotation(Service.class).value();
            if (!name.isEmpty()) return name;
        }

        // Default: class name with lowercase first letter
        return defaultBeanName(clazz);
    }

    /** A @Bean's name is @Bean("name") if given, otherwise the method name. */
    private static String getBeanName(Method method) {
        String name = method.getAnnotation(Bean.class).value();
        return name.isEmpty() ? method.getName() : name;
    }

    private static String defaultBeanName(Class<?> clazz) {
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * Injects @Value properties from application.properties into a bean's fields.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW @VALUE INJECTION WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Given:
     *   application.properties: user.default-role=USER
     *
     *   @Service
     *   public class UserService {
     *       @Value("user.default-role")
     *       private String defaultRole;    // ← Will be set to "USER"
     *   }
     *
     * Steps:
     * 1. Check every field (including inherited ones) for @Value
     * 2. Read the property key from the annotation
     * 3. Look up the value in PropertiesLoader
     * 4. Convert to the correct type (String, int, boolean, etc.)
     * 5. Set the field value via reflection
     *
     * A missing property fails startup, like Spring's
     * "Could not resolve placeholder" error.
     */
    private void injectValues(Object bean) {
        for (Class<?> clazz = bean.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Value.class)) {
                    continue;
                }
                String propertyKey = field.getAnnotation(Value.class).value();
                String propertyValue = propertiesLoader.getProperty(propertyKey);
                if (propertyValue == null) {
                    throw new BeanCreationException("Property '" + propertyKey + "' not found for @Value on "
                            + clazz.getSimpleName() + "." + field.getName()
                            + "\nAdd it to application.properties.");
                }
                try {
                    field.setAccessible(true);
                    // Convert String to the field's actual type
                    field.set(bean, convertValue(propertyValue, field.getType()));
                    logger.info("  → @Value(\"{}\") = \"{}\" → {}.{}",
                            propertyKey, propertyValue, clazz.getSimpleName(), field.getName());
                } catch (IllegalArgumentException e) {
                    throw new BeanCreationException("Cannot convert property '" + propertyKey + "' value \""
                            + propertyValue + "\" to " + field.getType().getSimpleName(), e);
                } catch (IllegalAccessException e) {
                    throw new BeanCreationException("Failed to inject @Value field " + field, e);
                }
            }
        }
    }

    /**
     * Converts a String property value to the target field type.
     *
     * Supports: String, int, long, boolean, double
     *
     * @throws IllegalArgumentException if the value can't be converted
     *         (NumberFormatException is a subclass of it)
     */
    private Object convertValue(String value, Class<?> targetType) {
        String trimmed = value.trim();
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(trimmed);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(trimmed);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(trimmed);
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (!trimmed.equalsIgnoreCase("true") && !trimmed.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("not a boolean: " + value);
            }
            return Boolean.parseBoolean(trimmed);
        }
        throw new IllegalArgumentException("unsupported @Value type " + targetType.getName());
    }

    /**
     * Injects @Autowired dependencies into all beans.
     *
     * This method iterates through ALL beans and calls injectFields for each.
     */
    private void injectDependencies() {
        for (Object bean : creationOrder) {
            injectFields(bean);
        }
    }

    /**
     * Injects @Autowired fields for a single bean.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW FIELD INJECTION WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Given a bean like:
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │  @RestController                                                        │
     * │  public class UserController {                                          │
     * │      @Autowired                                                         │
     * │      private UserService userService;  // We need to inject this!      │
     * │  }                                                                       │
     * └─────────────────────────────────────────────────────────────────────────┘
     *
     * Steps:
     * 1. Get all declared fields of the class (and its superclasses)
     * 2. For each field with @Autowired annotation:
     *    a. Get the field's type (UserService.class)
     *    b. Find a bean of that type in our container
     *    c. Use reflection to set the field value
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW @QUALIFIER RESOLUTION WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * WITHOUT @Qualifier (default):
     *   @Autowired
     *   private UserService userService;
     *   → Looks up bean by TYPE (UserService.class)
     *
     * WITH @Qualifier:
     *   @Autowired
     *   @Qualifier("emailNotifier")
     *   private Notifier notifier;
     *   → Looks up bean by NAME ("emailNotifier")
     *   → Solves the "multiple beans of same type" problem
     *
     * @param bean The bean to inject dependencies into
     */
    private void injectFields(Object bean) {
        // Walk up the class hierarchy so inherited @Autowired fields are injected too
        for (Class<?> clazz = bean.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            // Iterate through ALL fields (including private ones)
            for (Field field : clazz.getDeclaredFields()) {

                // Check if field has @Autowired annotation
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                Autowired autowired = field.getAnnotation(Autowired.class);
                Class<?> fieldType = field.getType();

                // Find the bean: by NAME with @Qualifier, otherwise by TYPE
                Qualifier qualifier = field.getAnnotation(Qualifier.class);
                Object dependency = qualifier != null
                        ? getQualifiedBean(qualifier.value(), fieldType)
                        : getOrCreateBean(fieldType);

                if (dependency != null) {
                    try {
                        // Make private field accessible (bypass access modifiers)
                        field.setAccessible(true);

                        // SET THE FIELD VALUE - This is the actual injection!
                        field.set(bean, dependency);

                        logger.info("  → Injected {}{} into {}.{}",
                                fieldType.getSimpleName(),
                                qualifier != null ? " @Qualifier(\"" + qualifier.value() + "\")" : "",
                                clazz.getSimpleName(),
                                field.getName());

                    } catch (IllegalAccessException e) {
                        throw new BeanCreationException("Failed to inject field " + field, e);
                    }
                } else if (autowired.required()) {
                    // The dependency was marked as required, but we can't find it!
                    throw new BeanCreationException(
                        "No bean found for required @Autowired dependency: " + fieldType.getName() +
                        (qualifier != null ? " named '" + qualifier.value() + "'" : "") +
                        " in " + clazz.getSimpleName() + "." + field.getName() +
                        "\nMake sure the class has @Component, @Service, or @RestController annotation."
                    );
                }
            }
        }
    }

    /**
     * Looks up a bean by NAME for @Qualifier, creating it first if it is defined
     * but not built yet.
     *
     * @return the bean, or null if no bean has this name
     * @throws BeanCreationException if the named bean is not of the required type
     */
    private Object getQualifiedBean(String name, Class<?> requiredType) {
        Object bean = beansByName.get(name);
        if (bean == null) {
            for (Class<?> clazz : componentClasses) {
                if (getBeanName(clazz).equals(name)) {
                    bean = getOrCreateBean(clazz);
                }
            }
            for (Method method : beanMethods) {
                if (getBeanName(method).equals(name)) {
                    bean = getOrCreateFactoryBean(method);
                }
            }
        }
        if (bean != null && !requiredType.isInstance(bean)) {
            throw new BeanCreationException("Bean '" + name + "' is a " + bean.getClass().getName()
                    + ", not a " + requiredType.getName());
        }
        return bean;
    }

    /**
     * Calls every no-arg method annotated with the given lifecycle annotation.
     *
     * @PostConstruct → after all dependencies are injected (init caches, validate config)
     * @PreDestroy    → on shutdown (close connections, flush buffers)
     */
    private void invokeLifecycleMethods(List<Object> beans, Class<? extends Annotation> annotation) {
        for (Object bean : beans) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(annotation)) {
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    throw new BeanCreationException("@" + annotation.getSimpleName()
                            + " method must have no parameters: " + method);
                }
                try {
                    method.setAccessible(true);
                    method.invoke(bean);
                    logger.info("  ⚙ Called @{} {}.{}()", annotation.getSimpleName(),
                            bean.getClass().getSimpleName(), method.getName());
                } catch (InvocationTargetException e) {
                    throw new BeanCreationException("@" + annotation.getSimpleName() + " method failed: "
                            + method, e.getCause());
                } catch (IllegalAccessException e) {
                    throw new BeanCreationException("Cannot call " + method, e);
                }
            }
        }
    }

    /** Looks up an already-created bean whose class is exactly this type. */
    private Object findExactBean(Class<?> type) {
        for (Object bean : creationOrder) {
            if (bean.getClass() == type) {
                return bean;
            }
        }
        return null;
    }

    /**
     * Looks up an already-created bean assignable to this type.
     * If several match, a single bean of exactly this class wins
     * (a parent-class bean beats its subclass beans); otherwise it's ambiguous.
     */
    private Object findExistingBean(Class<?> type) {
        List<Object> matches = new ArrayList<>();
        List<Object> exactMatches = new ArrayList<>();
        for (Object bean : creationOrder) {
            if (type.isInstance(bean)) {
                matches.add(bean);
                if (bean.getClass() == type) {
                    exactMatches.add(bean);
                }
            }
        }
        if (matches.size() <= 1) {
            return matches.isEmpty() ? null : matches.get(0);
        }
        if (exactMatches.size() == 1) {
            return exactMatches.get(0);
        }
        throw new BeanCreationException("Expected a single bean of type " + type.getName()
                + " but found " + matches.size() + "\nUse @Qualifier(\"beanName\") to pick one.");
    }

    /**
     * Gets a bean by its type.
     *
     * This is how you'd retrieve a bean programmatically:
     *   UserService service = context.getBean(UserService.class);
     *
     * The method also handles interfaces and superclasses:
     * If you request NotificationService.class and there's an EmailService
     * that implements NotificationService, it will return the EmailService.
     *
     * @param type The class type of the bean
     * @return The bean instance, or null if not found
     * @throws BeanCreationException if several beans match the type
     */
    public <T> T getBean(Class<T> type) {
        return type.cast(findExistingBean(type));
    }

    /**
     * Gets a bean by its name.
     *
     * Example: context.getBean("userService")
     *
     * @param name The bean name
     * @return The bean instance, or null if not found
     */
    public Object getBean(String name) {
        return beansByName.get(name);
    }

    /** Number of beans in the container. */
    public int getBeanCount() {
        return creationOrder.size();
    }

    /**
     * Gets all registered controllers.
     *
     * Used by RouteResolver to scan for @GetMapping, @PostMapping, etc.
     *
     * @return List of controller instances
     */
    public List<Object> getControllers() {
        return new ArrayList<>(controllers);
    }

    /** Gets the properties loader (for programmatic access, e.g. server.port). */
    public PropertiesLoader getPropertiesLoader() {
        return propertiesLoader;
    }

    /**
     * Registers a bean manually (useful for programmatic configuration).
     *
     * Sometimes you want to add a bean without using annotations:
     *   context.registerBean(DataSource.class, myDataSource);
     */
    public <T> void registerBean(Class<T> type, T instance) {
        register(defaultBeanName(type), instance);

        // Track controllers for RouteResolver
        if (type.isAnnotationPresent(RestController.class)) {
            controllers.add(instance);
        }
    }

    /**
     * Scans a package and returns all classes found.
     *
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW PACKAGE SCANNING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * 1. Convert package name to path: "com.example" → "com/example"
     * 2. Ask the ClassLoader for EVERY classpath location containing that path
     *    (there can be several, e.g. target/classes AND target/test-classes)
     * 3. Each location is either:
     *      file:/.../target/classes/com/example      → walk the directory
     *      jar:file:/.../app.jar!/com/example         → walk the JAR's entries
     * 4. Load each .class file using Class.forName()
     *
     * The JAR case matters: when you run "java -jar app.jar", classes live
     * INSIDE a zip file, and java.io.File cannot see them. Spring handles this
     * with PathMatchingResourcePatternResolver; we read the JarFile directly.
     *
     * This is a simplified implementation. Real Spring reads class metadata
     * with ASM bytecode parsing, so it doesn't even have to load the classes.
     *
     * @param basePackage The package to scan
     * @return List of all classes found
     */
    private List<Class<?>> scanPackage(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        String path = basePackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                switch (resource.getProtocol()) {
                    case "file" -> scanDirectory(new File(resource.toURI()), basePackage, classes, classLoader);
                    case "jar" -> scanJar(resource, path, classes, classLoader);
                    default -> logger.warn("Unsupported classpath location: {}", resource);
                }
            }
        } catch (Exception e) {
            throw new BeanCreationException("Error scanning package: " + basePackage, e);
        }

        return classes;
    }

    /**
     * Recursively scans a directory for .class files.
     *
     * @param directory   The directory to scan
     * @param packageName The Java package name corresponding to this directory
     * @param classes     List to add found classes to
     */
    private void scanDirectory(File directory, String packageName, List<Class<?>> classes, ClassLoader classLoader) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse into subdirectory
                // "com.project" + "." + "controller" = "com.project.controller"
                scanDirectory(file, packageName + "." + file.getName(), classes, classLoader);
            } else if (file.getName().endsWith(".class")) {
                // Found a .class file - load it!
                // "UserController.class" → "com.project.controller.UserController"
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                addClass(className, classes, classLoader);
            }
        }
    }

    /**
     * Scans the entries of a JAR file for .class files under the package path.
     *
     * A JAR is a zip; its entries have names like:
     *   com/project/MultiThreadedWebServer/controller/UserController.class
     */
    private void scanJar(URL resource, String packagePath, List<Class<?>> classes, ClassLoader classLoader)
            throws IOException {
        JarURLConnection connection = (JarURLConnection) resource.openConnection();
        connection.setUseCaches(false);
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(packagePath + "/") && name.endsWith(".class")) {
                    // "com/project/Foo.class" → "com.project.Foo"
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    addClass(className, classes, classLoader);
                }
            }
        }
    }

    /** Loads a class (without running its static initializers) and keeps it if instantiable. */
    private void addClass(String className, List<Class<?>> classes, ClassLoader classLoader) {
        try {
            // initialize=false: just read the class, don't run static blocks yet
            Class<?> clazz = Class.forName(className, false, classLoader);

            // Skip interfaces and abstract classes (can't instantiate them)
            if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                classes.add(clazz);
            }
        } catch (ClassNotFoundException | LinkageError e) {
            logger.warn("Could not load class: {}", className);
        }
    }

    /**
     * Shuts down the application context and releases resources.
     *
     * Calls @PreDestroy methods in REVERSE creation order, so a bean is
     * destroyed before the beans it depends on. In a real application this
     * is where you would close database connections, stop scheduled tasks
     * and release external resources.
     */
    public void close() {
        logger.info("Shutting down ApplicationContext...");
        List<Object> reversed = new ArrayList<>(creationOrder);
        Collections.reverse(reversed);
        for (Object bean : reversed) {
            try {
                invokeLifecycleMethods(List.of(bean), PreDestroy.class);
            } catch (BeanCreationException e) {
                // One failing @PreDestroy must not stop the others
                logger.error("Error during shutdown of {}", bean.getClass().getSimpleName(), e);
            }
        }
        beansByName.clear();
        controllers.clear();
        creationOrder.clear();
    }
}
