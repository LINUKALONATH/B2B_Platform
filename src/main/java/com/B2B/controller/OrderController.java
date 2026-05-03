package com.B2B.controller;

import com.B2B.model.Order;
import com.B2B.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/admin/orders")
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/orders")
    public Order placeOrder(@RequestBody Order order) {
        order.setStatus("Pending");
        order.setPaymentStatus("Unpaid");
        order.setDueDate(java.time.LocalDateTime.now().plusDays(30));
        return orderRepository.save(order);
    }

    @GetMapping("/business/{id}/orders/buy")
    public List<Order> getBuyingOrders(@PathVariable int id) {
        return orderRepository.findByBuyerId(id);
    }

    @GetMapping("/business/{id}/orders/sell")
    public List<Order> getSellingOrders(@PathVariable int id) {
        return orderRepository.findBySupplierId(id);
    }

    @PutMapping("/orders/{id}/status")
    public Order updateOrderStatus(@PathVariable int id, @RequestBody java.util.Map<String, String> payload) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        if (payload.containsKey("status")) {
            String newStatus = payload.get("status");
            order.setStatus(newStatus);
            // If order is approved/paid, synchronize payment status
            if ("Paid".equalsIgnoreCase(newStatus) || "Approved".equalsIgnoreCase(newStatus)) {
                order.setPaymentStatus("Paid");
            }
        }
        return orderRepository.save(order);
    }

    @PutMapping("/orders/{id}/payment-status")
    public Order updateOrderPaymentStatus(@PathVariable int id, @RequestBody java.util.Map<String, String> payload) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        if (payload.containsKey("paymentStatus")) {
            order.setPaymentStatus(payload.get("paymentStatus"));
        }
        return orderRepository.save(order);
    }
}
