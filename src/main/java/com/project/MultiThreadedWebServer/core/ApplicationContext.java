package com.project.MultiThreadedWebServer.core;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Component;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * STEP 2: BEAN CREATION
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  For each annotated class found:                                           │
 * │    1. Create an instance using reflection: clazz.newInstance()             │
 * │    2. Store in a Map for later retrieval                                   │
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
     *   T=10ms  Bean creation complete (created M beans)
     *   T=15ms  Dependency injection complete
     *   T=15ms  Context is READY - beans can be used!
     * 
     * @param basePackage The package to scan (e.g., "com.project.MultiThreadedWebServer")
     */
    public ApplicationContext(String basePackage) {
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║      Initializing IoC Container (ApplicationContext)     ║");
        logger.info("╚══════════════════════════════════════════════════════════╝");
        logger.info("Base package: {}", basePackage);
        
        // ═══════════════════════════════════════════════════════════════════════
        // STEP 1: COMPONENT SCANNING
        // Find all .class files in the package and its subpackages
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 1: Scanning for components...                      │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        List<Class<?>> classes = scanPackage(basePackage);
        logger.info("Found {} classes to analyze", classes.size());
        
        // ═══════════════════════════════════════════════════════════════════════
        // STEP 2: BEAN CREATION
        // For each class with @Component, @Service, or @RestController,
        // create an instance and store it
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 2: Creating beans...                               │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        for (Class<?> clazz : classes) {
            if (isComponent(clazz)) {
                createBean(clazz);
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // STEP 3: DEPENDENCY INJECTION
        // For each bean, find @Autowired fields and inject dependencies
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 3: Injecting dependencies...                       │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        injectDependencies();
        
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
     * Creates a bean instance and registers it in the container.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW BEAN CREATION WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * 1. Get the class's default constructor (no arguments)
     * 2. Call newInstance() to create an object
     * 3. Store in beansByType map: Class → Instance
     * 4. Store in beansByName map: "className" → Instance
     * 5. If it's a controller, also add to controllers list
     * 
     * Example:
     *   Input: UserService.class
     *   Output: 
     *     beansByType.put(UserService.class, new UserService())
     *     beansByName.put("userService", theInstance)
     * 
     * @param clazz The class to instantiate
     */
    private void createBean(Class<?> clazz) {
        try {
            // Use reflection to create instance via default constructor
            // This is equivalent to: Object instance = new UserService();
            Object instance = clazz.getDeclaredConstructor().newInstance();
            
            // Generate bean name (class name with lowercase first letter)
            String beanName = getBeanName(clazz);
            
            // Register by type and name for later retrieval
            beansByType.put(clazz, instance);
            beansByName.put(beanName, instance);
            
            // Track controllers separately - RouteResolver needs them
            if (clazz.isAnnotationPresent(RestController.class)) {
                controllers.add(instance);
                logger.info("  ✓ Registered @RestController: {}", clazz.getSimpleName());
            } else if (clazz.isAnnotationPresent(Service.class)) {
                logger.info("  ✓ Registered @Service: {} as '{}'", clazz.getSimpleName(), beanName);
            } else {
                logger.info("  ✓ Registered @Component: {} as '{}'", clazz.getSimpleName(), beanName);
            }
            
        } catch (Exception e) {
            logger.error("  ✗ Failed to create bean for: {}", clazz.getName(), e);
        }
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
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * Injects @Autowired dependencies into all beans.
     * 
     * This method iterates through ALL beans and calls injectFields for each.
     */
    private void injectDependencies() {
        for (Object bean : beansByType.values()) {
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
     * 1. Get all declared fields of the class
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
        Class<?> clazz = bean.getClass();
        
        // Iterate through ALL fields (including private ones)
        for (Field field : clazz.getDeclaredFields()) {
            
            // Check if field has @Autowired annotation
            if (field.isAnnotationPresent(Autowired.class)) {
                Autowired autowired = field.getAnnotation(Autowired.class);
                Class<?> fieldType = field.getType();
                
                // Find a bean of the required type
                Object dependency = getBean(fieldType);
                
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
                        logger.error("  ✗ Failed to inject field: {}", field.getName(), e);
                    }
                } else if (autowired.required()) {
                    // The dependency was marked as required, but we can't find it!
                    throw new RuntimeException(
                        "No bean found for required @Autowired dependency: " + fieldType.getName() +
                        "\nMake sure the class has @Component, @Service, or @RestController annotation."
                    );
                }
            }
        }
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
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        // First, try direct match
        if (beansByType.containsKey(type)) {
            return (T) beansByType.get(type);
        }
        
        // Second, check if any bean implements/extends the requested type
        // This allows injecting interfaces instead of concrete classes
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return (T) entry.getValue();
            }
        }
        
        return null;
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
        String beanName = Character.toLowerCase(type.getSimpleName().charAt(0)) + 
                          type.getSimpleName().substring(1);
        beansByName.put(beanName, instance);
        
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
     * 2. Get the classpath URL for that path
     * 3. Recursively scan for .class files
     * 4. Load each class using Class.forName()
     * 
     * This is a simplified implementation. Real Spring uses more
     * sophisticated scanning (ASM library, classpath scanning, etc.)
     * 
     * @param basePackage The package to scan
     * @return List of all classes found
     */
    private List<Class<?>> scanPackage(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        
        try {
            // Convert "com.project.foo" to "com/project/foo"
            String path = basePackage.replace('.', '/');
            
            // Get the classpath resource for this path
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(path);
            
            if (resource != null) {
                File directory = new File(resource.toURI());
                if (directory.exists()) {
                    scanDirectory(directory, basePackage, classes);
                }
            }
        } catch (Exception e) {
            logger.error("Error scanning package: {}", basePackage, e);
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
    private void scanDirectory(File directory, String packageName, List<Class<?>> classes) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse into subdirectory
                // "com.project" + "." + "controller" = "com.project.controller"
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                // Found a .class file - load it!
                // "UserController.class" → "com.project.controller.UserController"
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    
                    // Skip interfaces and abstract classes (can't instantiate them)
                    if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        classes.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not load class: {}", className);
                }
            }
        }
    }

    /**
     * Shuts down the application context and releases resources.
     * 
     * In a real application, this would:
     * - Close database connections
     * - Stop scheduled tasks
     * - Release external resources
     * - Call @PreDestroy methods
     */
    public void close() {
        logger.info("Shutting down ApplicationContext...");
        beansByType.clear();
        beansByName.clear();
        controllers.clear();
    }
}
