package com.zjgsu.cyy.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class TradeUser {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;

    /**
     * 实例标识只用于本次演进中的分发验证，不写入数据库。
     */
    @Transient
    private String instanceId;

    protected TradeUser() {
    }

    public TradeUser(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
