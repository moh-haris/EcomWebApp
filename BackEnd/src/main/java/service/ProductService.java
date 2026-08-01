package com.haris.SpringEcom.service;

import com.haris.SpringEcom.model.Product;
import com.haris.SpringEcom.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        // SOFT DELETE: Only return products that are still active (productAvailable = true).
        // Admin-deleted products (productAvailable=false) are filtered out here.
        // This ensures User3 browsing the shop never sees a removed product.
        return productRepository.findByProductAvailableTrue();
    }
    // Returns Optional — caller must handle the empty case explicitly (no more fake Product(-1))
    public Optional<Product> getProductById(int id) {
        return productRepository.findById(id);
    }
    public Product addOrUpdateProduct(Product product, MultipartFile image) throws IOException {

        if(image!=null && !image.isEmpty()) {
            product.setImageName(image.getOriginalFilename());
            product.setImageType(image.getContentType());
            product.setImageData(image.getBytes());
        } else if (product.getId() > 0) {
            Product existingProduct = productRepository.findById(product.getId()).orElse(null);
            if (existingProduct != null) {
                product.setImageName(existingProduct.getImageName());
                product.setImageType(existingProduct.getImageType());
                product.setImageData(existingProduct.getImageData());
            }
        }

        return productRepository.save(product);
    }
    public void deleteProduct(int id) {
        // SOFT DELETE: We do NOT run a SQL DELETE on the product row.
        //
        // WHY? Because the product may be referenced in order_item table by past orders.
        // A hard DELETE would violate the foreign key constraint and throw a 500 error.
        //
        // WHAT WE DO INSTEAD:
        //  - productAvailable = false → hides the product from all listings and search
        //  - stockQuantity = 0       → blocks any pending cart users from checking out
        //
        // The row stays in DB so order history remains fully intact.
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        product.setProductAvailable(false); // primary soft-delete signal: hide from shop
        product.setStockQuantity(0);        // secondary signal: block checkout for cart users

        productRepository.save(product);
    }
    @Transactional(readOnly = true)
    public List<Product> searchProducts(String keyword) {
        return productRepository.searchProducts(keyword);
    }
}
