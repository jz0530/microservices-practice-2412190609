package com.zjgsu.cyy.repository;
import com.zjgsu.cyy.model.TradeUser;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<TradeUser,String> {}
