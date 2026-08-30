package com.haris.SpringEcom.service;

import com.haris.SpringEcom.model.Product;
import com.haris.SpringEcom.repo.ProductRepository;
import com.haris.SpringEcom.error.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(value = "productsList")
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findByProductAvailableTrue();
    }

    public Optional<Product> getProductById(int id) {
        return productRepository.findById(id);
    }

    @CacheEvict(value = "productsList", allEntries = true)
    public Product addOrUpdateProduct(Product product, MultipartFile image) throws IOException {

        if (image != null && !image.isEmpty()) {
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

    @CacheEvict(value = "productsList", allEntries = true)
    public void deleteProduct(int id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setProductAvailable(false);
        product.setStockQuantity(0);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> searchProducts(String keyword) {
        return productRepository.searchProducts(keyword);
    }
}
