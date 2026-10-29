package com.zjgsu.cyy.service;
import com.zjgsu.cyy.model.TradeUser;
import com.zjgsu.cyy.repository.UserRepository;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class UserService {
    private final UserRepository users;
    public UserService(UserRepository users) { this.users=users; }
    public TradeUser create(String name) {
        if(name==null || name.isBlank() || name.trim().length()>80) throw new IllegalArgumentException("Name must contain 1-80 characters");
        return users.saveAndFlush(new TradeUser(UUID.randomUUID().toString(),name.trim()));
    }
    public TradeUser find(String id) { return users.findById(id).orElseThrow(()->new NoSuchElementException("User not found")); }
    public List<TradeUser> list() { return users.findAll(); }
}
