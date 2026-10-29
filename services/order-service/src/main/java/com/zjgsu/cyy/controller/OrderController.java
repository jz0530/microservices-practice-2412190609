package com.zjgsu.cyy.controller;
import com.zjgsu.cyy.model.TradeOrder;
import com.zjgsu.cyy.service.OrderService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service=service; }
    public record CreateOrderRequest(String buyerId,String productId) {}
    @PostMapping public ResponseEntity<TradeOrder> create(@RequestBody CreateOrderRequest request) {
        var order=service.create(request.buyerId(),request.productId());
        return ResponseEntity.created(URI.create("/api/orders/"+order.getId())).body(order);
    }
    @GetMapping public List<TradeOrder> list() { return service.list(); }
    @GetMapping("/{id}") public TradeOrder find(@PathVariable("id") String id) { return service.find(id); }
}
