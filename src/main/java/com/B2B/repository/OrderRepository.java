package com.B2B.repository;

import com.B2B.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByBuyerId(int buyerId);
    List<Order> findBySupplierId(int supplierId);
    List<Order> findAllByOrderByCreatedAtDesc();
}
