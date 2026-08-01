package com.haris.SpringEcom.controller;

import com.haris.SpringEcom.model.Product;
import com.haris.SpringEcom.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getProducts() {
        return new ResponseEntity<>(productService.getAllProducts(), HttpStatus.OK);
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable int id) {
        // Optional.map() returns 200 OK with product if present, orElse returns 404 if empty
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(product))
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/product/{productId}/image")
    public ResponseEntity<byte[]> getProductImage(@PathVariable int productId) {
        // Use Optional.map() to safely access product fields only when the product exists
        return productService.getProductById(productId)
                .map(product -> ResponseEntity.ok()
                        // Tell the browser/client exactly what image format this is (PNG, JPEG, WebP, etc.)
                        .contentType(MediaType.parseMediaType(product.getImageType()))
                        .body(product.getImageData()))
                .orElse(ResponseEntity.notFound().build());
    }
    // RBAC: Only ADMIN can add a product. USER hitting this endpoint gets 403 Forbidden.
    // @PreAuthorize works because @EnableMethodSecurity is set in WebSecurityConfig.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/product")
    public ResponseEntity<?> addProduct(@RequestPart Product product, @RequestPart MultipartFile imageFile) {
        Product savedProduct = null;
        try {
            savedProduct = productService.addOrUpdateProduct(product, imageFile);
            return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    // RBAC: Only ADMIN can update product details.
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/product/{id}")
    public ResponseEntity<String> updateProduct(@PathVariable int id, @RequestPart Product product, @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        try {
            product.setId(id);
            Product updatedProduct = productService.addOrUpdateProduct(product, imageFile);
            return new ResponseEntity<>("Updated", HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    // RBAC: Only ADMIN can delete a product.
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/product/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable int id) {
        // SOFT DELETE: The service no longer runs SQL DELETE.
        // It sets productAvailable=false and stockQuantity=0 instead.
        // If the product ID doesn't exist, service throws RuntimeException → we return 404.
        try {
            productService.deleteProduct(id);
            return new ResponseEntity<>("Product removed from shop successfully", HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>("Product not found", HttpStatus.NOT_FOUND);
        }
    }
    @GetMapping("/product/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String keyword) {
        List<Product> products = productService.searchProducts(keyword);
        System.out.println("searching with :" + keyword);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
}
