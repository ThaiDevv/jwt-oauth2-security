package com.attt.noauth.controller;

import com.attt.noauth.model.Order;
import com.attt.noauth.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserController {

    private final List<User> users = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();

    public UserController() {
        // Seed dummy data
        users.add(new User(1L, "alice", "alice@gmail.com", "user"));
        users.add(new User(2L, "bob", "bob@gmail.com", "user"));
        users.add(new User(3L, "admin", "admin@attt.com", "admin"));

        orders.add(new Order(1L, 1L, "iPhone 15 Pro Max", 1299.0, "DELIVERED"));
        orders.add(new Order(2L, 2L, "Samsung Galaxy S24 Ultra", 1199.0, "SHIPPED"));
        orders.add(new Order(3L, 3L, "MacBook Pro M3 Max", 3499.0, "PROCESSING"));
        orders.add(new Order(4L, 1L, "AirPods Pro 2", 249.0, "DELIVERED"));
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<?> getProfile(@PathVariable Long userId) {
        return users.stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .map(u -> new ResponseEntity<Object>(u, HttpStatus.OK))
                .orElse(new ResponseEntity<>(Map.of("error", "User not found"), HttpStatus.NOT_FOUND));
    }

    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<List<Order>> getOrders(@PathVariable Long userId) {
        List<Order> userOrders = orders.stream()
                .filter(o -> o.getUserId().equals(userId))
                .toList();
        return ResponseEntity.ok(userOrders);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
        return orders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst()
                .map(o -> new ResponseEntity<Object>(o, HttpStatus.OK))
                .orElse(new ResponseEntity<>(Map.of("error", "Order not found"), HttpStatus.NOT_FOUND));
    }
}
