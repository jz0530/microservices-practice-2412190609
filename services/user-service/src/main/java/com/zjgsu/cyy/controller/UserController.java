package com.zjgsu.cyy.controller;
import com.zjgsu.cyy.model.TradeUser;
import com.zjgsu.cyy.service.UserService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service=service; }
    public record CreateUserRequest(String name) {}
    @PostMapping public ResponseEntity<TradeUser> create(@RequestBody CreateUserRequest request) {
        var user=service.create(request.name());
        return ResponseEntity.created(URI.create("/api/users/"+user.getId())).body(user);
    }
    @GetMapping public List<TradeUser> list() { return service.list(); }
    @GetMapping("/{id}") public TradeUser find(@PathVariable("id") String id) { return service.find(id); }
}
