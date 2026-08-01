# Spring Security Flow — Complete Explanation

This document explains **everything** about the Spring Security setup in this SpringEcom project — what each file does, how they connect, and the concepts behind them (DAO, BCrypt, UserDetails, etc.).

---

## Table of Contents

1. [The Big Picture — What Problem Are We Solving?](#1-the-big-picture)
2. [All the Files Involved](#2-all-the-files-involved)
3. [The Complete Authentication Flow (Step by Step)](#3-the-complete-authentication-flow)
4. [Deep Dive: Each File Explained](#4-deep-dive-each-file-explained)
5. [Key Concepts Explained](#5-key-concepts-explained)
6. [How Password Hashing (BCrypt) Works](#6-how-password-hashing-bcrypt-works)
7. [What is DAO Authentication?](#7-what-is-dao-authentication)
8. [SecurityFilterChain — The Gatekeeper](#8-securityfilterchain--the-gatekeeper)
9. [How It All Connects — Visual Map](#9-how-it-all-connects)
10. [Annotations Explained — What Each One Does](#10-annotations-explained)

---

## 1. The Big Picture

Without security, anyone can hit your API endpoints — view products, place orders, delete data. That's dangerous.

**Spring Security** adds a "gate" in front of your entire application. Every incoming HTTP request must pass through this gate. The gate checks:

- **Who are you?** → Authentication (username + password check)
- **Are you allowed to access this?** → Authorization (role/permission check)

In your project, you've set up **database-backed authentication** — users are stored in PostgreSQL, passwords are hashed, and Spring Security verifies credentials against the DB on every request.

---

## 2. All the Files Involved

Here are the 7 files that make up your security system, organized by their role:

```
📁 Your Security System
│
├── 🔐 CONFIG (the rules)
│   └── SecurityConfig.java          ← Master configuration: what's protected, what's public
│
├── 👤 MODEL (the data)
│   ├── User.java                    ← JPA Entity — maps to "users" table in PostgreSQL
│   └── UserPrincipal.java           ← Wrapper — adapts your User into Spring Security's format
│
├── 🗄️ REPOSITORY (database access)
│   └── UserRepo.java                ← JPA Repository — SQL queries for users
│
├── ⚙️ SERVICE (business logic)
│   ├── UserService.java             ← Handles registration (hashes password + saves)
│   └── MyUserDetailsService.java    ← Handles login lookup (finds user by username for Spring)
│
└── 🌐 CONTROLLER (endpoints)
    └── UserController.java          ← REST endpoint: POST /register
```

---

## 3. The Complete Authentication Flow

### A) Registration Flow (Creating a new user)

When you send `POST /register` with `{"id":1, "username":"haris", "password":"h@123"}`:

```
Step 1: Request hits SecurityConfig's SecurityFilterChain
        → "/register" matches .requestMatchers("/register").permitAll()
        → ALLOWED without any authentication ✅

Step 2: Request reaches UserController.register()
        → Receives the JSON body as a User object

Step 3: UserController calls UserService.saveUser(user)

Step 4: UserService does two things:
        a) Hashes the plain password "h@123" using BCryptPasswordEncoder(12)
           → becomes something like "$2a$12$LJ3m4ys..." (60 characters, irreversible)
        b) Saves the User to PostgreSQL via UserRepo.save(user)

Step 5: Response sent back with the saved User (including hashed password)
```

**Database after registration:**
| id | username | password |
|----|----------|----------|
| 1  | haris    | $2a$12$LJ3m4ysXqK... (hashed, NOT "h@123") |

---

### B) Authentication Flow (Accessing a protected endpoint)

When you send `GET /api/products` with Basic Auth (username: haris, password: h@123):

```
Step 1: Request hits SecurityConfig's SecurityFilterChain
        → "/api/products" does NOT match "/register"
        → Falls to .anyRequest().authenticated()
        → Authentication REQUIRED ⚠️

Step 2: .httpBasic() is configured
        → Spring reads the "Authorization: Basic aGFyaXM6aEAxMjM=" header
        → Decodes Base64 → extracts "haris" and "h@123"

Step 3: Spring calls the AuthenticationProvider (DaoAuthenticationProvider)
        → This is the bean we configured in SecurityConfig.authProvider()

Step 4: DaoAuthenticationProvider calls MyUserDetailsService.loadUserByUsername("haris")
        → MyUserDetailsService calls UserRepo.findByUsername("haris")
        → Gets the User object from the database
        → Wraps it in UserPrincipal and returns it

Step 5: DaoAuthenticationProvider compares passwords:
        → Takes the plain password from request: "h@123"
        → Takes the hashed password from DB (via UserPrincipal.getPassword()): "$2a$12$LJ3m4ys..."
        → Uses BCryptPasswordEncoder.matches("h@123", "$2a$12$LJ3m4ys...")
        → BCrypt hashes "h@123" with the same salt and checks if results match
        → If they match → ✅ AUTHENTICATED
        → If they don't → ❌ 401 Unauthorized

Step 6: Request proceeds to the ProductController
        → Returns the products data
```

---

## 4. Deep Dive: Each File Explained

### 4.1 — User.java (The Entity)

```java
@Data                       // Lombok: auto-generates getters, setters, toString, etc.
@Table(name = "users")      // Maps to the "users" table in PostgreSQL
@Entity                     // Marks this as a JPA entity (a DB table)
public class User {
    @Id
    private int id;         // Primary key
    private String username;
    private String password; // Stored as BCrypt hash, NOT plain text
}
```

**What it is:** Your database table represented as a Java class. Each field = a column. JPA/Hibernate handles the SQL for you.

**Important:** This is YOUR class. Spring Security doesn't know about it directly — that's why we need `UserPrincipal` as a bridge.

---

### 4.2 — UserPrincipal.java (The Bridge/Wrapper)

```java
public class UserPrincipal implements UserDetails {
    private User user;  // Wraps YOUR User entity

    // Spring Security calls these methods:
    getUsername()     → returns user.getUsername()     // Who is this user?
    getPassword()    → returns user.getPassword()     // What's the hashed password?
    getAuthorities() → returns "USER" role            // What permissions do they have?
    isAccountNonExpired()    → true                   // Is account still valid?
    isAccountNonLocked()     → true                   // Is account not locked?
    isCredentialsNonExpired()→ true                   // Is password not expired?
    isEnabled()              → true                   // Is account active?
}
```

**Why do we need this?**

Spring Security doesn't know what your `User` class looks like — you could have `email` instead of `username`, or `passcode` instead of `password`. Every project is different.

So Spring Security defines a **standard interface** called `UserDetails` with specific methods it expects. `UserPrincipal` implements that interface and translates YOUR `User` into Spring's expected format.

Think of it like an **adapter/translator**:
```
Your World (User.java)  ←→  UserPrincipal  ←→  Spring Security's World (UserDetails)
```

---

### 4.3 — UserRepo.java (Database Access)

```java
@Repository
public interface UserRepo extends JpaRepository<User, Integer> {
    User findByUsername(String username);  // Spring auto-generates the SQL query
}
```

**What it does:** Provides database operations for the `users` table.

- `JpaRepository<User, Integer>` gives you free methods: `save()`, `findById()`, `findAll()`, `delete()`, etc.
- `findByUsername(String username)` → Spring Data JPA reads the method name and auto-generates: `SELECT * FROM users WHERE username = ?`

No SQL writing needed. Spring figures it out from the method name.

---

### 4.4 — MyUserDetailsService.java (The Lookup Service)

```java
@Service  // Makes this a Spring bean — Spring can find and inject it
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo repo;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = repo.findByUsername(username);  // Find in DB
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new UserPrincipal(user);  // Wrap in Spring's format
    }
}
```

**Why does this exist?**

When someone tries to log in with username "haris", Spring Security needs to ask: *"Give me the user details for 'haris' from your database."*

Spring Security doesn't know how YOUR database works (PostgreSQL? MongoDB? File?). So it defines an interface `UserDetailsService` with one method: `loadUserByUsername()`.

YOU implement it to tell Spring: *"Here's how to find a user in MY database."*

**The flow:**
```
Spring Security: "I need user details for 'haris'"
       │
       ▼
MyUserDetailsService.loadUserByUsername("haris")
       │
       ▼
UserRepo.findByUsername("haris")  →  SQL query to PostgreSQL
       │
       ▼
Returns User entity  →  Wraps in UserPrincipal  →  Returns to Spring Security
```

---

### 4.5 — UserService.java (Registration Logic)

```java
@Service
public class UserService {

    @Autowired
    private UserRepo repo;
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public User saveUser(User user) {
        user.setPassword(encoder.encode(user.getPassword()));  // Hash the password
        return repo.save(user);                                 // Save to DB
    }
}
```

**What it does:** Handles user registration. The critical part is **never storing plain passwords**:

```
Input:  "h@123"
After:  "$2a$12$LJ3m4ysXqKzGQoYN3lM8wuRq9z..." (hashed)
Saved to DB: the hashed version only
```

The number `12` in `BCryptPasswordEncoder(12)` is the **strength/rounds** (explained in Section 6 below).

---

### 4.6 — UserController.java (The Endpoint)

```java
@RestController
public class UserController {

    @Autowired
    private UserService service;

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return service.saveUser(user);
    }
}
```

**What it does:** Exposes `POST /register` so users can create accounts via API. Takes JSON body, passes it to `UserService`, returns the saved user.

---

### 4.7 — SecurityConfig.java (The Master Configuration)

This is the most important file. Let's break it down line by line:

```java
@Configuration      // Tells Spring: "this class contains bean definitions"
@EnableWebSecurity  // Tells Spring: "activate Spring Security for this app"
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;
    // Spring injects MyUserDetailsService here (because it implements UserDetailsService)
```

#### Bean 1: AuthenticationProvider

```java
    @Bean
    public AuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(new BCryptPasswordEncoder(12));
        return provider;
    }
```

This configures HOW Spring verifies credentials:
- **DaoAuthenticationProvider** = "use a DAO (database) to authenticate" (explained in Section 7)
- `userDetailsService` = tells it WHERE to find users (our `MyUserDetailsService`)
- `BCryptPasswordEncoder(12)` = tells it HOW to compare passwords (using BCrypt hashing)

#### Bean 2: SecurityFilterChain

```java
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(customizer -> customizer.disable())
            // ↑ Disable CSRF protection (needed for REST APIs with Postman)

            .authorizeHttpRequests(request -> request
                .requestMatchers("/register").permitAll()
                // ↑ /register is PUBLIC — no login needed

                .anyRequest().authenticated())
                // ↑ Everything else requires authentication

            .httpBasic(Customizer.withDefaults())
            // ↑ Enable HTTP Basic Auth (username:password in header)

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            // ↑ Don't create sessions — every request must authenticate independently

        return http.build();
    }
```

---

## 5. Key Concepts Explained

### What is a Bean?

A **bean** is an object that Spring creates and manages for you. Instead of you writing `new MyUserDetailsService()`, you annotate the class with `@Service` or `@Component`, and Spring creates it automatically and injects it wherever needed using `@Autowired`.

```
@Service                          ← "Spring, please create and manage this object"
public class MyUserDetailsService

@Autowired                        ← "Spring, please inject the bean you created"
private UserDetailsService userDetailsService;
```

### What is UserDetails?

An **interface** defined by Spring Security. It represents the "security view" of a user. It has methods like:
- `getUsername()` — who is this user?
- `getPassword()` — what's their (hashed) password?
- `getAuthorities()` — what roles/permissions do they have?
- `isEnabled()` — is the account active?

Your `UserPrincipal` class implements this interface.

### What is UserDetailsService?

An **interface** with one method: `loadUserByUsername(String username)`. You implement it to tell Spring how to find a user in your specific database. Your `MyUserDetailsService` class implements this.

### What is CSRF and why disable it?

**CSRF (Cross-Site Request Forgery)** is an attack where a malicious website tricks your browser into making requests to your server using your existing session cookies.

Spring Security enables CSRF protection by default (requires a special token with each request). We **disable** it because:
- Our API is **stateless** (no sessions, no cookies)
- We use **Basic Auth / JWT tokens** in headers
- Postman and mobile apps can't easily handle CSRF tokens

For REST APIs, CSRF protection is typically not needed.

### What is STATELESS session management?

```java
SessionCreationPolicy.STATELESS
```

By default, when you log in, Spring creates a **session** on the server and gives your browser a **cookie**. On subsequent requests, the cookie tells Spring "this user already logged in."

With `STATELESS`, Spring does **NOT** create sessions. Every request must send credentials (username + password) freshly. The server doesn't remember you between requests.

**Why?** Because REST APIs should be stateless — each request is independent. This is essential for scalability (any server in a cluster can handle any request).

---

## 6. How Password Hashing (BCrypt) Works

### The Problem

If you store passwords as plain text in the database:
```
| username | password |
|----------|----------|
| haris    | h@123    |   ← If someone hacks your DB, they get all passwords!
```

### The Solution: Hashing

A **hash** is a one-way function — you can convert a password to a hash, but you can **never** convert it back.

```
"h@123"  →  BCrypt  →  "$2a$12$LJ3m4ysXqK..."  (one-way, irreversible)
"$2a$12$LJ3m4ysXqK..."  →  ???  →  IMPOSSIBLE to get "h@123" back
```

### BCrypt Specifically

BCrypt is one of the best password hashing algorithms because:

1. **It's slow on purpose** — makes brute-force attacks very hard
2. **It adds salt** — a random string added before hashing, so two users with the same password get different hashes
3. **Configurable strength** — the `12` in `BCryptPasswordEncoder(12)` means 2^12 = 4096 rounds of hashing

```
BCryptPasswordEncoder(12)
                      ↑
                      strength = 12
                      rounds = 2^12 = 4,096 iterations
                      Higher = slower but more secure
                      Typical range: 10-14
```

### BCrypt Hash Format

```
$2a$12$LJ3m4ysXqKzGQoYN3lM8wu...
 │   │  │                       │
 │   │  └── Salt (22 chars)     └── Hash (31 chars)
 │   └── Strength/Rounds (12)
 └── Algorithm version (2a = BCrypt)
```

### How Password Verification Works

When a user logs in with password "h@123", Spring does NOT decrypt the hash. Instead:

```
Step 1: Read the hash from DB: "$2a$12$LJ3m4ysXqK..."
Step 2: Extract the salt from the hash
Step 3: Hash the incoming password "h@123" with the SAME salt and rounds
Step 4: Compare the two hashes
        → If they match → password is correct ✅
        → If they don't → password is wrong ❌
```

This is why BCrypt is in **two places** in your code:
- `UserService` → `encoder.encode("h@123")` — creates the hash during **registration**
- `SecurityConfig` → `provider.setPasswordEncoder(new BCryptPasswordEncoder(12))` — verifies the hash during **login**

Both must use the **same strength (12)**, otherwise verification will fail.

---

## 7. What is DAO Authentication?

### What is DAO?

**DAO = Data Access Object.** It's a design pattern that separates database logic from business logic.

In your project:
- `UserRepo` is the DAO — it talks to the database
- `UserService` / `MyUserDetailsService` are the services — they use the DAO

### DaoAuthenticationProvider

`DaoAuthenticationProvider` is Spring Security's built-in authentication provider that uses a **DAO (database)** to verify users. It needs two things:

1. **UserDetailsService** — to load user data from the database
2. **PasswordEncoder** — to compare passwords

```java
DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
provider.setPasswordEncoder(new BCryptPasswordEncoder(12));
```

This tells Spring: *"When someone tries to log in, use `MyUserDetailsService` to find the user in the database, then use `BCryptPasswordEncoder` to verify their password."*

### Why "DAO" Authentication?

Spring Security supports many authentication types:
- **DaoAuthenticationProvider** → Verify against a database (what you're using)
- **LdapAuthenticationProvider** → Verify against LDAP/Active Directory
- **OAuth2** → Verify via Google, GitHub, etc.
- **InMemoryUserDetailsManager** → Hardcoded users (for testing only)

You're using DAO because your users are in PostgreSQL.

---

## 8. SecurityFilterChain — The Gatekeeper

Every HTTP request to your Spring app passes through a **chain of filters** before reaching your controller. Spring Security inserts its own filters into this chain.

```
HTTP Request
    │
    ▼
┌──────────────────────────────────┐
│     SecurityFilterChain          │
│                                  │
│  1. CSRF Filter (disabled)       │  ← We disabled this
│  2. Authorization Filter         │  ← Checks: is this URL public or protected?
│  3. BasicAuthenticationFilter    │  ← Reads username:password from header
│  4. DaoAuthenticationProvider    │  ← Verifies against DB using BCrypt
│                                  │
│  Result: ✅ Authenticated        │
│          OR ❌ 401 Unauthorized  │
└──────────────────────────────────┘
    │
    ▼ (only if authenticated)
┌──────────────────────────────────┐
│  Your Controller                 │
│  (ProductController, etc.)       │
└──────────────────────────────────┘
```

### The rules you've defined:

| Rule | Meaning |
|------|---------|
| `.csrf().disable()` | Don't require CSRF tokens (REST API) |
| `.requestMatchers("/register").permitAll()` | `/register` is public, no auth needed |
| `.anyRequest().authenticated()` | All other URLs need authentication |
| `.httpBasic(Customizer.withDefaults())` | Use HTTP Basic Auth (username:password in header) |
| `.sessionCreationPolicy(STATELESS)` | Don't save sessions, authenticate every request |

---

## 9. How It All Connects — Visual Map

```
                    ┌──────────────────────────────────────────────────────┐
                    │                  REGISTRATION FLOW                    │
                    │                                                      │
  POST /register    │   UserController ──→ UserService ──→ UserRepo        │
  {user, password}  │                      │                  │            │
                    │                      │ BCrypt.encode()  │ .save()    │
                    │                      │ "h@123" →        │            │
                    │                      │ "$2a$12$..."     ▼            │
                    │                                    PostgreSQL        │
                    │                                    (users table)     │
                    └──────────────────────────────────────────────────────┘


                    ┌──────────────────────────────────────────────────────┐
                    │                AUTHENTICATION FLOW                    │
                    │                                                      │
  GET /api/products │                                                      │
  + Basic Auth      │                                                      │
  (haris:h@123)     │                                                      │
        │           │                                                      │
        ▼           │                                                      │
  SecurityFilterChain                                                      │
        │           │                                                      │
        ▼           │                                                      │
  Is URL public?    │                                                      │
  /register? → No   │                                                      │
  → Needs auth      │                                                      │
        │           │                                                      │
        ▼           │                                                      │
  HTTP Basic Auth   │                                                      │
  → Decode header   │                                                      │
  → username="haris"│                                                      │
  → password="h@123"│                                                      │
        │           │                                                      │
        ▼           │                                                      │
  DaoAuthProvider   │                                                      │
        │           │                                                      │
        ├──→ MyUserDetailsService.loadUserByUsername("haris")               │
        │       │                                                          │
        │       ▼                                                          │
        │    UserRepo.findByUsername("haris")  ──→ PostgreSQL               │
        │       │                                                          │
        │       ▼                                                          │
        │    User{id=1, username="haris", password="$2a$12$..."}           │
        │       │                                                          │
        │       ▼                                                          │
        │    return new UserPrincipal(user)                                │
        │                                                                  │
        ├──→ BCryptPasswordEncoder.matches("h@123", "$2a$12$...")          │
        │                                                                  │
        │    Match? ──→ ✅ YES → Request goes to ProductController         │
        │              ❌ NO  → 401 Unauthorized                           │
        │                                                                  │
        └──────────────────────────────────────────────────────────────────┘
```

---

## Summary of Your Security Architecture

| Component | Class | Role |
|-----------|-------|------|
| Entity | `User` | Database table representation |
| Adapter | `UserPrincipal` | Translates `User` → Spring Security's `UserDetails` |
| Repository | `UserRepo` | Database queries (find by username) |
| Lookup Service | `MyUserDetailsService` | Implements `UserDetailsService` — loads user from DB |
| Registration Service | `UserService` | Hashes password + saves new user |
| Controller | `UserController` | `POST /register` endpoint |
| Security Config | `SecurityConfig` | Wires it all together — DAO provider, BCrypt, HTTP rules |

The key insight is that Spring Security is **interface-driven**. It defines interfaces (`UserDetails`, `UserDetailsService`, `PasswordEncoder`) and YOUR code implements them to plug into Spring's security engine. Spring doesn't care if your users are in PostgreSQL, MongoDB, or a text file — as long as you implement the right interfaces.

---

## 10. Annotations Explained — What Each One Does

Here is every important annotation used in your security setup, organized by file. For each annotation, we explain: **what it does**, **why you need it**, and **what breaks if you remove it**.

---

### 10.1 — SecurityConfig.java Annotations

#### `@Configuration`

```java
@Configuration
public class SecurityConfig {
```

| | |
|---|---|
| **What it does** | Tells Spring: *"This class contains bean definitions. Treat methods annotated with `@Bean` inside this class as factory methods that create objects Spring should manage."* |
| **Why needed** | Without it, Spring won't recognize `authProvider()` and `securityFilterChain()` as beans. They'd just be regular methods nobody calls. |
| **Without it** | Your security config won't be loaded. Spring Boot will use its **default** security config — which generates a random password at startup and prints it in the console. None of your custom rules would apply. |
| **Belongs to** | Spring Framework Core (`org.springframework.context.annotation.Configuration`) |

**How it works internally:**
Spring treats `@Configuration` classes specially using something called **CGLIB proxying**. When one `@Bean` method calls another, Spring ensures you get the **same singleton instance**, not a new object each time.

---

#### `@EnableWebSecurity`

```java
@EnableWebSecurity
public class SecurityConfig {
```

| | |
|---|---|
| **What it does** | Activates Spring Security's web security support. It imports all the necessary security filters and infrastructure. |
| **Why needed** | This is the "on switch" for Spring Security. It tells Spring to apply the security filter chain to all incoming HTTP requests. |
| **Without it** | Spring Security would still partially work (Spring Boot auto-configures some defaults), but your custom `SecurityFilterChain` bean might not be picked up properly. Always include it to be explicit. |
| **Belongs to** | Spring Security (`org.springframework.security.config.annotation.web.configuration.EnableWebSecurity`) |

**What it does behind the scenes:**
- Registers the `springSecurityFilterChain` servlet filter
- Sets up the security context (where authenticated user info is stored)
- Enables `@Bean` method detection for `SecurityFilterChain`

---

#### `@Bean`

```java
@Bean
public AuthenticationProvider authProvider() { ... }

@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) { ... }
```

| | |
|---|---|
| **What it does** | Tells Spring: *"The object returned by this method should be managed by Spring's container. Register it as a bean that can be injected elsewhere."* |
| **Why needed** | Spring Security looks for beans of type `AuthenticationProvider` and `SecurityFilterChain` in the container. If they don't exist as beans, Spring uses defaults instead of your custom configuration. |
| **Without it** | The methods would exist but Spring would never call them. Your custom auth provider and security rules would be completely ignored. |
| **Belongs to** | Spring Framework Core (`org.springframework.context.annotation.Bean`) |

**Key point:** `@Bean` is used inside `@Configuration` classes. It's for methods that **create and return objects**. It's different from `@Component`/`@Service` which go on the class itself.

```
@Service / @Component  →  put on a CLASS   →  "Spring, create an instance of this class"
@Bean                  →  put on a METHOD  →  "Spring, call this method and store what it returns"
```

---

#### `@Autowired`

```java
@Autowired
private UserDetailsService userDetailsService;
```

| | |
|---|---|
| **What it does** | Tells Spring: *"Find a bean of type `UserDetailsService` in the container and inject (assign) it here automatically."* |
| **Why needed** | You need the `UserDetailsService` instance (which is `MyUserDetailsService`) to pass it to `DaoAuthenticationProvider`. Instead of creating it manually with `new`, Spring finds and injects it. |
| **Without it** | `userDetailsService` would be `null`. When `authProvider()` runs, it would pass `null` to `DaoAuthenticationProvider` → `NullPointerException` at startup. |
| **Belongs to** | Spring Framework Core (`org.springframework.beans.factory.annotation.Autowired`) |

**How Spring knows which class to inject:**
`UserDetailsService` is an **interface**. Spring scans all beans and finds `MyUserDetailsService` which:
1. Is annotated with `@Service` (so it's a bean)
2. Implements `UserDetailsService` (so it matches the type)

→ Spring injects `MyUserDetailsService` into the `userDetailsService` field.

If TWO classes implemented `UserDetailsService`, Spring would throw an error:
`NoUniqueBeanDefinitionException: expected single matching bean but found 2`

---

### 10.2 — MyUserDetailsService.java Annotations

#### `@Service`

```java
@Service
public class MyUserDetailsService implements UserDetailsService {
```

| | |
|---|---|
| **What it does** | Marks this class as a Spring bean — specifically a **service layer** bean. Spring creates an instance of this class at startup and stores it in the container. |
| **Why needed** | Without it, Spring doesn't know this class exists. `SecurityConfig`'s `@Autowired UserDetailsService` would fail because there's no bean to inject. This was the EXACT error you had originally! |
| **Without it** | `Field userDetailsService in SecurityConfig required a bean of type 'UserDetailsService' that could not be found.` — the error we fixed! |
| **Belongs to** | Spring Framework (`org.springframework.stereotype.Service`) |

**`@Service` vs `@Component` vs `@Repository`:**
They ALL do the same thing — register the class as a Spring bean. The difference is purely **semantic** (naming convention):

```
@Component   →  Generic bean (any class)
@Service     →  Business logic layer (services)
@Repository  →  Data access layer (database repos)
@Controller  →  Web layer (HTTP controllers)
```

Spring treats them identically. But using the right one makes your code self-documenting — anyone reading `@Service` knows this class contains business logic.

---

#### `@Override`

```java
@Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
```

| | |
|---|---|
| **What it does** | Tells the compiler: *"This method is overriding a method from a parent class or interface."* |
| **Why needed** | It's a safety check. If you accidentally misspell the method name (e.g., `loadUserByUserName` with capital N), the compiler will catch it and throw an error instead of silently creating a new method. |
| **Without it** | Code still works, but you lose the compile-time safety check. If you mistype the method name, Spring Security would never call your method (it expects exactly `loadUserByUsername`), and you'd get a confusing runtime error. |
| **Belongs to** | Java language itself (`java.lang.Override`) — not a Spring annotation |

---

### 10.3 — User.java Annotations

#### `@Entity`

```java
@Entity
public class User {
```

| | |
|---|---|
| **What it does** | Tells JPA/Hibernate: *"This Java class represents a table in the database. Each instance of this class = one row."* |
| **Why needed** | Without it, JPA doesn't know `User` is a database table. `UserRepo` wouldn't work because JPA can't map queries to this class. |
| **Without it** | Application fails to start: `Not a managed type: class com.haris.SpringEcom.model.User` |
| **Belongs to** | Jakarta Persistence API (`jakarta.persistence.Entity`) |

---

#### `@Table(name = "users")`

```java
@Table(name = "users")
public class User {
```

| | |
|---|---|
| **What it does** | Specifies the **exact table name** in the database. Without it, JPA would use the class name as the table name. |
| **Why needed** | `user` is a **reserved keyword** in PostgreSQL (it refers to the current database user). If you named the table `user`, SQL queries would break. So you name it `users` instead. |
| **Without it** | JPA would try to create/query a table called `user` → PostgreSQL would throw a syntax error because `user` is reserved. |
| **Belongs to** | Jakarta Persistence API (`jakarta.persistence.Table`) |

---

#### `@Id`

```java
@Id
private int id;
```

| | |
|---|---|
| **What it does** | Marks this field as the **primary key** of the table. |
| **Why needed** | Every JPA entity MUST have a primary key. JPA uses it to uniquely identify rows, track changes, and perform operations like `findById()`. |
| **Without it** | Application fails to start: `No identifier specified for entity: User` |
| **Belongs to** | Jakarta Persistence API (`jakarta.persistence.Id`) |

---

#### `@Data` (Lombok)

```java
@Data
public class User {
```

| | |
|---|---|
| **What it does** | Lombok annotation that **auto-generates** at compile time: `getters`, `setters`, `toString()`, `equals()`, `hashCode()`, and a required-args constructor. |
| **Why needed** | Without it, you'd have to manually write `getId()`, `setId()`, `getUsername()`, `setUsername()`, `getPassword()`, `setPassword()`, `toString()`, etc. — boilerplate code that adds nothing. |
| **Without it** | Code won't compile wherever `user.getUsername()`, `user.setPassword()`, etc. are called — because those methods don't exist without Lombok generating them. |
| **Belongs to** | Project Lombok (`lombok.Data`) — a compile-time code generator |

**What `@Data` generates (invisible code):**
```java
// Lombok generates ALL of this behind the scenes:
public int getId() { return id; }
public void setId(int id) { this.id = id; }
public String getUsername() { return username; }
public void setUsername(String username) { this.username = username; }
public String getPassword() { return password; }
public void setPassword(String password) { this.password = password; }
public String toString() { ... }
public boolean equals(Object o) { ... }
public int hashCode() { ... }
```

---

### 10.4 — UserPrincipal.java Annotations

`UserPrincipal` has no Spring annotations — it uses `implements UserDetails` (an interface, not an annotation) and `@Override` on each method. It's not a Spring bean; it's manually created with `new UserPrincipal(user)` inside `MyUserDetailsService`.

---

### 10.5 — UserRepo.java Annotations

#### `@Repository`

```java
@Repository
public interface UserRepo extends JpaRepository<User, Integer> {
```

| | |
|---|---|
| **What it does** | Marks this interface as a **data access bean**. Also enables automatic translation of database exceptions into Spring's `DataAccessException` hierarchy. |
| **Why needed** | Registers this as a Spring bean so it can be `@Autowired` into services. Also, since it extends `JpaRepository`, Spring Data JPA automatically generates the implementation class at runtime. |
| **Without it** | For `JpaRepository` interfaces, Spring Data JPA would actually still auto-detect it in most cases. But `@Repository` makes it explicit and adds the exception translation feature. Best practice to include it. |
| **Belongs to** | Spring Framework (`org.springframework.stereotype.Repository`) |

**The magic of `JpaRepository`:**
You never write `UserRepoImpl`. Spring Data JPA sees the interface, sees it extends `JpaRepository`, and **generates the implementation at runtime** using dynamic proxies. The methods like `save()`, `findById()`, `findAll()`, `delete()` are all auto-implemented.

For `findByUsername(String username)` — Spring reads the method name, parses it as "find + By + Username", and generates:
```sql
SELECT * FROM users WHERE username = ?
```

---

### 10.6 — UserController.java Annotations

#### `@RestController`

```java
@RestController
public class UserController {
```

| | |
|---|---|
| **What it does** | Combines two annotations: `@Controller` + `@ResponseBody`. It means: *"This class handles HTTP requests, and every method's return value should be written directly to the HTTP response body as JSON."* |
| **Why needed** | Without it, Spring doesn't know this class handles web requests. Your `/register` endpoint wouldn't exist. |
| **Without it** | The endpoint wouldn't be registered. Hitting `POST /register` would return 404 Not Found. |
| **Belongs to** | Spring Web (`org.springframework.web.bind.annotation.RestController`) |

**`@RestController` vs `@Controller`:**
```
@Controller     →  Returns VIEW names (HTML templates like Thymeleaf)
@RestController →  Returns DATA directly (JSON/XML) — used for REST APIs
```
`@RestController` = `@Controller` + `@ResponseBody` on every method.

---

#### `@PostMapping("/register")`

```java
@PostMapping("/register")
public User register(@RequestBody User user) {
```

| | |
|---|---|
| **What it does** | Maps HTTP **POST** requests to `/register` to this method. |
| **Why needed** | Without it, Spring doesn't know which method handles which URL. This is the mapping that tells DispatcherServlet: *"When someone sends POST /register, call this method."* |
| **Without it** | The `/register` endpoint doesn't exist → 404 Not Found. |
| **Belongs to** | Spring Web (`org.springframework.web.bind.annotation.PostMapping`) |

**The mapping annotation family:**
```
@GetMapping("/path")     →  handles GET requests
@PostMapping("/path")    →  handles POST requests
@PutMapping("/path")     →  handles PUT requests
@DeleteMapping("/path")  →  handles DELETE requests
@RequestMapping("/path") →  handles ALL methods (or specify: @RequestMapping(method = GET))
```

---

#### `@RequestBody`

```java
public User register(@RequestBody User user) {
```

| | |
|---|---|
| **What it does** | Tells Spring: *"Take the JSON from the HTTP request body and convert it into a `User` Java object."* |
| **Why needed** | Without it, Spring doesn't know where to get the `user` parameter from. The JSON body `{"id":1,"username":"haris","password":"h@123"}` needs to be deserialized into a `User` object. |
| **Without it** | The `user` parameter would be `null` or Spring would look for it in query parameters instead of the request body. |
| **Belongs to** | Spring Web (`org.springframework.web.bind.annotation.RequestBody`) |

**The conversion process (called Deserialization):**
```
JSON string:           →    Java object:
{                             User {
  "id": 1,                     id = 1
  "username": "haris",          username = "haris"
  "password": "h@123"           password = "h@123"
}                             }

Done by Jackson library (included automatically by Spring Boot)
Jackson matches JSON keys to Java field names
```

---

### 10.7 — UserService.java Annotations

Uses `@Service` (same as MyUserDetailsService — registers as a bean) and `@Autowired` on `UserRepo` (same concept — injects the repository bean).

---

### Quick Reference Table — All Annotations at a Glance

| Annotation | Used On | Purpose | What Breaks Without It |
|---|---|---|---|
| `@Configuration` | SecurityConfig class | Declares bean definition class | Security config not loaded |
| `@EnableWebSecurity` | SecurityConfig class | Activates Spring Security | Security filters not applied |
| `@Bean` | Methods in SecurityConfig | Registers returned object as a bean | Custom security rules ignored |
| `@Autowired` | Fields in multiple classes | Auto-injects dependencies | NullPointerException |
| `@Service` | MyUserDetailsService, UserService | Registers class as a Spring bean | "Bean not found" error |
| `@Repository` | UserRepo interface | Registers as data access bean | DB operations may fail |
| `@Entity` | User class | Marks as database table | "Not a managed type" error |
| `@Table` | User class | Sets exact table name in DB | Reserved keyword conflict |
| `@Id` | User.id field | Marks primary key | "No identifier" error |
| `@Data` | User class | Generates getters/setters/etc. | Compile errors everywhere |
| `@RestController` | UserController class | HTTP handler + JSON responses | Endpoints don't exist (404) |
| `@PostMapping` | Controller methods | Maps POST requests to method | Endpoint not registered |
| `@RequestBody` | Method parameters | Converts JSON body → Java object | Parameter is null |
| `@Override` | Interface method implementations | Compile-time safety check | No error, but risky |
