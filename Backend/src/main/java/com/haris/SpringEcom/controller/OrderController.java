package com.haris.SpringEcom.controller;

import com.haris.SpringEcom.model.dto.OrderRequestDto;
import com.haris.SpringEcom.model.dto.OrderResponseDto;
import com.haris.SpringEcom.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/orders/place")
    public ResponseEntity<OrderResponseDto> placeOrder(@Valid @RequestBody OrderRequestDto orderRequestDto) {
        OrderResponseDto orderResponseDto = orderService.placeOrder(orderRequestDto);
        return new ResponseEntity<>(orderResponseDto, HttpStatus.CREATED);
    }
    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponseDto>> getAllOrders() {
        List<OrderResponseDto> orderResponseDtoList = orderService.getAllOrderResponses();
        return new ResponseEntity<>(orderResponseDtoList, HttpStatus.OK);
    }
}
