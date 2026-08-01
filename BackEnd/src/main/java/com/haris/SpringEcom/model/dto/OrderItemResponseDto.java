package com.haris.SpringEcom.model.dto;

import java.math.BigDecimal;

public record OrderItemResponseDto(
    String productName,
    int quantity,
    BigDecimal totalPrice
) {}
