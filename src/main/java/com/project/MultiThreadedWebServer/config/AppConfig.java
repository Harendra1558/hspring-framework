package com.project.MultiThreadedWebServer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.project.MultiThreadedWebServer.annotations.Bean;
import com.project.MultiThreadedWebServer.annotations.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                          APPLICATION CONFIG                                   ║
 * ║              (@Configuration + @Bean Example)                                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This class demonstrates Java-based bean configuration — the SECOND way
 * to register beans in Spring (the first being component scanning).
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHY USE @CONFIGURATION + @BEAN?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * You CAN'T put @Component on classes you don't own (like ObjectMapper,
 * DataSource, RestTemplate). Instead, you create a @Bean factory method:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  // You CAN'T do this — ObjectMapper is from Jackson library!              │
 * │  @Component                                                                 │
 * │  public class ObjectMapper { ... }  // ❌ You don't own this class         │
 * │                                                                             │
 * │  // Instead, use a @Bean factory method:                                    │
 * │  @Configuration                                                             │
 * │  public class AppConfig {                                                   │
 * │      @Bean                                                                  │
 * │      public ObjectMapper objectMapper() {                                   │
 * │          return new ObjectMapper();  // ✅ You control creation!            │
 * │      }                                                                      │
 * │  }                                                                          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * HOW THE FRAMEWORK PROCESSES THIS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. ApplicationContext finds this class (has @Configuration annotation)
 * 2. Creates an instance of AppConfig
 * 3. Scans for methods with @Bean annotation
 * 4. Calls each @Bean method
 * 5. Registers the return value as a bean
 * 
 * Result: ObjectMapper instance is now in the IoC container!
 * Any class with @Autowired ObjectMapper will get this instance.
 */
@Configuration
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    /**
     * Creates a pre-configured ObjectMapper bean.
     * 
     * ObjectMapper is from the Jackson library — we can't put @Component
     * on it. Instead, we create it here with our preferred configuration.
     * 
     * This bean can then be @Autowired into any service or controller.
     * 
     * @return A configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Pretty-print JSON output for readability
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        logger.info("Created ObjectMapper bean with INDENT_OUTPUT enabled");
        return mapper;
    }
}
