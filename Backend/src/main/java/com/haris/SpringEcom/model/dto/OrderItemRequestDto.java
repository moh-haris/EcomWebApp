package com.haris.SpringEcom.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record OrderItemRequestDto(
    @Positive(message = "Product ID must be valid")
    int productId,
    
    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity
) {}
