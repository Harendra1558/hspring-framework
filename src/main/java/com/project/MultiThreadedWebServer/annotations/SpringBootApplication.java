package com.project.MultiThreadedWebServer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                       @SPRINGBOOTAPPLICATION                                  ║
 * ║                  (The Iconic Entry Point Annotation)                         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This is the MOST important annotation in Spring Boot. Every Spring Boot app
 * starts with this on the main class:
 * 
 *   @SpringBootApplication
 *   public class MyApp {
 *       public static void main(String[] args) {
 *           SpringApplication.run(MyApp.class, args);
 *       }
 *   }
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHAT IT ACTUALLY IS (A META-ANNOTATION)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * In real Spring Boot, @SpringBootApplication is a COMBINATION of 3 annotations:
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  @SpringBootApplication  =  @Configuration                                 │
 * │                             + @EnableAutoConfiguration                      │
 * │                             + @ComponentScan                               │
 * │                                                                             │
 * │  @Configuration          → This class can define @Bean methods             │
 * │  @EnableAutoConfiguration → Auto-configure based on classpath dependencies  │
 * │  @ComponentScan          → Scan this package and sub-packages for beans     │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * In HSpring, we support:
 *   - scanBasePackages: which packages to scan (like @ComponentScan)
 *   - The annotated class itself acts as @Configuration
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY INSIGHT
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * The reason your main class MUST be in the ROOT package is because
 * @ComponentScan scans from the annotated class's package DOWNWARD.
 * 
 *   com.myapp/                 ← @SpringBootApplication is here
 *   com.myapp.controller/     ← ✓ scanned (sub-package)
 *   com.myapp.service/        ← ✓ scanned (sub-package)
 *   com.other/                ← ✗ NOT scanned (different root)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpringBootApplication {

    /**
     * Base packages to scan for components.
     * Default: the package of the annotated class (and all sub-packages).
     * 
     * Same as Spring's @ComponentScan(basePackages = {...})
     */
    String[] scanBasePackages() default {};
}
