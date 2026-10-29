package com.project.MultiThreadedWebServer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                          PROPERTIES LOADER                                    ║
 * ║              (Externalized Configuration Manager)                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class loads key-value pairs from application.properties on the classpath
 * and makes them available for @Value injection.
 * Similar to Spring's PropertySourcesPlaceholderConfigurer / Environment.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HOW SPRING LOADS CONFIGURATION
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Spring Boot automatically reads from these sources (in priority order):
 *   1. Command-line arguments       (--server.port=9090)
 *   2. System properties            (java -Dserver.port=9090)
 *   3. application.properties       (src/main/resources/)
 *   4. Default values               (@Value("server.port:8080"))
 * 
 * Our simplified implementation reads from (highest priority first):
 *   1. Command-line arguments       (--server.port=9090)
 *   2. System properties            (java -Dserver.port=9090)
 *   3. Environment variables        (SERVER_PORT=9090)
 *   4. application.properties on the classpath
 *   5. Default values (if property not found, returns the default)
 *
 * Environment variable names follow Spring's "relaxed binding":
 * upper case, with '.' and '-' replaced by '_':
 *   server.thread-pool-size  →  SERVER_THREAD_POOL_SIZE
 *
 * PORT is also accepted for server.port (after SERVER_PORT), because that is
 * the variable Railway, Render and Heroku inject.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * FILE FORMAT (application.properties)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  # Comments start with #                                                    │
 * │  server.port=8080                                                           │
 * │  server.thread-pool-size=10                                                 │
 * │  app.name=HSpring Framework                                                 │
 * │  app.version=1.0.0                                                          │
 * │  app.debug=false                                                            │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Each line is a key=value pair.
 * Java's Properties class handles the parsing for us.
 */
public class PropertiesLoader {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesLoader.class);

    /** Loaded properties stored in a thread-safe map */
    private final Map<String, String> properties = new ConcurrentHashMap<>();

    /** Highest-priority values, e.g. parsed from --key=value command-line arguments */
    private final Map<String, String> overrides;

    /** The name of the properties file on the classpath */
    private static final String DEFAULT_FILE = "application.properties";

    /** Extra environment variable names accepted for a key, checked after the relaxed name */
    private static final Map<String, String> ENVIRONMENT_ALIASES = Map.of("server.port", "PORT");

    /**
     * Creates a PropertiesLoader and loads application.properties from the classpath.
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     * HOW CLASSPATH RESOURCE LOADING WORKS
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * When you put a file in src/main/resources/, Maven copies it to the 
     * "classpath" (target/classes/). We can then load it using:
     * 
     *   getClass().getClassLoader().getResourceAsStream("application.properties")
     * 
     * This works both in development AND inside a JAR file, because
     * the classloader knows how to read from both directories and JARs.
     */
    public PropertiesLoader() {
        this(Map.of());
    }

    /**
     * Creates a PropertiesLoader whose overrides win over every other source.
     *
     * @param overrides e.g. {"server.port" → "9090"} from --server.port=9090
     */
    public PropertiesLoader(Map<String, String> overrides) {
        this.overrides = Map.copyOf(overrides);
        loadProperties(DEFAULT_FILE);
    }

    /**
     * Loads properties from the given file on the classpath.
     * 
     * @param fileName Name of the properties file (e.g., "application.properties")
     */
    private void loadProperties(String fileName) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                logger.warn("Properties file '{}' not found on classpath. Using defaults.", fileName);
                return;
            }

            // Java's Properties class parses key=value pairs for us
            Properties props = new Properties();
            props.load(inputStream);

            // Copy into our ConcurrentHashMap
            for (String key : props.stringPropertyNames()) {
                properties.put(key, props.getProperty(key));
            }

            logger.info("Loaded {} properties from '{}'", properties.size(), fileName);
            properties.forEach((key, value) -> 
                logger.info("  {} = {}", key, value));

        } catch (IOException e) {
            logger.error("Error loading properties file: {}", fileName, e);
        }
    }

    /**
     * Gets a property value by key, checking every source in priority order.
     *
     * @param key The property key (e.g., "server.port")
     * @return The value, or null if not found
     */
    public String getProperty(String key) {
        String value = overrides.get(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        if (value == null) {
            value = System.getenv(toEnvironmentVariableName(key));
        }
        if (value == null && ENVIRONMENT_ALIASES.containsKey(key)) {
            value = System.getenv(ENVIRONMENT_ALIASES.get(key));
        }
        if (value == null) {
            value = properties.get(key);
        }
        return value;
    }

    /**
     * Gets a property value with a default fallback.
     *
     * @param key          The property key
     * @param defaultValue Value to return if property not found
     * @return The property value, or defaultValue if not found
     */
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    /** server.thread-pool-size → SERVER_THREAD_POOL_SIZE */
    static String toEnvironmentVariableName(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    /**
     * Returns the properties loaded from application.properties as an unmodifiable map
     * (without overrides from arguments, system properties or the environment).
     */
    public Map<String, String> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
