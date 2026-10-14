package com.zjgsu.cyy.controller;

import com.zjgsu.cyy.dto.CreateOrderRequest;
import com.zjgsu.cyy.dto.OrderResponse;
import com.zjgsu.cyy.service.OrderService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        var order = service.create(request.productId(), request.buyerName());
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable("id") String id) {
        return OrderResponse.from(service.find(id));
    }
}