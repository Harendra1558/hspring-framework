package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                           @POSTCONSTRUCT                                     ║
 * ║                    (Bean Lifecycle - Initialization)                          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Marks a method to be called AFTER the bean is fully created and all
 * dependencies have been injected. Similar to javax.annotation.PostConstruct.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEAN LIFECYCLE IN SPRING
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * A bean goes through several phases:
 * 
 *   ┌──────────────────────────────────────────────────────────────────────────┐
 *   │  1. INSTANTIATION     → Constructor called (new MyService())           │
 *   │  2. PROPERTY SETTING  → @Autowired fields injected                     │
 *   │  3. @PostConstruct    → YOUR initialization method runs ← THIS!       │
 *   │  4. READY FOR USE     → Bean is fully initialized                      │
 *   │  ...                                                                    │
 *   │  5. @PreDestroy       → YOUR cleanup method runs                       │
 *   │  6. GARBAGE COLLECTED → Bean is removed from container                 │
 *   └──────────────────────────────────────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHY NOT JUST USE THE CONSTRUCTOR?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * At constructor time, @Autowired dependencies are NOT yet injected!
 * 
 * ┌─────────────────────────────── BAD ────────────────────────────────────────┐
 * │  @Service                                                                  │
 * │  public class ReportService {                                              │
 * │      @Autowired                                                            │
 * │      private UserService userService;                                      │
 * │                                                                            │
 * │      public ReportService() {                                              │
 * │          userService.loadData();  // ❌ NullPointerException!              │
 * │          // userService is null here because injection hasn't happened     │
 * │      }                                                                     │
 * │  }                                                                         │
 * └────────────────────────────────────────────────────────────────────────────┘
 * 
 * ┌─────────────────────────────── GOOD ───────────────────────────────────────┐
 * │  @Service                                                                  │
 * │  public class ReportService {                                              │
 * │      @Autowired                                                            │
 * │      private UserService userService;                                      │
 * │                                                                            │
 * │      @PostConstruct                                                        │
 * │      public void init() {                                                  │
 * │          userService.loadData();  // ✅ Works! Dependencies are injected   │
 * │      }                                                                     │
 * │  }                                                                         │
 * └────────────────────────────────────────────────────────────────────────────┘
 * 
 * RULES:
 * - The method must take no arguments
 * - The method must return void
 * - Only ONE method per class should have @PostConstruct
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostConstruct {
}
