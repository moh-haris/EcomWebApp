# How Spring Boot Routes Requests — Complete Explanation

This document explains how Spring Boot knows **where to send each HTTP request** — how it picks the right controller, the right method, and what role Security plays in this process. All examples use code from this SpringEcom project.

---

## Table of Contents

1. [The Confusion — What You're Really Asking](#1-the-confusion)
2. [Two Layers — The Key Insight](#2-two-layers)
3. [Layer 1: Security Filters (The Security Guard)](#3-layer-1-security-filters)
4. [Layer 2: DispatcherServlet (The Receptionist)](#4-layer-2-dispatcherservlet)
5. [How the Routing Table is Built at Startup](#5-how-the-routing-table-is-built)
6. [How URL Paths Are Constructed](#6-how-url-paths-are-constructed)
7. [How Matching Works — Method + URL](#7-how-matching-works)
8. [Full Request Examples Using Your Code](#8-full-request-examples)
9. [Edge Cases — What Happens When Things Don't Match](#9-edge-cases)
10. [Common Confusions Cleared](#10-common-confusions-cleared)

---

## 1. The Confusion

The confusion is:

> *"When I hit `POST /register`, how does Spring know to go to SecurityConfig first, then to UserController? How does it find the right controller among ProductController, OrderController, and UserController?"*

The answer is: **SecurityConfig and Controllers are two completely independent systems.** They don't talk to each other. They are two separate checkpoints that a request passes through — one after the other.

---

## 2. Two Layers — The Key Insight

Every HTTP request passes through **two layers in order**:

```
HTTP Request (e.g., GET /api/products)
     │
     │  ┌─────────────────────────────────────────────────────────────┐
     │  │  These two layers are INDEPENDENT.                         │
     │  │  Layer 1 doesn't know which controller exists.             │
     │  │  Layer 2 doesn't know or care about security rules.        │
     │  └─────────────────────────────────────────────────────────────┘
     │
     ▼
╔═══════════════════════════════════════════════════╗
║  LAYER 1: Security Filter Chain                   ║
║                                                   ║
║  Defined in: SecurityConfig.java                  ║
║  Question it answers: "Is this person ALLOWED?"   ║
║  How: Checks URL against security rules            ║
║       (permitAll or authenticated)                 ║
║                                                   ║
║  It does NOT know which controller will handle it  ║
╚═══════════════════════════════════════════════════╝
     │
     │  If blocked → 401 Unauthorized (request STOPS here)
     │  If allowed → request continues ↓
     │
     ▼
╔═══════════════════════════════════════════════════╗
║  LAYER 2: DispatcherServlet                       ║
║                                                   ║
║  Built by: Spring Boot automatically              ║
║  Question it answers: "WHERE should this go?"      ║
║  How: Matches HTTP method + URL path to find the   ║
║       exact controller method                      ║
║                                                   ║
║  It does NOT know or care about security           ║
╚═══════════════════════════════════════════════════╝
     │
     │  If no match → 404 Not Found
     │  If method wrong → 405 Method Not Allowed
     │  If match found → executes the controller method
     │
     ▼
  Controller Method executes → Returns Response
```

**Analogy:**

Think of it like entering a **restricted office building**:

- **Layer 1 (Security Guard at the door):** Checks your ID. Doesn't know which office you're visiting. Just decides if you're allowed IN the building.
- **Layer 2 (Receptionist inside):** You're already inside. Now the receptionist looks at where you want to go and directs you to the right office (controller).

The security guard and receptionist are **two different people** doing **two different jobs**. They don't coordinate with each other.

---

## 3. Layer 1: Security Filters (The Security Guard)

This is your `SecurityConfig.java`:

```java
http.csrf(customizer -> customizer.disable())
    .authorizeHttpRequests(request -> request
        .requestMatchers("/register").permitAll()      // Rule 1
        .anyRequest().authenticated())                  // Rule 2
    .httpBasic(Customizer.withDefaults())
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

### How it works:

Security filters are **Servlet Filters** — they run **before** any controller code. Spring Security inserts its filters into the standard Java Servlet filter chain.

When a request arrives, Spring Security checks the rules **in order** (top to bottom):

```
Request: POST /register
  → Rule 1: Does URL match "/register"? YES → permitAll() → ✅ PASS (no auth needed)

Request: GET /api/products
  → Rule 1: Does URL match "/register"? NO
  → Rule 2: anyRequest().authenticated() → needs auth
  → Checks Authorization header → verifies credentials
  → If valid → ✅ PASS
  → If invalid → ❌ 401 Unauthorized (request STOPS, never reaches controller)

Request: DELETE /api/product/5
  → Rule 1: Does URL match "/register"? NO
  → Rule 2: anyRequest().authenticated() → needs auth
  → Same process...
```

### What Security DOES NOT do:

- It does NOT know that `UserController` handles `/register`
- It does NOT know that `ProductController` handles `/api/products`
- It does NOT find or call any controller
- It ONLY looks at the **URL string** and decides: allow or block

---

## 4. Layer 2: DispatcherServlet (The Receptionist)

The `DispatcherServlet` is the **heart of Spring MVC**. It's a single servlet that receives ALL incoming HTTP requests (after they pass through security filters) and routes them to the correct controller method.

You never write `DispatcherServlet` yourself — Spring Boot creates it automatically.

### What it does:

1. **At startup:** Scans all `@RestController` / `@Controller` classes and builds a routing table
2. **At runtime:** For each incoming request, matches the **HTTP method + URL path** against the routing table to find the right method

---

## 5. How the Routing Table is Built at Startup

When your Spring Boot application starts, this happens:

```
App starts → Spring scans all classes in com.haris.SpringEcom

Found @RestController: ProductController
  → Class-level: @RequestMapping("/api")
  → Method-level: @GetMapping("/products")     → registers GET  /api/products
  → Method-level: @GetMapping("/product/{id}") → registers GET  /api/product/{id}
  → Method-level: @PostMapping("/product")     → registers POST /api/product
  → ... (and so on for every method)

Found @RestController: OrderController
  → Class-level: @RequestMapping("/api")
  → Method-level: @PostMapping("/orders/place") → registers POST /api/orders/place
  → Method-level: @GetMapping("/orders")        → registers GET  /api/orders

Found @RestController: UserController
  → Class-level: none
  → Method-level: @PostMapping("/register")     → registers POST /register
```

The final routing table that Spring builds looks like this:

```
┌──────────────┬──────────────────────────┬──────────────────────────────────────────┐
│ HTTP Method  │ URL Pattern              │ Maps To                                  │
├──────────────┼──────────────────────────┼──────────────────────────────────────────┤
│ GET          │ /api/products            │ ProductController.getProducts()           │
│ GET          │ /api/product/{id}        │ ProductController.getProductById()        │
│ GET          │ /api/product/{id}/image  │ ProductController.getProductImage()       │
│ POST         │ /api/product             │ ProductController.addProduct()            │
│ PUT          │ /api/product/{id}        │ ProductController.updateProduct()         │
│ DELETE       │ /api/product/{id}        │ ProductController.deleteProduct()         │
│ GET          │ /api/product/search      │ ProductController.searchProducts()        │
│ POST         │ /api/orders/place        │ OrderController.placeOrder()              │
│ GET          │ /api/orders              │ OrderController.getAllOrders()             │
│ POST         │ /register                │ UserController.register()                 │
└──────────────┴──────────────────────────┴──────────────────────────────────────────┘
```

This table is built **once at startup** and stored in memory. Spring doesn't scan classes on every request — it looks up this pre-built table.

---

## 6. How URL Paths Are Constructed

Spring **combines** class-level and method-level annotations to create the full URL:

### ProductController — Has class-level @RequestMapping

```java
@RestController
@RequestMapping("/api")                    // ← Class level prefix: /api
public class ProductController {

    @GetMapping("/products")               // ← Method level: /products
    // Final URL = /api + /products = /api/products

    @GetMapping("/product/{id}")           // ← Method level: /product/{id}
    // Final URL = /api + /product/{id} = /api/product/{id}

    @PostMapping("/product")               // ← Method level: /product
    // Final URL = /api + /product = /api/product
}
```

### UserController — No class-level mapping

```java
@RestController                            // ← No @RequestMapping on class
public class UserController {

    @PostMapping("/register")              // ← Method level only
    // Final URL = /register (no prefix)
}
```

### The formula:

```
Final URL = Class @RequestMapping + Method @GetMapping/@PostMapping/...

If class has @RequestMapping("/api"):
    /api + /products = /api/products
    /api + /product/{id} = /api/product/{id}

If class has NO @RequestMapping:
    (nothing) + /register = /register
```

---

## 7. How Matching Works — Method + URL

When a request comes in, DispatcherServlet matches on **TWO things**:

### Rule 1: HTTP Method must match

```
Request: GET /api/products
         ^^^
         Must match a @GetMapping, not @PostMapping

GET  → matches @GetMapping
POST → matches @PostMapping
PUT  → matches @PutMapping
DELETE → matches @DeleteMapping
```

### Rule 2: URL path must match

```
Request: GET /api/products
                 ^^^^^^^^^^
                 Must match the URL pattern in the annotation

/api/products → matches @GetMapping("/products") on a class with @RequestMapping("/api")
```

### Both must match together:

```
Request: POST /api/product/5

Looking at the routing table:
  GET    /api/product/{id}  → method doesn't match (GET ≠ POST) ❌
  PUT    /api/product/{id}  → method doesn't match (PUT ≠ POST) ❌
  DELETE /api/product/{id}  → method doesn't match (DELETE ≠ POST) ❌
  POST   /api/product       → URL doesn't match (/product ≠ /product/5) ❌

No match found → 405 Method Not Allowed
(This is exactly the error you saw in Postman earlier!)
```

### Path Variables — The {id} thing

`{id}` is a **path variable** — it matches any value in that position:

```
Pattern:  /api/product/{id}
Matches:  /api/product/1     → id = 1
          /api/product/5     → id = 5
          /api/product/999   → id = 999
          /api/product/abc   → id = "abc" (would fail at type conversion since id is int)

Does NOT match:
          /api/product           → missing the {id} part
          /api/product/5/image   → has extra "/image" part
```

### Priority: Exact match wins over path variable

What if a URL could match two patterns?

```
Request: GET /api/product/search

Could match: /api/product/{id}     where id = "search"
Could match: /api/product/search   exact match

Spring picks the EXACT match → ProductController.searchProducts()
NOT ProductController.getProductById("search")
```

Spring's priority order:
1. **Exact match** (`/api/product/search`) → highest priority
2. **Path variable** (`/api/product/{id}`) → lower priority
3. **Wildcard** (`/api/**`) → lowest priority

---

## 8. Full Request Examples Using Your Code

### Example 1: POST /register (public endpoint)

```
Step 1: Request arrives: POST /register + Body: {"id":1,"username":"haris","password":"h@123"}

Step 2: LAYER 1 — Security Filters
        → Check rules in SecurityConfig:
          → requestMatchers("/register").permitAll() → URL matches! → ALLOWED ✅
        → No authentication needed, pass through

Step 3: LAYER 2 — DispatcherServlet
        → Look up routing table:
          → HTTP Method = POST, URL = /register
          → Scan table... found: POST /register → UserController.register()
        → NOT ProductController (no matching URL)
        → NOT OrderController (no matching URL)

Step 4: UserController.register() executes
        → Receives JSON body as User object
        → Calls UserService.saveUser()
        → Returns saved user

Step 5: Response sent: 200 OK + User JSON
```

### Example 2: GET /api/products (protected endpoint)

```
Step 1: Request arrives: GET /api/products + Header: Authorization: Basic aGFyaXM6...

Step 2: LAYER 1 — Security Filters
        → Check rules:
          → requestMatchers("/register") → "/api/products" ≠ "/register" → NO
          → anyRequest().authenticated() → needs authentication
        → Read Basic Auth header → decode → username="haris", password="h@123"
        → Call DaoAuthenticationProvider → MyUserDetailsService → verify → ✅ PASS

Step 3: LAYER 2 — DispatcherServlet
        → Look up routing table:
          → HTTP Method = GET, URL = /api/products
          → Found: GET /api/products → ProductController.getProducts()
        → NOT UserController (URL doesn't match /register)
        → NOT OrderController (URL doesn't match /api/orders)

Step 4: ProductController.getProducts() executes
        → Calls ProductService.getAllProducts()
        → Returns list of products

Step 5: Response sent: 200 OK + Products JSON
```

### Example 3: POST /api/orders/place (protected endpoint)

```
Step 1: Request arrives: POST /api/orders/place + Basic Auth

Step 2: LAYER 1 — Security Filters
        → "/api/orders/place" ≠ "/register" → needs auth
        → Verify credentials → ✅ PASS

Step 3: LAYER 2 — DispatcherServlet
        → HTTP Method = POST, URL = /api/orders/place
        → Found: POST /api/orders/place → OrderController.placeOrder()
        → NOT ProductController (no method matches this URL)
        → NOT UserController (URL doesn't match)

Step 4: OrderController.placeOrder() executes
```

### Example 4: GET /api/product/5 vs GET /api/product/search

```
Request A: GET /api/product/5
  → DispatcherServlet checks:
    → /api/product/search → "5" ≠ "search" → NO
    → /api/product/{id}   → "5" matches {id} → YES ✅
  → Calls ProductController.getProductById(5)

Request B: GET /api/product/search?keyword=phone
  → DispatcherServlet checks:
    → /api/product/search → exact match → YES ✅ (higher priority!)
    → /api/product/{id}   → also matches, but lower priority
  → Calls ProductController.searchProducts("phone")
```

---

## 9. Edge Cases — What Happens When Things Don't Match

### Case 1: URL exists but wrong HTTP method → 405

```
Request: POST /api/product/2

Routing table has:
  GET    /api/product/{id}  ← exists but method is GET
  PUT    /api/product/{id}  ← exists but method is PUT
  DELETE /api/product/{id}  ← exists but method is DELETE

The URL pattern /api/product/{id} EXISTS, but not for POST.
Spring says: "I know this URL, but POST is not allowed for it"
→ 405 Method Not Allowed
```

### Case 2: URL doesn't exist at all → 404

```
Request: GET /api/customers

Routing table has nothing matching /api/customers.
Spring says: "I have no idea what this URL is"
→ 404 Not Found
```

### Case 3: Security blocks before reaching controller → 401

```
Request: GET /api/products (WITHOUT Basic Auth header)

Layer 1 (Security):
  → anyRequest().authenticated() → no credentials found → BLOCKED ❌
  → 401 Unauthorized

Layer 2 (DispatcherServlet): NEVER REACHED
  → The controller method never even runs
```

### Case 4: Duplicate mapping → App fails to start

If two controllers both map `GET /api/products`:
```java
// In ProductController
@GetMapping("/products")    // → GET /api/products

// In SomeOtherController
@GetMapping("/api/products")  // → GET /api/products (same!)
```
Spring Boot will **refuse to start** and throw an error:
`Ambiguous mapping. Cannot map 'someOtherController' method to {GET /api/products}: There is already 'productController' mapped.`

---

## 10. Common Confusions Cleared

### Confusion 1: "SecurityConfig routes requests to controllers"

**Wrong.** SecurityConfig is a **gatekeeper** — it only allows or blocks. It never routes or redirects to any controller. Even if you remove SecurityConfig entirely, requests would still reach the correct controller because DispatcherServlet handles routing independently.

### Confusion 2: "Spring searches through controllers one by one at runtime"

**Wrong.** Spring builds the routing table **once at startup** and keeps it in memory as a HashMap-like structure. At runtime, it does a direct lookup — not a sequential search through files. It's extremely fast.

### Confusion 3: "The order of controllers matters"

**Wrong.** It doesn't matter if `UserController` is defined before or after `ProductController`. Spring scans ALL of them, builds the routing table, and the order of scanning doesn't affect matching. What matters is the URL pattern and HTTP method.

### Confusion 4: "@RequestMapping on the class is required"

**Wrong.** It's optional. `UserController` has no class-level `@RequestMapping` — the method-level `@PostMapping("/register")` works fine on its own. The class-level annotation is just a convenient prefix when all methods share a common base path like `/api`.

### Confusion 5: "Security permitAll() means Spring skips Security entirely"

**Wrong.** The security filter chain STILL runs for `/register`. Spring Security still processes the request through its filter chain. `permitAll()` just means the authorization check says "yes, everyone is allowed" — but the filter still executes. If there were other security filters (like logging), they would still run.

---

## Visual Summary

```
                    YOUR SPRING BOOT APPLICATION
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   Incoming Request: GET /api/product/5 + Basic Auth         │
│        │                                                    │
│        ▼                                                    │
│   ┌─────────────────────────────────────────┐               │
│   │  LAYER 1: Security Filter Chain          │               │
│   │                                          │               │
│   │  SecurityConfig rules:                   │               │
│   │  → "/register" → permitAll              │               │
│   │  → everything else → authenticated      │               │
│   │                                          │               │
│   │  Checks credentials → ✅ Valid           │               │
│   │                                          │               │
│   │  ⚠️ Knows NOTHING about controllers      │               │
│   └──────────────────┬──────────────────────┘               │
│                      │                                      │
│                      ▼                                      │
│   ┌─────────────────────────────────────────┐               │
│   │  LAYER 2: DispatcherServlet              │               │
│   │                                          │               │
│   │  Routing Table (built at startup):       │               │
│   │                                          │               │
│   │  GET /api/products      → ProductCtrl    │               │
│   │  GET /api/product/{id}  → ProductCtrl  ◄─── MATCH!      │
│   │  POST /api/product      → ProductCtrl    │               │
│   │  POST /api/orders/place → OrderCtrl      │               │
│   │  GET /api/orders        → OrderCtrl      │               │
│   │  POST /register         → UserCtrl       │               │
│   │                                          │               │
│   │  ⚠️ Knows NOTHING about security         │               │
│   └──────────────────┬──────────────────────┘               │
│                      │                                      │
│                      ▼                                      │
│   ┌─────────────────────────────────────────┐               │
│   │  ProductController.getProductById(5)     │               │
│   │  → calls ProductService                  │               │
│   │  → returns Product data                  │               │
│   └─────────────────────────────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
