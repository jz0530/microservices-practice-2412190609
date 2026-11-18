package com.zjgsu.cyy.model;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class Account {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 36)
    private String buyerId;

    protected Account() {
    }

    public Account(String id, String username, String passwordHash, String role, String buyerId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.buyerId = buyerId;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getBuyerId() { return buyerId; }
}
