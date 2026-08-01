package com.haris.SpringEcom.service;

import com.haris.SpringEcom.model.Order;
import com.haris.SpringEcom.model.OrderItem;
import com.haris.SpringEcom.model.Product;
import com.haris.SpringEcom.model.User;
import com.haris.SpringEcom.model.dto.OrderItemRequestDto;
import com.haris.SpringEcom.model.dto.OrderItemResponseDto;
import com.haris.SpringEcom.model.dto.OrderRequestDto;
import com.haris.SpringEcom.model.dto.OrderResponseDto;
import com.haris.SpringEcom.repo.OrderRepository;
import com.haris.SpringEcom.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepo;

    @Transactional
    public OrderResponseDto placeOrder(OrderRequestDto request) {

        // Get the currently logged-in user from Spring Security context
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Order order = new Order();
        String orderId = "ORD" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
        order.setOrderId(orderId);
        order.setCustomerName(request.customerName());
        order.setEmail(request.email());
        order.setUserId(currentUser.getId()); // Link order to the user who placed it
        order.setStatus("PLACED");
        order.setOrderDate(LocalDate.now());

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequestDto itemReq : request.items()) {

            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Guard 1: Quantity sent by client must be a positive number
            if (itemReq.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product: " + product.getName());
            }

            // Guard 2: Available stock must be enough to fulfil the request
            if (product.getStockQuantity() < itemReq.quantity()) {
                throw new IllegalStateException("Insufficient stock for: " + product.getName()
                        + ". Available: " + product.getStockQuantity()
                        + ", Requested: " + itemReq.quantity());
            }

            product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());
            productRepository.save(product);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.quantity())
                    .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity())))
                    .order(order)
                    .build();
            orderItems.add(orderItem);

        }
        order.setOrderItems(orderItems);
        Order savedOrder = orderRepo.save(order);
            List<OrderItemResponseDto> itemResponses = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            OrderItemResponseDto orderItemResponseDto = new OrderItemResponseDto(
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getTotalPrice()
            );
            itemResponses.add(orderItemResponseDto);
        }

        OrderResponseDto orderResponseDto = new OrderResponseDto(
                savedOrder.getOrderId(),
                savedOrder.getCustomerName(),
                savedOrder.getEmail(),
                savedOrder.getStatus(),
                savedOrder.getOrderDate(),
                itemResponses
        );
        return orderResponseDto;
    }
    @Transactional
    public List<OrderResponseDto> getAllOrderResponses() {

        // Get the currently logged-in user
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        List<Order> orders;
        
        // RBAC: Admin sees all orders across the entire platform. User sees only their own.
        if (currentUser.getRole() == com.haris.SpringEcom.model.Role.ADMIN) {
            orders = orderRepo.findAll();
        } else {
            orders = orderRepo.findByUserId(currentUser.getId());
        }
        
        List<OrderResponseDto> orderResponsDtos = new ArrayList<>();

        for (Order order : orders) {
            List<OrderItemResponseDto> itemResponses = new ArrayList<>();
            for(OrderItem item : order.getOrderItems()) {
                OrderItemResponseDto orderItemResponseDto = new OrderItemResponseDto(
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getTotalPrice()
                );
                itemResponses.add(orderItemResponseDto);
            }
            OrderResponseDto orderResponseDto = new OrderResponseDto(
                    order.getOrderId(),
                    order.getCustomerName(),
                    order.getEmail(),
                    order.getStatus(),
                    order.getOrderDate(),
                    itemResponses
            );
            orderResponsDtos.add(orderResponseDto);
        }

        return orderResponsDtos;
    }
}
