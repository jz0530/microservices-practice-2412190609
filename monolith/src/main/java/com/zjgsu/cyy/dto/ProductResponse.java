package com.zjgsu.cyy.dto;
import com.zjgsu.cyy.model.Product;
import java.math.BigDecimal;
public record ProductResponse(String id, String title, String description, BigDecimal price, String status) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.id(),p.title(),p.description(),p.price(),p.status());
    }
}
