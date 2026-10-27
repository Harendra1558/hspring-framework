package com.project.MultiThreadedWebServer.core;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Component;
import com.project.MultiThreadedWebServer.annotations.PostConstruct;
import com.project.MultiThreadedWebServer.annotations.PreDestroy;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * WHAT THIS CLASS DOES (STEP BY STEP)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * STEP 1: COMPONENT SCANNING
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Scan the specified package for classes with these annotations:            │
 * │    - @Component  → General managed component                               │
 * │    - @Service    → Business logic layer                                    │
 * │    - @RestController → HTTP endpoint handler                               │
 * │                                                                             │
 * │  Example: Scanning "com.project.MyApp" might find:                         │
 * │    - UserController.class (has @RestController)                            │
 * │    - UserService.class (has @Service)                                      │
 * │    - EmailService.class (has @Component)                                   │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * STEP 2: BEAN CREATION (+ CONSTRUCTOR INJECTION)
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  For each annotated class found:                                           │
 * │    1. Pick its constructor; create its parameters first (recursively)      │
 * │    2. Create an instance using reflection: constructor.newInstance(args)   │
 * │    3. Store in a Map for later retrieval                                   │
 * │    4. Detect circular constructor dependencies (A → B → A) and fail fast   │
 * │                                                                             │
 * │  After this step, we have:                                                 │
 * │    beans = {                                                                │
 * │      UserController.class → UserController instance                        │
 * │      UserService.class    → UserService instance                           │
 * │      EmailService.class   → EmailService instance                          │
 * │    }                                                                        │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * STEP 3: DEPENDENCY INJECTION
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  For each bean, look for fields annotated with @Autowired:                 │
 * │                                                                             │
 * │  UserController has:                                                        │
 * │    @Autowired                                                               │
 * │    private UserService userService;  // Need to inject this!              │
 * │                                                                             │
 * │  Find the UserService bean in our map and SET the field:                   │
 * │    field.set(userControllerInstance, userServiceInstance)                  │
 * │                                                                             │
 * │  Now UserController can use userService without creating it!               │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * STEP 4: LIFECYCLE CALLBACKS
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
     * Storage for all managed beans, indexed by their class type.
     *
     * Example contents:
     *   UserService.class → UserService@abc123
     *   UserController.class → UserController@def456
     *
     * Using ConcurrentHashMap for thread-safety (multiple threads may access).
     */
    private final Map<Class<?>, Object> beansByType = new ConcurrentHashMap<>();

    /**
     * Storage for beans indexed by name.
     *
     * Example contents:
     *   "userService" → UserService@abc123
     *   "userController" → UserController@def456
     *
     * The name is derived from the class name with first letter lowercase.
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
     * Beans whose constructor is currently being resolved.
     *
     * If we're asked to create a bean that is already in this set, we've found
     * a CIRCULAR DEPENDENCY: A needs B, B needs A → neither can be built first.
     * LinkedHashSet keeps insertion order so we can print the cycle: A → B → A
     */
    private final Set<Class<?>> currentlyInCreation = new LinkedHashSet<>();

    /**
     * Creates the ApplicationContext and scans the given base package for components.
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
     *   T=15ms  Field injection complete
     *   T=15ms  @PostConstruct methods called
     *   T=15ms  Context is READY - beans can be used!
     *
     * @param basePackage The package to scan (e.g., "com.project.MultiThreadedWebServer")
     * @throws BeanCreationException if any bean cannot be created (fail fast, like Spring)
     */
    public ApplicationContext(String basePackage) {
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║      Initializing IoC Container (ApplicationContext)     ║");
        logger.info("╚══════════════════════════════════════════════════════════╝");
        logger.info("Base package: {}", basePackage);

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 1: COMPONENT SCANNING
        // Find all .class files in the package and its subpackages
        // (works for both target/classes directories AND packaged JAR files)
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 1: Scanning for components...                      │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        List<Class<?>> classes = scanPackage(basePackage);
        logger.info("Found {} classes to analyze", classes.size());
        for (Class<?> clazz : classes) {
            if (isComponent(clazz)) {
                componentClasses.add(clazz);
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 2: BEAN CREATION + CONSTRUCTOR INJECTION
        // For each component class, create an instance. If its constructor
        // needs other beans, those are created first (recursively).
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 2: Creating beans...                               │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        for (Class<?> clazz : componentClasses) {
            getOrCreateBean(clazz);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 3: FIELD INJECTION
        // For each bean, find @Autowired fields and inject dependencies.
        // All beans exist by now, so even field-level cycles (A ↔ B) work.
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 3: Injecting dependencies...                       │");
        logger.info("└─────────────────────────────────────────────────────────┘");

        injectDependencies();

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 4: LIFECYCLE CALLBACKS
        // Call @PostConstruct methods - every dependency is injected by now
        // ═══════════════════════════════════════════════════════════════════════
        invokeLifecycleMethods(creationOrder, PostConstruct.class);

        logger.info("═══════════════════════════════════════════════════════════");
        logger.info("ApplicationContext ready with {} beans", beansByType.size());
        logger.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Checks if a class should be managed by the container.
     *
     * A class is a "component" if it has any of these annotations:
     *   - @Component  → General purpose bean
     *   - @Service    → Business logic layer
     *   - @RestController → HTTP endpoint handler
     *
     * In real Spring, there are more: @Repository, @Configuration, etc.
     *
     * @param clazz The class to check
     * @return true if the class should be managed as a bean
     */
    private boolean isComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class) ||
               clazz.isAnnotationPresent(Service.class) ||
               clazz.isAnnotationPresent(RestController.class);
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
     * @return the bean, or null if no component matches the type
     */
    private Object getOrCreateBean(Class<?> requestedType) {
        // A component class is looked up by EXACT type, so that a subclass bean
        // can never stand in for its (also annotated) parent class
        Object existing = componentClasses.contains(requestedType)
                ? beansByType.get(requestedType)
                : findExistingBean(requestedType);
        if (existing != null) {
            return existing;
        }

        Class<?> implementation = resolveImplementation(requestedType);
        if (implementation == null) {
            return null;
        }

        // ─────────────────────────────────────────────────────────────────────────
        // CIRCULAR DEPENDENCY DETECTION
        // If this class is already "in creation" further up the call stack,
        // following this path would recurse forever.
        // ─────────────────────────────────────────────────────────────────────────
        if (currentlyInCreation.contains(implementation)) {
            StringBuilder cycle = new StringBuilder();
            boolean inCycle = false;
            for (Class<?> c : currentlyInCreation) {
                if (c == implementation) inCycle = true;
                if (inCycle) cycle.append(c.getSimpleName()).append(" → ");
            }
            cycle.append(implementation.getSimpleName());
            throw new BeanCreationException("Circular dependency detected: " + cycle
                    + "\nBreak the cycle, or use @Autowired field injection on one side.");
        }

        currentlyInCreation.add(implementation);
        try {
            return createBean(implementation);
        } finally {
            currentlyInCreation.remove(implementation);
        }
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
     * 4. Store in beansByType map: Class → Instance
     * 5. Store in beansByName map: "className" → Instance
     * 6. If it's a controller, also add to controllers list
     *
     * Example:
     *   Input: UserService.class
     *   Output:
     *     beansByType.put(UserService.class, new UserService())
     *     beansByName.put("userService", theInstance)
     *
     * @param clazz The class to instantiate
     */
    private Object createBean(Class<?> clazz) {
        Constructor<?> constructor = selectConstructor(clazz);
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        // Resolve every constructor argument from the container
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = getOrCreateBean(parameterTypes[i]);
            if (args[i] == null) {
                throw new BeanCreationException("Cannot create " + clazz.getSimpleName()
                        + ": no bean of type " + parameterTypes[i].getName()
                        + " for constructor parameter " + i
                        + "\nMake sure the class has @Component, @Service, or @RestController annotation.");
            }
        }

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
        if (beansByName.containsKey(beanName)) {
            throw new BeanCreationException("Duplicate bean name '" + beanName + "' for " + clazz.getName());
        }

        // Register by type and name for later retrieval
        beansByType.put(clazz, instance);
        beansByName.put(beanName, instance);
        creationOrder.add(instance);

        // Track controllers separately - RouteResolver needs them
        String injected = parameterTypes.length == 0 ? "" : " (constructor-injected " + parameterTypes.length + " dependencies)";
        if (clazz.isAnnotationPresent(RestController.class)) {
            controllers.add(instance);
            logger.info("  ✓ Registered @RestController: {}{}", clazz.getSimpleName(), injected);
        } else if (clazz.isAnnotationPresent(Service.class)) {
            logger.info("  ✓ Registered @Service: {} as '{}'{}", clazz.getSimpleName(), beanName, injected);
        } else {
            logger.info("  ✓ Registered @Component: {} as '{}'{}", clazz.getSimpleName(), beanName, injected);
        }
        return instance;
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
     * Finds which component class should satisfy a requested type.
     *
     *   - Requested type IS a component             → itself
     *   - Exactly one component implements/extends it → that one
     *   - None                                       → null
     *   - More than one                              → error (ambiguous!)
     *
     * The last case is Spring's NoUniqueBeanDefinitionException, which Spring
     * lets you resolve with @Primary or @Qualifier.
     */
    private Class<?> resolveImplementation(Class<?> requestedType) {
        if (componentClasses.contains(requestedType)) {
            return requestedType;
        }
        List<Class<?>> candidates = new ArrayList<>();
        for (Class<?> candidate : componentClasses) {
            if (requestedType.isAssignableFrom(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.size() > 1) {
            throw new BeanCreationException("Expected a single bean of type " + requestedType.getName()
                    + " but found " + candidates.size() + ": " + candidates);
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

    private static String defaultBeanName(Class<?> clazz) {
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
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
     * After injection, userService field will reference the UserService bean!
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

                // Find a bean of the required type
                Object dependency = getOrCreateBean(fieldType);

                if (dependency != null) {
                    try {
                        // Make private field accessible (bypass access modifiers)
                        field.setAccessible(true);

                        // SET THE FIELD VALUE - This is the actual injection!
                        field.set(bean, dependency);

                        logger.info("  → Injected {} into {}.{}",
                                fieldType.getSimpleName(),
                                clazz.getSimpleName(),
                                field.getName());

                    } catch (IllegalAccessException e) {
                        throw new BeanCreationException("Failed to inject field " + field, e);
                    }
                } else if (autowired.required()) {
                    // The dependency was marked as required, but we can't find it!
                    throw new BeanCreationException(
                        "No bean found for required @Autowired dependency: " + fieldType.getName() +
                        " in " + clazz.getSimpleName() + "." + field.getName() +
                        "\nMake sure the class has @Component, @Service, or @RestController annotation."
                    );
                }
            }
        }
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

    /** Looks up an already-created bean by exact type, then by assignable type. */
    private Object findExistingBean(Class<?> type) {
        Object bean = beansByType.get(type);
        if (bean != null) {
            return bean;
        }
        Object match = null;
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                if (match != null) {
                    throw new BeanCreationException("Expected a single bean of type " + type.getName()
                            + " but found more than one");
                }
                match = entry.getValue();
            }
        }
        return match;
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
        return beansByType.size();
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

    /**
     * Registers a bean manually (useful for programmatic configuration).
     *
     * Sometimes you want to add a bean without using annotations:
     *   context.registerBean(DataSource.class, myDataSource);
     */
    public <T> void registerBean(Class<T> type, T instance) {
        beansByType.put(type, instance);
        beansByName.put(defaultBeanName(type), instance);
        creationOrder.add(instance);

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
        beansByType.clear();
        beansByName.clear();
        controllers.clear();
        creationOrder.clear();
    }
}
