package com.zjgsu.cyy.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name="trade_orders",uniqueConstraints=@UniqueConstraint(columnNames="product_id"))
public class TradeOrder {
    @Id @Column(length=36) private String id;
    @Column(name="buyer_id",nullable=false,length=36) private String buyerId;
    @Column(name="product_id",nullable=false,length=36) private String productId;
    @Column(nullable=false,length=120) private String productTitle;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=24) private String status;
    @Column(nullable=false) private Instant createdAt;
    protected TradeOrder() {}
    public TradeOrder(String id,String buyerId,String productId,String title,BigDecimal amount) {
        this.id=id;this.buyerId=buyerId;this.productId=productId;this.productTitle=title;
        this.amount=amount;this.status="PENDING_PAYMENT";this.createdAt=Instant.now();
    }
    public String getId(){return id;}
    public String getBuyerId(){return buyerId;}
    public String getProductId(){return productId;}
    public String getProductTitle(){return productTitle;}
    public BigDecimal getAmount(){return amount;}
    public String getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}
}
