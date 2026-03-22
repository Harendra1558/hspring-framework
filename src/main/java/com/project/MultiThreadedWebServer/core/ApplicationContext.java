package com.project.MultiThreadedWebServer.core;

import com.project.MultiThreadedWebServer.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
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
 * COMPLETE BEAN LIFECYCLE (All 6 Steps)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * STEP 1: COMPONENT SCANNING
 *   → Find all @Component, @Service, @RestController, @Configuration classes
 * 
 * STEP 2: BEAN CREATION
 *   → Instantiate each class via reflection
 *   → Process @Configuration → call @Bean factory methods
 * 
 * STEP 3: @VALUE INJECTION
 *   → Load application.properties
 *   → Inject property values into @Value annotated fields
 * 
 * STEP 4: DEPENDENCY INJECTION
 *   → For each @Autowired field, find the matching bean and inject it
 *   → Use @Qualifier to resolve ambiguity when multiple beans match
 * 
 * STEP 5: @POSTCONSTRUCT
 *   → Call initialization methods AFTER all injection is complete
 * 
 * STEP 6: READY
 *   → Beans are fully initialized and ready for use!
 * 
 * On shutdown:
 * STEP 7: @PREDESTROY
 *   → Call cleanup methods before destroying beans
 */
public class ApplicationContext {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationContext.class);

    /**
     * Storage for all managed beans, indexed by their class type.
     * Using ConcurrentHashMap for thread-safety.
     */
    private final Map<Class<?>, Object> beansByType = new ConcurrentHashMap<>();
    
    /**
     * Storage for beans indexed by name.
     * The name is derived from the class name with first letter lowercase,
     * or explicitly set via @Component("customName").
     */
    private final Map<String, Object> beansByName = new ConcurrentHashMap<>();
    
    /**
     * Special list tracking only controllers (for route registration).
     */
    private final List<Object> controllers = new ArrayList<>();

    /**
     * Properties loader — reads application.properties for @Value injection.
     */
    private final PropertiesLoader propertiesLoader;

    /**
     * Creates the ApplicationContext and performs the full bean lifecycle.
     * 
     * @param basePackage The package to scan (e.g., "com.project.MultiThreadedWebServer")
     */
    public ApplicationContext(String basePackage) {
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║      Initializing IoC Container (ApplicationContext)     ║");
        logger.info("╚══════════════════════════════════════════════════════════╝");
        logger.info("Base package: {}", basePackage);
        
        // ═══════════════════════════════════════════════════════════════════════
        // STEP 0: LOAD CONFIGURATION
        // Load application.properties BEFORE creating any beans
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 0: Loading application.properties...               │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        this.propertiesLoader = new PropertiesLoader();

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 1: COMPONENT SCANNING
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 1: Scanning for components...                      │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        List<Class<?>> classes = scanPackage(basePackage);
        logger.info("Found {} classes to analyze", classes.size());
        
        // ═══════════════════════════════════════════════════════════════════════
        // STEP 2: BEAN CREATION
        // For @Component/@Service/@RestController → create via no-arg constructor
        // For @Configuration → create instance, then call @Bean methods
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 2: Creating beans...                               │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        for (Class<?> clazz : classes) {
            if (isComponent(clazz)) {
                createBean(clazz);
            }
            if (clazz.isAnnotationPresent(Configuration.class)) {
                processConfiguration(clazz);
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // STEP 3: @VALUE INJECTION
        // Inject property values from application.properties
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 3: Injecting @Value properties...                  │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        injectValues();

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 4: DEPENDENCY INJECTION (@Autowired + @Qualifier)
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 4: Injecting @Autowired dependencies...            │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        injectDependencies();

        // ═══════════════════════════════════════════════════════════════════════
        // STEP 5: @POSTCONSTRUCT CALLBACKS
        // Call initialization methods after all injection is done
        // ═══════════════════════════════════════════════════════════════════════
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│ STEP 5: Calling @PostConstruct methods...               │");
        logger.info("└─────────────────────────────────────────────────────────┘");
        
        invokePostConstruct();
        
        logger.info("═══════════════════════════════════════════════════════════");
        logger.info("ApplicationContext ready with {} beans", beansByType.size());
        logger.info("═══════════════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BEAN DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if a class should be managed by the container.
     * Returns true for @Component, @Service, or @RestController.
     */
    private boolean isComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class) ||
               clazz.isAnnotationPresent(Service.class) ||
               clazz.isAnnotationPresent(RestController.class);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STEP 2a: BEAN CREATION (Component Scanning)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a bean instance and registers it in the container.
     */
    private void createBean(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            String beanName = getBeanName(clazz);
            
            beansByType.put(clazz, instance);
            beansByName.put(beanName, instance);
            
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

    // ═══════════════════════════════════════════════════════════════════════════
    // STEP 2b: @CONFIGURATION + @BEAN PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes a @Configuration class.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW @CONFIGURATION PROCESSING WORKS
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
     * 1. Create AppConfig instance
     * 2. Find methods with @Bean annotation
     * 3. Call objectMapper() method → gets an ObjectMapper instance
     * 4. Register that instance with key = ObjectMapper.class
     * 
     * Now anyone can @Autowired ObjectMapper!
     */
    private void processConfiguration(Class<?> configClass) {
        try {
            Object configInstance = configClass.getDeclaredConstructor().newInstance();
            logger.info("  ⚙ Processing @Configuration: {}", configClass.getSimpleName());

            for (Method method : configClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class)) {
                    // Call the @Bean method to get the bean instance
                    Object beanInstance = method.invoke(configInstance);
                    
                    if (beanInstance != null) {
                        // Register by return type
                        Class<?> beanType = method.getReturnType();
                        beansByType.put(beanType, beanInstance);
                        
                        // Determine bean name (from annotation or method name)
                        Bean beanAnnotation = method.getAnnotation(Bean.class);
                        String beanName = beanAnnotation.value().isEmpty() 
                                ? method.getName() 
                                : beanAnnotation.value();
                        beansByName.put(beanName, beanInstance);
                        
                        logger.info("  ✓ Registered @Bean: {} → {}()", 
                                beanType.getSimpleName(), method.getName());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("  ✗ Failed to process @Configuration: {}", configClass.getName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STEP 3: @VALUE INJECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Injects @Value properties from application.properties into bean fields.
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
     * 1. Iterate over all beans
     * 2. For each bean, check fields for @Value
     * 3. Read the property key from annotation
     * 4. Look up the value in PropertiesLoader
     * 5. Convert to the correct type (String, int, boolean, etc.)
     * 6. Set the field value via reflection
     */
    private void injectValues() {
        for (Object bean : beansByType.values()) {
            Class<?> clazz = bean.getClass();
            
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Value.class)) {
                    Value valueAnnotation = field.getAnnotation(Value.class);
                    String propertyKey = valueAnnotation.value();
                    String propertyValue = propertiesLoader.getProperty(propertyKey);
                    
                    if (propertyValue != null) {
                        try {
                            field.setAccessible(true);
                            // Convert String to the field's actual type
                            Object converted = convertValue(propertyValue, field.getType());
                            field.set(bean, converted);
                            
                            logger.info("  → @Value(\"{}\") = \"{}\" → {}.{}", 
                                    propertyKey, propertyValue, 
                                    clazz.getSimpleName(), field.getName());
                        } catch (Exception e) {
                            logger.error("  ✗ Failed to inject @Value for {}.{}", 
                                    clazz.getSimpleName(), field.getName(), e);
                        }
                    } else {
                        logger.warn("  ⚠ Property '{}' not found for {}.{}", 
                                propertyKey, clazz.getSimpleName(), field.getName());
                    }
                }
            }
        }
    }

    /**
     * Converts a String property value to the target field type.
     * 
     * Supports: String, int, long, boolean, double
     */
    private Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STEP 4: @AUTOWIRED + @QUALIFIER DEPENDENCY INJECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Injects @Autowired dependencies into all beans.
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
     */
    private void injectFields(Object bean) {
        Class<?> clazz = bean.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                Autowired autowired = field.getAnnotation(Autowired.class);
                Object dependency;

                // Check for @Qualifier — lookup by NAME instead of by TYPE
                if (field.isAnnotationPresent(Qualifier.class)) {
                    String qualifierName = field.getAnnotation(Qualifier.class).value();
                    dependency = getBean(qualifierName);
                    
                    if (dependency != null) {
                        logger.info("  → @Qualifier(\"{}\") resolved {} for {}.{}", 
                                qualifierName, dependency.getClass().getSimpleName(),
                                clazz.getSimpleName(), field.getName());
                    }
                } else {
                    // Default: lookup by TYPE
                    dependency = getBean(field.getType());
                }
                
                if (dependency != null) {
                    try {
                        field.setAccessible(true);
                        field.set(bean, dependency);
                        
                        if (!field.isAnnotationPresent(Qualifier.class)) {
                            logger.info("  → Injected {} into {}.{}", 
                                    field.getType().getSimpleName(), 
                                    clazz.getSimpleName(), 
                                    field.getName());
                        }
                    } catch (IllegalAccessException e) {
                        logger.error("  ✗ Failed to inject field: {}", field.getName(), e);
                    }
                } else if (autowired.required()) {
                    throw new RuntimeException(
                        "No bean found for required @Autowired dependency: " + field.getType().getName() +
                        "\nMake sure the class has @Component, @Service, or @RestController annotation."
                    );
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STEP 5: @POSTCONSTRUCT LIFECYCLE CALLBACKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Invokes @PostConstruct methods on all beans.
     * 
     * This runs AFTER all dependencies have been injected,
     * so beans can safely use their @Autowired fields.
     */
    private void invokePostConstruct() {
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            Object bean = entry.getValue();
            Class<?> clazz = entry.getKey();

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(bean);
                        logger.info("  ✓ @PostConstruct: {}.{}()", 
                                clazz.getSimpleName(), method.getName());
                    } catch (Exception e) {
                        logger.error("  ✗ @PostConstruct failed for {}.{}()", 
                                clazz.getSimpleName(), method.getName(), e);
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHUTDOWN: @PREDESTROY LIFECYCLE CALLBACKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Shuts down the application context.
     * 
     * 1. Calls @PreDestroy methods on all beans (cleanup callbacks)
     * 2. Clears all bean references
     */
    public void close() {
        logger.info("Shutting down ApplicationContext...");
        
        // Call @PreDestroy on all beans
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            Object bean = entry.getValue();
            Class<?> clazz = entry.getKey();

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PreDestroy.class)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(bean);
                        logger.info("  ✓ @PreDestroy: {}.{}()", 
                                clazz.getSimpleName(), method.getName());
                    } catch (Exception e) {
                        logger.error("  ✗ @PreDestroy failed for {}.{}()", 
                                clazz.getSimpleName(), method.getName(), e);
                    }
                }
            }
        }
        
        beansByType.clear();
        beansByName.clear();
        controllers.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BEAN RETRIEVAL METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets a bean by its type.
     * Also handles interface lookups via isAssignableFrom.
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        if (beansByType.containsKey(type)) {
            return (T) beansByType.get(type);
        }
        
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return (T) entry.getValue();
            }
        }
        
        return null;
    }

    /** Gets a bean by its name. */
    public Object getBean(String name) {
        return beansByName.get(name);
    }

    /** Gets all registered controllers. */
    public List<Object> getControllers() {
        return new ArrayList<>(controllers);
    }

    /** Gets the properties loader (for programmatic access). */
    public PropertiesLoader getPropertiesLoader() {
        return propertiesLoader;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MANUAL BEAN REGISTRATION (Fallback for JAR mode)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Registers a bean manually. */
    public <T> void registerBean(Class<T> type, T instance) {
        beansByType.put(type, instance);
        String beanName = Character.toLowerCase(type.getSimpleName().charAt(0)) + 
                          type.getSimpleName().substring(1);
        beansByName.put(beanName, instance);
        
        if (type.isAnnotationPresent(RestController.class)) {
            controllers.add(instance);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER: BEAN NAME GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    private String getBeanName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Component.class)) {
            String name = clazz.getAnnotation(Component.class).value();
            if (!name.isEmpty()) return name;
        }
        if (clazz.isAnnotationPresent(Service.class)) {
            String name = clazz.getAnnotation(Service.class).value();
            if (!name.isEmpty()) return name;
        }
        
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER: PACKAGE SCANNING
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Class<?>> scanPackage(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        
        try {
            String path = basePackage.replace('.', '/');
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

    private void scanDirectory(File directory, String packageName, List<Class<?>> classes) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        classes.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not load class: {}", className);
                }
            }
        }
    }
}
