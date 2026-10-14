package com.zjgsu.cyy.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="trade_orders", uniqueConstraints=@UniqueConstraint(name="uk_order_product", columnNames="product_id"))
public class TradeOrder {
    @Id @Column(length=36) private String id;
    @ManyToOne(optional=false) @JoinColumn(name="product_id", nullable=false,
        foreignKey=@ForeignKey(name="fk_order_product")) private Product product;
    @Column(nullable=false, length=80) private String buyerName;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Column(nullable=false, length=24) private String status;
    @Column(nullable=false) private Instant createdAt;
    protected TradeOrder() {}
    public TradeOrder(String id, Product product, String buyerName) {
        this.id=id;this.product=product;this.buyerName=buyerName;
        amount=product.price();status="PENDING_PAYMENT";createdAt=Instant.now();
    }
    public String id() { return id; }
    public String productId() { return product.getId(); }
    public String buyerName() { return buyerName; }
    public BigDecimal amount() { return amount; }
    public String status() { return status; }
    public Instant createdAt() { return createdAt; }
}
