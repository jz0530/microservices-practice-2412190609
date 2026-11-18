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

    /*
     * client：负责通过 HTTP 调用用户服务、商品服务。
     * orders：负责读写自己的订单数据库。
     */
    public OrderService(TradeClient client, OrderRepository orders) {
        this.client = client;
        this.orders = orders;
    }

    public TradeOrder create(String buyerId, String productId) {
        validateId(buyerId);
        validateId(productId);
        client.requireUser(buyerId);
        var product = client.reserve(productId);
        // 调用商品服务占用商品，拿回商品信息
        // 把保存后的订单返回给调用者.商品占用已经在远端完成，接下来的订单保存仍可能失败
        return orders.saveAndFlush(
                new TradeOrder(UUID.randomUUID().toString(), buyerId, productId, product.title(), product.price()));
    }

    private void validateId(String id) {
        if (id == null || !UUID.fromString(id).toString().equalsIgnoreCase(id))
            throw new IllegalArgumentException("A valid UUID is required");
    }

    public List<TradeOrder> list() {
        return orders.findAll();
    }

    public TradeOrder find(String id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("Order not found"));
    }
}
