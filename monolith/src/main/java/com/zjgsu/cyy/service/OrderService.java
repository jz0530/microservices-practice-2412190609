package com.zjgsu.cyy.service;
import com.zjgsu.cyy.model.TradeOrder;
import com.zjgsu.cyy.repository.*;
import com.zjgsu.cyy.exception.ProductUnavailableException;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final ProductRepository products;
    private final OrderRepository orders;
    public OrderService(ProductRepository products, OrderRepository orders) {
        this.products=products;this.orders=orders;
    }
    @Transactional
    public TradeOrder create(String productId, String buyerName) {
        if (productId == null || productId.isBlank() || buyerName == null
                || buyerName.isBlank() || buyerName.trim().length()>80) {
            throw new IllegalArgumentException("商品编号和买家名称不能为空，买家名称最多 80 字");
        }
        var product=products.findForUpdate(productId)
                .orElseThrow(() -> new NoSuchElementException("商品不存在"));
        if (!"AVAILABLE".equals(product.status())) { throw new ProductUnavailableException(); }
        product.reserve();
        var order=new TradeOrder(UUID.randomUUID().toString(), product, buyerName.trim());
        products.save(product);
        return orders.saveAndFlush(order);
    }
    @Transactional(readOnly=true)
    public TradeOrder find(String id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("订单不存在"));
    }
}
