package com.project.MultiThreadedWebServer.model;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                              USER MODEL                                      ║
 * ║                    (Domain Object / POJO / DTO)                              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * This is a simple POJO (Plain Old Java Object) representing a User entity.
 * In Spring Boot applications, you would typically have model/entity classes
 * like this to represent your data.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * WHY USE A MODEL CLASS INSTEAD OF Map<String, Object>?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Using raw Maps:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Map<String, Object> user = new HashMap<>();                                │
 * │  user.put("name", "John");                                                  │
 * │  user.put("emil", "john@mail.com");  // ❌ Typo! No compiler error!        │
 * │  String name = (String) user.get("name");  // Ugly casting!               │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Using a model class:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  User user = new User();                                                    │
 * │  user.setName("John");                                                      │
 * │  user.setEmil("john@mail.com");  // ❌ Compiler error! Typo caught!        │
 * │  String name = user.getName();   // Type-safe, no casting!                 │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * Benefits of model classes:
 *   1. Type Safety    → Compiler catches errors at build time
 *   2. IDE Support    → Auto-complete for field names
 *   3. Validation     → Can add @NotNull, @Email, etc.
 *   4. Documentation  → Fields are self-documenting
 *   5. Serialization  → Jackson automatically converts to/from JSON
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * IN SPRING BOOT WITH JPA
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * In a real Spring Boot app, this class would also have JPA annotations:
 * 
 *   @Entity                          ← Marks as a database table
 *   @Table(name = "users")           ← Table name
 *   public class User {
 *       @Id                          ← Primary key
 *       @GeneratedValue              ← Auto-increment
 *       private Long id;
 * 
 *       @Column(nullable = false)    ← Not-null constraint
 *       private String name;
 *   }
 * 
 * Our simplified version doesn't use JPA but follows the same POJO pattern.
 */
public class User {

    private Long id;
    private String name;
    private String email;
    private String role;
    private long createdAt;

    /** Default constructor (required for Jackson deserialization) */
    public User() {
    }

    /** Constructor with required fields */
    public User(Long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdAt = System.currentTimeMillis();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS AND SETTERS
    // In real Spring Boot, you might use Lombok @Data to auto-generate these.
    // ═══════════════════════════════════════════════════════════════════════════

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email + "', role='" + role + "'}";
    }
}
