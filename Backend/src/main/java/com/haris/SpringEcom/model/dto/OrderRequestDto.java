package com.haris.SpringEcom.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrderRequestDto(
    @NotBlank(message = "Customer name is required")
    String customerName,
    
    @NotBlank(message = "Email is required")
    String email,
    
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    List<OrderItemRequestDto> items
) {}
