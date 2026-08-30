package com.haris.SpringEcom.error;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(int id) {
        super("Product not found with id: " + id);
    }
}