package com.zjgsu.cyy.service;
import com.zjgsu.cyy.client.TradeClient;
import com.zjgsu.cyy.model.TradeOrder;
import com.zjgsu.cyy.repository.OrderRepository;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class OrderService {
    private final TradeClient client;
    private final OrderRepository orders;
    public OrderService(TradeClient client,OrderRepository orders) { this.client=client;this.orders=orders; }
    public TradeOrder create(String buyerId,String productId) {
        validateId(buyerId); validateId(productId);
        client.requireUser(buyerId);
        var product=client.reserve(productId);
        // The remote reservation has committed. A local failure here needs future reconciliation.
        return orders.saveAndFlush(new TradeOrder(UUID.randomUUID().toString(),buyerId,productId,product.title(),product.price()));
    }
    private void validateId(String id) {
        if(id==null || !UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("A valid UUID is required");
    }
    public List<TradeOrder> list() { return orders.findAll(); }
    public TradeOrder find(String id) { return orders.findById(id).orElseThrow(()->new NoSuchElementException("Order not found")); }
}
