# 🎯 SpringEcom — Interview Questions (Current Project Version)

> These questions are based on YOUR exact codebase as it is right now.  
> Interviewers will open your GitHub, read your code, and ask these.

---

## 🔴 HIGH PRIORITY — Prepare These FIRST (Asked in almost every interview)

---

### Q1. "Walk me through what happens end-to-end when a user places an order."
**What they're testing:** Can you trace the full flow?

**Expected Answer:**
1. Client sends `POST /api/orders/place` with JSON body (`OrderRequest`)
2. `OrderController.placeOrder()` receives it, deserializes JSON → `OrderRequest` record
3. Calls `OrderService.placeOrder()` which is `@Transactional`
4. Creates `Order` entity, generates unique orderId using `UUID`
5. Loops through each `OrderItemRequest` → fetches `Product` from DB → decrements stock → builds `OrderItem` using Builder pattern
6. Saves `Order` (cascade saves all `OrderItem`s too because of `CascadeType.ALL`)
7. Maps entity to `OrderResponse` DTO and returns
8. Controller wraps in `ResponseEntity` with `HttpStatus.CREATED` (201)

**Follow-up they'll ask:** *"What if the DB goes down after stock is decremented but before order is saved?"*  
→ `@Transactional` ensures rollback. Both stock decrement and order save are in one transaction.

---

### Q2. "Two users try to order the last item at the same time. What happens?"
**What they're testing:** Concurrency awareness

**Expected Answer (be honest about the bug):**
> "Currently this is a race condition in my code. Both threads read `stockQuantity = 1`, both subtract 1, both save `stock = 0`. One order will succeed, the other will also succeed — but we've oversold. The stock could even go negative since there's no check for `stock >= quantity` before decrementing.  
> To fix this, I would add `@Version` for optimistic locking on the Product entity, or use a pessimistic lock with `SELECT FOR UPDATE`."

**The code with the issue:**
```java
// OrderService.java line 47
product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());
productRepo.save(product);
```

> **Tip:** Interviewers LOVE when you honestly identify bugs in your own code. It shows maturity. Don't hide it.

---

### Q3. "Why did you use DTOs (records) for Orders but expose the Product entity directly?"
**What they're testing:** Design consistency, security awareness

**Expected Answer:**
> "For Orders, I created `OrderRequest`/`OrderResponse` records to separate the API contract from the database entity — clients don't need internal fields like `id` or the `Order` back-reference. For Products, I should have done the same but didn't yet. The current issue is that `ProductController` returns the raw `Product` entity including `byte[] imageData`, which means every product listing sends the full image binary in JSON — that's both a performance and security problem."

---

### Q4. "What is `@Transactional` doing in your `placeOrder()` method? What if you remove it?"
**What they're testing:** Transaction management understanding

**Expected Answer:**
- `@Transactional` creates a **proxy** around `OrderService` (AOP-based)
- All DB operations inside `placeOrder()` run in a **single transaction**
- If ANY exception occurs (e.g., product not found for the 3rd item), ALL changes roll back — including stock decrements for items 1 and 2
- Without it: each `productRepo.save()` and `orderRepo.save()` would be in its own transaction. If it fails mid-way, stock is decremented but order isn't saved = **data inconsistency**

**Follow-up:** *"Does `@Transactional` on `getAllOrderResponses()` make sense? It's a read-only method."*  
→ It works, but should be `@Transactional(readOnly = true)` for optimization. Tells Hibernate to skip dirty-checking.

---

### Q5. "Explain the `@OneToMany` and `@ManyToOne` relationships in your project."
**What they're testing:** JPA relationship understanding

**Expected Answer:**
```
Order (1) ←→ (Many) OrderItem ←→ (Many to 1) Product
```
- `Order` has `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL)` → Order owns the relationship from the parent side, but `mappedBy` says the **foreign key is in OrderItem table**
- `OrderItem` has `@ManyToOne` on both `order` and `product` → OrderItem table has `order_id` FK and `product_id` FK
- `CascadeType.ALL` means saving Order auto-saves all its OrderItems
- `FetchType.LAZY` on `OrderItem.order` means the Order is NOT loaded when you fetch an OrderItem — only loaded when accessed

**Follow-up:** *"What is the owning side of the relationship?"*  
→ `OrderItem` is the owning side (it has the FK). `mappedBy = "order"` on the `Order` side confirms this.

---

### Q6. "What is the N+1 problem? Does your code have it?"
**What they're testing:** Performance awareness

**Expected Answer:**
> "Yes, my `getAllOrderResponses()` has it. It does:
> 1. **1 query** to fetch all Orders
> 2. **N queries** — one per order to fetch `order.getOrderItems()` (lazy loading triggers)
> 3. Then for each OrderItem, another query to fetch `item.getProduct().getName()`
>
> So for 100 orders with 3 items each, that's 1 + 100 + 300 = **401 queries** instead of 1-2.
>
> Fix: Use `@Query("SELECT o FROM orders o JOIN FETCH o.orderItems oi JOIN FETCH oi.product")` or `@EntityGraph`."

---

### Q7. "Why `JpaRepository` interface? Why not a class? How does it work without implementation?"
**What they're testing:** Spring Data JPA + Proxy pattern understanding

**Expected Answer:**
- `JpaRepository` is an interface. I never write an implementation class.
- At runtime, **Spring Data JPA creates a proxy** (dynamic proxy using `java.lang.reflect.Proxy`) that implements all the methods.
- For standard methods like `findAll()`, `save()`, `findById()` — the proxy generates SQL automatically based on method name and entity metadata
- For custom queries like my `searchProducts()` — the `@Query` annotation provides the JPQL, and the proxy executes it
- `findByOrderId(String orderId)` in `OrderRepo` — Spring parses the method name: `findBy` + `OrderId` → generates `SELECT * FROM orders WHERE order_id = ?`

---

### Q8. "What are Java Records? Why did you use them for DTOs?"
**What they're testing:** Modern Java knowledge

**Expected Answer:**
- Records (Java 16+) are **immutable data carriers** — perfect for DTOs
- `public record OrderRequest(String customerName, String email, List<OrderItemRequest> items)` auto-generates: constructor, getters (`customerName()` not `getCustomerName()`), `equals()`, `hashCode()`, `toString()`
- They're immutable — fields are `private final`, no setters
- Why for DTOs: DTOs should be immutable (request comes in, shouldn't be modified), less boilerplate than a class with Lombok

**Follow-up:** *"Why did you use `@Data` (Lombok) for entities but Records for DTOs?"*  
→ JPA entities need a no-arg constructor and setters (Hibernate creates entities via reflection, sets fields). Records don't have either — so they can't be JPA entities.

---

### Q9. "Explain `CascadeType.ALL`. What happens if you delete an Order?"
**What they're testing:** Cascade and data integrity understanding

**Expected Answer:**
- `CascadeType.ALL` = PERSIST + MERGE + REMOVE + REFRESH + DETACH
- If I save an Order → all OrderItems are also saved (CASCADE PERSIST)
- If I delete an Order → all OrderItems are also deleted (CASCADE REMOVE)
- But Products are NOT deleted — there's no cascade from OrderItem to Product
- **Danger:** `CascadeType.ALL` on `@OneToMany` can be risky — if you accidentally remove an Order, you lose all order history items. In production, you'd typically use soft deletes instead.

---

### Q10. "Why `@CrossOrigin` on your controllers? Is it secure?"
**What they're testing:** Web security awareness

**Expected Answer:**
- `@CrossOrigin` without parameters allows requests from **ANY origin** — this is insecure in production
- It adds `Access-Control-Allow-Origin: *` header to responses
- In production, should restrict to specific frontend domain: `@CrossOrigin(origins = "https://mystore.com")`
- Better approach: configure CORS globally in a `WebMvcConfigurer` or through Spring Security's `CorsConfigurationSource`

---

### Q11. "What is `@Autowired`? What happens internally when Spring sees it?"
**What they're testing:** IoC/DI understanding

**Expected Answer:**
1. At startup, Spring scans packages for `@Component`, `@Service`, `@Repository`, `@Controller` classes
2. Creates singleton instances (beans) and stores them in `ApplicationContext`
3. When it finds `@Autowired` on a field, it looks in the context for a bean of that type
4. If found → injects it. If multiple found → error (unless `@Qualifier` used). If none → error.
5. This is **Inversion of Control** — I don't create `ProductService` with `new`, Spring manages its lifecycle

**Follow-up:** *"Why is field injection (`@Autowired`) considered bad practice?"*  
→ Can't make fields `final` (not immutable), harder to unit test (can't pass mocks via constructor), hides dependencies, allows circular dependencies silently.

---

### Q12. "What is `GenerationType.IDENTITY`? Why not `SEQUENCE`?"
**What they're testing:** Database + PK generation knowledge

**Expected Answer:**
- `IDENTITY` = uses the database's auto-increment feature (`SERIAL` in PostgreSQL)
- `SEQUENCE` = uses a database sequence object, Spring can pre-allocate IDs in batches
- For PostgreSQL, `SEQUENCE` is actually **better** — it supports batch inserts (Hibernate can allocate 50 IDs at once). `IDENTITY` forces one-by-one inserts because it needs to wait for the DB to assign the ID
- I used `IDENTITY` for simplicity, but `SEQUENCE` would be the better choice for performance

---

### Q13. "What is `@Lob` on `imageData`? Is storing images in DB a good idea?"
**What they're testing:** System design thinking

**Expected Answer:**
- `@Lob` = Large Object. Maps to `BYTEA` type in PostgreSQL
- Stores the actual binary image data in the database row
- **This is NOT ideal for production** because:
  - DB size grows rapidly
  - Every `SELECT * FROM product` fetches the image bytes (even if you don't need the image)
  - Can't use CDN for caching images
- **Better approach:** Store images in object storage (S3, GCS) or filesystem, save only the URL/path in the database
- But for a learning project, it demonstrates handling binary data in JPA

---

### Q14. "What is the `Builder` pattern? Why did you use it for `OrderItem` but not `Order`?"
**What they're testing:** Design pattern knowledge

**Expected Answer:**
- Builder creates objects step-by-step, useful when you have many constructor parameters
- `OrderItem.builder().product(p).quantity(q).totalPrice(tp).order(o).build()` — clear, readable, order of params doesn't matter
- `Order` is built using setters (`order.setOrderId(...)`) because I construct it incrementally (orderItems aren't ready yet when Order is first created)
- I could have used Builder for Order too — but the sequential nature of setting fields + adding items later made setters more natural here

---

### Q15. "Your `searchProducts` query — is it vulnerable to SQL injection?"
**What they're testing:** Security awareness

**Expected Answer:**
- No, it's safe. JPQL with `:keyword` parameter binding uses **prepared statements** under the hood
- The `keyword` value is passed as a parameter, never concatenated into the query string
- If I had used string concatenation like `"WHERE name LIKE '%" + keyword + "%'"` — that would be SQL injection
- Spring Data JPA always uses parameterized queries, which is the primary defense against SQL injection

---

### Q16. "What is `ResponseEntity`? Why not just return the object directly?"
**What they're testing:** REST API understanding

**Expected Answer:**
- `ResponseEntity<T>` wraps the response body + HTTP status code + headers
- If I just return `Product`, Spring defaults to `200 OK` always — I can't send `201 CREATED` for POST or `404 NOT FOUND`
- Gives full control: `new ResponseEntity<>(product, HttpStatus.CREATED)` returns body + 201
- Alternative: Use `@ResponseStatus(HttpStatus.CREATED)` on the method — but less flexible

---

### Q17. "What is the difference between `@RequestBody` vs `@RequestPart`? You use both."
**What they're testing:** Multipart handling knowledge

**Expected Answer:**
- `@RequestBody` → expects the ENTIRE request body as JSON. Used in `OrderController` for `OrderRequest`
- `@RequestPart` → used in **multipart/form-data** requests where the body has multiple parts. Used in `ProductController` because product creation sends both JSON data AND an image file
- In `addProduct()`: `@RequestPart Product product` reads the JSON part, `@RequestPart MultipartFile imageFile` reads the file part
- You can't use `@RequestBody` when uploading files — it only handles a single body payload

---

### Q18. "What does `@SpringBootApplication` do?"
**What they're testing:** Spring Boot internals

**Expected Answer:**
It's a shortcut for 3 annotations:
1. `@Configuration` — marks this class as a source of bean definitions
2. `@EnableAutoConfiguration` — Spring Boot auto-configures beans based on classpath (sees PostgreSQL driver → configures DataSource, sees spring-data-jpa → configures EntityManager)
3. `@ComponentScan` — scans the current package and sub-packages for `@Component`, `@Service`, `@Repository`, `@Controller`

That's why all my classes are under `com.haris.SpringEcom.*` — they're within the scan scope.

---

### Q19. "What are SOLID principles? Show me examples from your project."
**What they're testing:** OOP design principles

| Principle | Your Project Example |
|---|---|
| **S** — Single Responsibility | `ProductService` handles product logic only, `OrderService` handles order logic only. Not mixed. |
| **O** — Open/Closed | `JpaRepository` is open for extension (I add `searchProducts` custom query) but I don't modify its source code |
| **L** — Liskov Substitution | `ProductRepo` and `OrderRepo` both extend `JpaRepository` — anywhere a `JpaRepository` is expected, my repos work |
| **I** — Interface Segregation | `JpaRepository` extends `CrudRepository` + `PagingAndSortingRepository` — I get only what I need |
| **D** — Dependency Inversion | `ProductController` depends on `ProductService` (abstraction managed by Spring), not on `ProductRepo` directly |

---

### Q20. "What happens if `productRepo.findById()` returns empty in your `placeOrder`?"
**What they're testing:** Error handling awareness

**Expected Answer:**
- Currently: `.orElseThrow(() -> new RuntimeException("Product not found"))` — throws a raw `RuntimeException`
- Since `placeOrder()` is `@Transactional`, this exception triggers rollback of the entire transaction (stock decrements for previous items are rolled back too)
- **Problem:** `RuntimeException` returns generic `500 Internal Server Error` to the client with no useful message
- **Better:** Use a custom `ResourceNotFoundException` extending `RuntimeException`, catch it in a `@ControllerAdvice`, return `404` with a proper error body

---

## 🟠 MEDIUM PRIORITY — Prepare After High Priority

---

### Q21. "What is Lazy vs Eager loading? You have `FetchType.LAZY` on `OrderItem.order`. Why?"
**Expected Answer:**
- EAGER = load the related entity immediately when the parent is fetched (extra JOIN or query)
- LAZY = load only when you access the field (creates a proxy, query fires on first access)
- `OrderItem.order` is LAZY because: when listing order items, I usually already have the order context — no need to re-fetch it
- Default: `@ManyToOne` is EAGER by default, `@OneToMany` is LAZY by default
- LAZY can cause `LazyInitializationException` if accessed outside a transaction/session

---

### Q22. "What is `UUID.randomUUID().substring(0,8)` for orderId? Is it guaranteed unique?"
**Expected Answer:**
- UUID v4 generates 128-bit random IDs. I take the first 8 chars of the hex string = 32 bits = ~4 billion combinations
- **Not guaranteed unique** — collision probability is low but non-zero with only 8 chars
- I have `@Column(unique = true)` on `orderId` — so DB will reject duplicates, but I don't handle that exception
- Better: Use the full UUID, or use a database sequence-based ID

---

### Q23. "Your `getProductById` returns `new Product(-1)` when not found. What's wrong with this?"
**Expected Answer:**
- This is a **sentinel value anti-pattern** — caller must know to check `id > 0`, which is fragile
- If caller forgets the check, they work with a fake product with id=-1
- Better approaches: throw `ResourceNotFoundException`, return `Optional<Product>`, or return `ResponseEntity.notFound()`
- In controller, I check `product.getId() > 0` — but what if product id is actually 0 (auto-generated)? Edge case.

---

### Q24. "What is `mappedBy` in `@OneToMany`? What if you remove it?"
**Expected Answer:**
- `mappedBy = "order"` tells JPA: "the `order` field in `OrderItem` is the FK owner — don't create a join table"
- Without `mappedBy`: JPA creates a **join table** `order_order_items(order_id, order_item_id)` — extra table, extra joins, worse performance
- The value `"order"` refers to the field name in `OrderItem` class, NOT the column name

---

### Q25. "What SQL does your `searchProducts` JPQL generate?"
**Expected Answer:**
```sql
SELECT p.* FROM product p 
WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', ?, '%'))
   OR LOWER(p.description) LIKE LOWER(CONCAT('%', ?, '%'))
   OR LOWER(p.brand) LIKE LOWER(CONCAT('%', ?, '%'))
   OR LOWER(p.category) LIKE LOWER(CONCAT('%', ?, '%'))
```
- Uses `LIKE` with wildcards — does a **full table scan**, no index used
- For production: use full-text search (PostgreSQL `tsvector`/`tsquery`) or Elasticsearch
- This query won't scale with millions of products

---

### Q26. "What is Lombok `@Data`? What does it generate?"
**Expected Answer:**
- `@Data` = `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`
- Generates all getters/setters, `toString()`, `equals()` and `hashCode()`
- **Problem with JPA entities:** Lombok `@EqualsAndHashCode` uses all fields by default. For entities, `equals`/`hashCode` should use only the `@Id` field — otherwise two unsaved entities (both id=0) are considered "equal"
- Better: Use `@Getter @Setter @NoArgsConstructor` separately and write `equals`/`hashCode` manually on `id`

---

### Q27. "How does `@RestController` differ from `@Controller`?"
**Expected Answer:**
- `@RestController` = `@Controller` + `@ResponseBody` on every method
- `@Controller` → methods return **view names** (HTML templates via Thymeleaf, etc.)
- `@RestController` → methods return **objects** that are serialized to JSON/XML via Jackson
- My project is a REST API (no server-side HTML), so `@RestController` is correct

---

### Q28. "What is `BigDecimal`? Why not `double` for price?"
**Expected Answer:**
- `double` has floating-point precision issues: `0.1 + 0.2 = 0.30000000000000004`
- For **money**, you MUST use `BigDecimal` — exact decimal arithmetic, no rounding errors
- `product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity()))` — this is safe
- In DB, this maps to `NUMERIC`/`DECIMAL` type in PostgreSQL — exact precision

---

### Q29. "How does Jackson serialize your entities to JSON?"
**Expected Answer:**
- Spring Boot auto-configures `Jackson` (via `spring-boot-starter-webmvc`)
- When a controller returns an object, `MappingJackson2HttpMessageConverter` converts it to JSON
- It uses getters by default — `getName()` becomes `"name"` in JSON
- `@JsonFormat(shape = Shape.STRING, pattern = "dd-MM-yyyy")` on `releaseDate` tells Jackson to format the date as a string in that pattern
- `byte[] imageData` gets Base64-encoded in JSON — which is why the response is huge

---

### Q30. "Your `addOrUpdateProduct` handles both create and update. Is that good design?"
**Expected Answer:**
- It follows the **upsert** pattern — check if image is new, if not keep old image data
- The method has two responsibilities (create + update) which slightly violates SRP
- The `if (product.getId() > 0)` check to decide "is this an update?" is fragile
- Better: Split into `createProduct(Product, MultipartFile)` and `updateProduct(int id, Product, MultipartFile)` — clearer intent, different validation rules

---

### Q31. "What is `@RequestParam` vs `@PathVariable`?"
**Expected Answer:**
- `@PathVariable` → extracts from URL path: `GET /product/5` → `@PathVariable int id` = 5
- `@RequestParam` → extracts from query string: `GET /product/search?keyword=phone` → `@RequestParam String keyword` = "phone"
- Use `@PathVariable` for resource identification (which product?), `@RequestParam` for filtering/search/pagination

---

### Q32. "What database are you using? Why PostgreSQL?"
**Expected Answer:**
- PostgreSQL — open-source, ACID-compliant, supports advanced features (JSONB, full-text search, arrays)
- Better for production than H2/MySQL for: complex queries, concurrent access, data integrity
- Connected via `spring-boot-starter-data-jpa` + `postgresql` driver
- JPA/Hibernate abstracts the database — I could switch to MySQL by just changing the driver and connection URL

---

### Q33. "What is `@Entity(name = "orders")` on your Order class? Why not just `@Entity`?"
**Expected Answer:**
- `ORDER` is a **reserved keyword** in SQL (`ORDER BY`)
- If I use `@Entity` without `name`, Hibernate creates a table called `order` — which clashes with the reserved keyword
- `@Entity(name = "orders")` maps it to a table named `orders` instead
- Alternative: use `@Table(name = "orders")` which is more explicit for table naming

---

### Q34. "How would you add a payment feature to this project?"
**Expected Answer:**
- Use **Strategy Pattern**: Create a `PaymentStrategy` interface with `pay(Order order, BigDecimal amount)` method
- Implementations: `CreditCardPayment`, `UPIPayment`, `WalletPayment`
- `OrderService` accepts a `PaymentStrategy` and calls `strategy.pay()` during order placement
- This is Open/Closed principle — add new payment methods without modifying existing code
- Would also need a `Payment` entity to track payment status separately from order status

---

### Q35. "If this project gets 10,000 concurrent users, what breaks first?"
**Expected Answer:**
1. **Stock race condition** — overselling (no locking)
2. **N+1 queries** in `getAllOrderResponses()` — DB connection pool exhaustion
3. **No pagination** — `getAllProducts()` loads entire table into memory → OOM
4. **Images in DB** — huge response payloads, slow serialization
5. **No caching** — same product data fetched repeatedly from DB
6. **No connection pooling config** — default HikariCP pool size (10) will bottleneck

---

## 🔵 LOW PRIORITY — Bonus Knowledge (Nice to know)

---

### Q36. "What is the Spring Bean lifecycle?"
**Expected Answer:**
1. Bean instantiation (constructor)
2. Dependency injection (field/setter/constructor)
3. `@PostConstruct` method (if any)
4. Bean is ready to use
5. `@PreDestroy` method (on shutdown)
6. Bean destroyed

---

### Q37. "What is AOP? Where is it used in your project?"
**Expected Answer:**
- Aspect-Oriented Programming — cross-cutting concerns handled separately
- In my project: `@Transactional` is AOP — Spring creates a **proxy** around my service, intercepts method calls, starts transaction before, commits/rollbacks after
- I don't write AOP code directly, but Spring uses it behind the scenes

---

### Q38. "What is `Optional` in Java? You use `orElse` and `orElseThrow`."
**Expected Answer:**
- `Optional<T>` is a container that may or may not contain a value — avoids `NullPointerException`
- `findById()` returns `Optional<Product>` — forces me to handle the "not found" case
- `orElse(new Product(-1))` → returns fallback if empty (my current approach — not ideal)
- `orElseThrow(() -> new RuntimeException(...))` → throws if empty (used in OrderService)
- Better than returning `null` because it makes the "might be absent" contract explicit

---

### Q39. "What HTTP methods are idempotent? Are your APIs idempotent?"
**Expected Answer:**
- **Idempotent** = calling N times has the same effect as calling once
- GET, PUT, DELETE are idempotent. POST is NOT.
- My `DELETE /product/{id}` — idempotent? Sort of. Second call returns 404 but no side effect.
- My `POST /orders/place` — NOT idempotent. Calling twice creates two orders. Would need an idempotency key to fix.

---

### Q40. "What is `spring-boot-starter-webmvc-test`? Do you have tests?"
**Expected Answer:**
- It's a testing dependency for writing integration tests for controllers using `MockMvc`
- Currently I don't have any tests written (be honest)
- To test `ProductController`, I'd use `@WebMvcTest(ProductController.class)` + `@MockBean ProductService` + `MockMvc` to simulate HTTP requests
- Unit tests for services would use `@ExtendWith(MockitoExtension.class)` with mocked repos

---

## 📊 Quick Revision Checklist

| # | Question Topic | Priority | 30-sec answer ready? |
|---|---|---|---|
| Q1 | End-to-end order flow | 🔴 | ☐ |
| Q2 | Race condition on stock | 🔴 | ☐ |
| Q3 | DTOs for Orders, not Products — why? | 🔴 | ☐ |
| Q4 | @Transactional explained | 🔴 | ☐ |
| Q5 | OneToMany/ManyToOne relationships | 🔴 | ☐ |
| Q6 | N+1 problem | 🔴 | ☐ |
| Q7 | JpaRepository — how does it work without impl? | 🔴 | ☐ |
| Q8 | Java Records for DTOs | 🔴 | ☐ |
| Q9 | CascadeType.ALL implications | 🔴 | ☐ |
| Q10 | @CrossOrigin security | 🔴 | ☐ |
| Q11 | @Autowired / DI internals | 🔴 | ☐ |
| Q12 | GenerationType.IDENTITY vs SEQUENCE | 🔴 | ☐ |
| Q13 | @Lob — images in DB good idea? | 🔴 | ☐ |
| Q14 | Builder pattern — why for OrderItem? | 🔴 | ☐ |
| Q15 | SQL injection safe? | 🔴 | ☐ |
| Q16 | ResponseEntity — why? | 🔴 | ☐ |
| Q17 | @RequestBody vs @RequestPart | 🔴 | ☐ |
| Q18 | @SpringBootApplication internals | 🔴 | ☐ |
| Q19 | SOLID from your project | 🔴 | ☐ |
| Q20 | Error handling — what if product not found? | 🔴 | ☐ |
| Q21 | Lazy vs Eager loading | 🟠 | ☐ |
| Q22 | UUID collision risk | 🟠 | ☐ |
| Q23 | Sentinel value anti-pattern | 🟠 | ☐ |
| Q24 | mappedBy explained | 🟠 | ☐ |
| Q25 | Search query — scalability | 🟠 | ☐ |
| Q26 | Lombok @Data problems with JPA | 🟠 | ☐ |
| Q27 | @RestController vs @Controller | 🟠 | ☐ |
| Q28 | BigDecimal vs double for money | 🟠 | ☐ |
| Q29 | Jackson serialization | 🟠 | ☐ |
| Q30 | addOrUpdateProduct — SRP violation? | 🟠 | ☐ |
| Q31 | @RequestParam vs @PathVariable | 🟠 | ☐ |
| Q32 | Why PostgreSQL? | 🟠 | ☐ |
| Q33 | @Entity(name = "orders") — reserved keyword | 🟠 | ☐ |
| Q34 | Add payment — Strategy Pattern | 🟠 | ☐ |
| Q35 | What breaks at 10K users? | 🟠 | ☐ |
| Q36 | Bean lifecycle | 🔵 | ☐ |
| Q37 | AOP in your project | 🔵 | ☐ |
| Q38 | Optional explained | 🔵 | ☐ |
| Q39 | Idempotency of your APIs | 🔵 | ☐ |
| Q40 | Testing — do you have tests? | 🔵 | ☐ |
