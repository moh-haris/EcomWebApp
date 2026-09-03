package com.haris.SpringEcom.model.dto;

import java.time.LocalDate;
import java.util.List;

public record OrderResponseDto(
    String orderId,
    String customerName,
    String email,
    String status,
    LocalDate orderDate,
    List<OrderItemResponseDto> items
) {}
