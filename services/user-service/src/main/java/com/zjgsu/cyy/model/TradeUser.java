package com.zjgsu.cyy.model;
import jakarta.persistence.*;
@Entity
@Table(name="users")
public class TradeUser {
    @Id @Column(length=36) private String id;
    @Column(nullable=false,length=80) private String name;
    protected TradeUser() {}
    public TradeUser(String id,String name) { this.id=id; this.name=name; }
    public String getId() { return id; }
    public String getName() { return name; }
}
