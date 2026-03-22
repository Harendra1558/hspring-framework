package com.project.MultiThreadedWebServer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
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
 * Our simplified implementation reads from:
 *   1. application.properties on the classpath
 *   2. Default values (if property not found, returns the default)
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

    /** The name of the properties file on the classpath */
    private static final String DEFAULT_FILE = "application.properties";

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
     * Gets a property value by key.
     * 
     * @param key The property key (e.g., "server.port")
     * @return The value, or null if not found
     */
    public String getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Gets a property value with a default fallback.
     * 
     * @param key          The property key
     * @param defaultValue Value to return if property not found
     * @return The property value, or defaultValue if not found
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Returns all loaded properties as an unmodifiable map.
     */
    public Map<String, String> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
