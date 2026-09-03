package com.haris.SpringEcom.repo;

import com.haris.SpringEcom.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderId(String orderId);
    List<Order> findByUserId(Long userId);  // Fetch only orders belonging to a specific user
}
