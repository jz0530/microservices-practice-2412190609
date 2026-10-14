package com.zjgsu.cyy.dto;
import com.zjgsu.cyy.model.TradeOrder;
import java.math.BigDecimal;
import java.time.Instant;
public record OrderResponse(String id, String productId, String buyerName, BigDecimal amount,
                            String status, Instant createdAt) {
    public static OrderResponse from(TradeOrder o) {
        return new OrderResponse(o.id(),o.productId(),o.buyerName(),o.amount(),o.status(),o.createdAt());
    }
}
