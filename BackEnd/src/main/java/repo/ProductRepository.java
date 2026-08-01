package com.haris.SpringEcom.repo;

import com.haris.SpringEcom.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer>{

    // SOFT DELETE: Returns only products where productAvailable = true.
    // Admin-deleted products (productAvailable=false) are excluded from this list.
    // Used by getAllProducts() so User3 browsing the shop never sees removed products.
    List<Product> findByProductAvailableTrue();

    // SOFT DELETE: Search also excludes soft-deleted products.
    // Added "AND p.productAvailable = true" so deleted products don't appear in search results either.
    @Query("SELECT p from Product p WHERE " +
            "p.productAvailable = true AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.brand) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Product> searchProducts(String keyword);
}
