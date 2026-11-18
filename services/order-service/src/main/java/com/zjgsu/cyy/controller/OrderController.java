package com.zjgsu.cyy.controller;

import com.zjgsu.cyy.model.TradeOrder;
import com.zjgsu.cyy.service.OrderService;
import java.net.URI;
import com.zjgsu.cyy.exception.ApiFailure;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public record CreateOrderRequest(String buyerId, String productId) {
    }

    @PostMapping
    public ResponseEntity<TradeOrder> create(@RequestBody CreateOrderRequest request,
            @RequestHeader(value="X-Buyer-Id",required=false) String buyer,
            @RequestHeader(value="X-User-Role",required=false) String role) {
        if (!"BUYER".equals(role) || buyer==null || !buyer.equals(request.buyerId()))
            throw new ApiFailure(HttpStatus.FORBIDDEN,"Buyers may only order for themselves");
        var order = service.create(request.buyerId(), request.productId());
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId())).body(order);
    }

    @GetMapping
    public List<TradeOrder> list(
            @RequestHeader(value="X-Buyer-Id",required=false) String buyer,
            @RequestHeader(value="X-User-Role",required=false) String role) {
        requireIdentity(buyer,role);
        return "ADMIN".equals(role) ? service.list() : service.list().stream().filter(o -> buyer.equals(o.getBuyerId())).toList();
    }

    @GetMapping("/{id}")
    public TradeOrder find(@PathVariable("id") String id,
            @RequestHeader(value="X-Buyer-Id",required=false) String buyer,
            @RequestHeader(value="X-User-Role",required=false) String role) {
        requireIdentity(buyer,role);
        var order=service.find(id);
        if (!"ADMIN".equals(role) && !buyer.equals(order.getBuyerId())) throw new ApiFailure(HttpStatus.FORBIDDEN,"Own order only");
        return order;
    }
    private void requireIdentity(String buyer,String role) {
        if (!"ADMIN".equals(role) && !("BUYER".equals(role) && buyer!=null && !buyer.isBlank()))
            throw new ApiFailure(HttpStatus.FORBIDDEN,"Verified identity required");
    }
}
