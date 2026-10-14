package com.zjgsu.cyy.repository;
import com.zjgsu.cyy.model.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderRepository extends JpaRepository<TradeOrder,String> {}
