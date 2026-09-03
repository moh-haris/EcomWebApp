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
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(product))   
                .orElse(ResponseEntity.notFound().build());    
    }

    @GetMapping("/product/{productId}/image")
    public ResponseEntity<byte[]> getProductImage(@PathVariable int productId) {
        return productService.getProductById(productId)
                .map(product -> ResponseEntity.ok()
                      
                        .contentType(MediaType.parseMediaType(product.getImageType()))
                        .body(product.getImageData()))  
                .orElse(ResponseEntity.notFound().build()); 
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/product")
    public ResponseEntity<Product> addProduct(
            @RequestPart Product product,
            @RequestPart MultipartFile imageFile) throws IOException {

        Product savedProduct = productService.addOrUpdateProduct(product, imageFile);
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

   @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/product/{id}")
    public ResponseEntity<String> updateProduct(
            @PathVariable int id,
            @RequestPart Product product,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) throws IOException {

        product.setId(id);
        productService.addOrUpdateProduct(product, imageFile);
        return new ResponseEntity<>("Updated", HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/product/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable int id) {
        productService.deleteProduct(id);
        return new ResponseEntity<>("Product removed from shop successfully", HttpStatus.OK);
    }

    @GetMapping("/product/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String keyword) {
        List<Product> products = productService.searchProducts(keyword);
        System.out.println("Searching with keyword: " + keyword);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
}
