package com.zjgsu.cyy.model;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id @Column(length=36) private String id;
    @Column(nullable=false, length=120) private String title;
    @Column(nullable=false, length=2000) private String description;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal price;
    @Column(nullable=false, length=24) private String status;
    protected Product() {}
    public Product(String id, String title, String description, BigDecimal price, String status) {
        this.id=id; this.title=title; this.description=description; this.price=price; this.status=status;
    }
    public String id() { return id; }
    public String getId() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public BigDecimal price() { return price; }
    public String status() { return status; }
    public void reserve() { status="RESERVED"; }
}
