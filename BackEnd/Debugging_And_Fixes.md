# Spring Boot E-Commerce Project: Debugging & Fixes Log

This document records the problems encountered, their explanations, and solutions applied during development for future reference and revision.

---

## 1. Unique Constraint Violation on OrderItem
**Error Message:** 
`org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "order_item_pkey" Detail: Key (id)=(0) already exists.`

**Why it Occurred:**
The `OrderItem` entity had an `@Id` field but lacked the `@GeneratedValue` annotation. Because `id` is a primitive `int`, its default value is `0`. When saving multiple order items, Hibernate repeatedly tried to save them with `id = 0`. The database rejected the second attempt because a row with `id = 0` already existed.

**The Solution:**
Add the `@GeneratedValue(strategy = GenerationType.IDENTITY)` annotation to tell Hibernate that the database should auto-generate unique IDs.

```java
@Entity
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    // ...
}
```

---

## 2. Null Value in Column ID Violates Not-Null Constraint
**Error Message:**
`ERROR: null value in column "id" of relation "order_item" violates not-null constraint Detail: Failing row contains (null, 1, 999.99, 6, 1).`

**Why it Occurred:**
After fixing Problem 1, Hibernate stopped sending an ID (leaving it `null`) because it expected the database to generate it automatically. However, because the table was initially created *before* we added the `@GeneratedValue` annotation (using `spring.jpa.hibernate.ddl-auto=update`), the database column was a regular integer, not an auto-incrementing `serial`/`identity` column. The database didn't know how to generate an ID, resulting in a `null` value.

**The Solution:**
This is a one-time schema synchronization issue.
1. Drop the table manually in PostgreSQL (`DROP TABLE order_item CASCADE;`).
2. Alternatively, temporarily change `spring.jpa.hibernate.ddl-auto=create` in `application.properties`, run the app once, and then switch it back to `update`.

This forces Hibernate to recreate the table with the correct auto-incrementing column configuration.

---

## 3. Product Updates Failing When No Image is Uploaded
**The Problem:**
When trying to update a product's details (e.g., ticking the "Product Available" checkbox) without uploading a new image, the product wouldn't update. Instead, it might create a duplicate product or crash.

**Why it Occurred:**
There were two separate issues in the controller and service layer:

**Issue A: Missing ID Assignment**
Many frontends send the item ID in the URL (`PUT /product/1`), but omit it from the JSON payload body. When Spring parses the JSON into a `Product` object, `product.getId()` defaults to `0`. If you save an entity with `id = 0`, Hibernate assumes it's brand new and tries to *create* a duplicate instead of updating the existing one.

*Fix:* Explicitly inject the URL's path variable `id` into the `Product` object before saving.

**Issue B: Strict MultipartFile Requirements and Image Wiping**
If you don't select a new image, the frontend might not send the `imageFile` part. By default, `@RequestPart` is strictly required, causing a 400 Bad Request error. If you bypass that by making it optional, the `MultipartFile` becomes `null`. Without proper checks, trying to call `.getOriginalFilename()` on a null file causes a `NullPointerException`. Even worse, if you save the product without restoring the old image, the database overwrites the existing image with nulls!

*Fix:* Make the image optional in the controller, add null checks in the service, and fetch/preserve the existing image if a new one isn't provided.

**The Solution:**
*Controller:*
```java
@PutMapping("/product/{id}")
public ResponseEntity<String> updateProduct(
        @PathVariable int id, 
        @RequestPart Product product, 
        // 1. Make the image optional
        @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
    try {
        // 2. Explicitly assign the ID from the URL to prevent duplicates
        product.setId(id);
        Product updatedProduct = productService.addOrUpdateProduct(product, imageFile);
        return new ResponseEntity<>("Updated", HttpStatus.OK);
    } catch (IOException e) {
        return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

*Service:*
```java
public Product addOrUpdateProduct(Product product, MultipartFile image) throws IOException {
    // 3. Null check to prevent NullPointerException
    if (image != null && !image.isEmpty()) {
        product.setImageName(image.getOriginalFilename());
        product.setImageType(image.getContentType());
        product.setImageData(image.getBytes());
    } else if (product.getId() > 0) {
        // 4. Preserve existing image if no new image is provided
        Product existingProduct = productRepo.findById(product.getId()).orElse(null);
        if (existingProduct != null) {
            product.setImageName(existingProduct.getImageName());
            product.setImageType(existingProduct.getImageType());
            product.setImageData(existingProduct.getImageData());
        }
    }
    return productRepo.save(product);
}
```

---

## 4. OAuth2 Intercepting Manual Login (CORS / Redirect Error)
**Error Message (in Frontend Console):**
`Access to fetch at 'https://accounts.google.com/o/oauth2/v2/auth...' (redirected from 'http://localhost:8080/login') ... has been blocked by CORS policy`

**Why it Occurred:**
When `.oauth2Login()` is enabled in `SecurityConfig`, Spring Security automatically registers its own endpoint at `/login` to handle the Google OAuth2 flow. When the React frontend sent a manual `POST` request with username/password to `/login`, Spring Security intercepted it before it reached the `UserController`, treated it as an OAuth2 attempt, and redirected it to Google. The frontend `fetch` followed the redirect, which Google blocked due to CORS (Google does not allow AJAX requests to its login page).

**The Solution:**
Move the manual authentication endpoints to a different path to prevent conflicts with Spring Security's default paths.
1. Change the controller endpoints to `/api/auth/login` and `/api/auth/register`.
2. Update `SecurityConfig` to permit `/api/auth/**`.
3. Update the frontend `fetch` URLs to match the new endpoints.

---

## 5. Spring Security Redirecting on Authentication Failure
**Error Message (in Frontend Console):**
`Access to fetch at 'https://accounts.google.com/o/oauth2/v2/auth...' (redirected from 'http://localhost:8080/api/auth/login') ... has been blocked by CORS policy`

**Why it Occurred (Even after fixing Problem 4):**
This happened when a user tried to login with incorrect credentials or when the database had duplicate usernames. When `authenticationManager.authenticate()` fails, it throws an `AuthenticationException`. Because this exception was not caught in the controller, it bubbled up to Spring Security. Spring Security saw the failed authentication, assumed the user needed to log in, and redirected them to the default OAuth2 entry point (Google), causing the exact same CORS error as before.

**The Solution:**
1. **Application Level:** Wrap the `authenticate()` call in a `try-catch` block inside the controller. This catches the exception before Spring Security sees it, allowing you to return a clean `401 Unauthorized` JSON response.
2. **Database Level:** Prevent duplicate usernames from being created in the first place by checking for existing users during registration and adding `@Column(unique = true)` to the `username` field in the `User` entity.

*Controller Fix:*
```java
@PostMapping("/api/auth/login")
public ResponseEntity<?> login(@RequestBody User user) {
    try {
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
        if (authentication.isAuthenticated()) {
            String token = jwtService.generateToken(user.getUsername());
            return ResponseEntity.ok(Collections.singletonMap("token", token));
        }
    } catch (Exception e) {
        // Catch the exception HERE so Spring Security doesn't redirect to OAuth2
    }
    return ResponseEntity.status(401).body(Collections.singletonMap("error", "Invalid username or password"));
}
```
