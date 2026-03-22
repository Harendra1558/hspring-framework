package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                             @QUALIFIER                                       ║
 * ║              (Bean Disambiguation / Selection)                               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Used alongside @Autowired to specify WHICH bean to inject when multiple
 * beans of the same type exist. Similar to Spring's @Qualifier.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * THE PROBLEM @QUALIFIER SOLVES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Imagine you have TWO implementations of a notification service:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Component("emailNotifier")                                                 │
 * │  public class EmailNotifier implements Notifier { ... }                     │
 * │                                                                              │
 * │  @Component("smsNotifier")                                                   │
 * │  public class SmsNotifier implements Notifier { ... }                       │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Now when you inject:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Autowired                                                                  │
 * │  private Notifier notifier;  // ❌ Which one? Email or SMS?                 │
 * │                               // Spring throws: "No qualifying bean"        │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Solution — use @Qualifier:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @Autowired                                                                  │
 * │  @Qualifier("emailNotifier")                                                │
 * │  private Notifier notifier;  // ✅ Clear! Use the EmailNotifier             │
 * └─────────────────────────────────────────────────────────────────────────────┘
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Qualifier {
    /**
     * The name of the bean to inject.
     * Must match the name given in @Component("name") or @Service("name")
     * or the default bean name (class name with lowercase first letter).
     */
    String value();
}
