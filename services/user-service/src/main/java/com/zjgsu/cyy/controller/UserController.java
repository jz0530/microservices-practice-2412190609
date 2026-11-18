package com.zjgsu.cyy.controller;

import com.zjgsu.cyy.model.TradeUser;
import com.zjgsu.cyy.service.UserService;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService service;
    private final String instanceId;

    public UserController(UserService service,
                          @Value("${INSTANCE_ID:user-service}") String instanceId) {
        this.service = service;
        this.instanceId = instanceId;
    }

    public record CreateUserRequest(String name) {
    }

    @PostMapping
    public ResponseEntity<TradeUser> create(@RequestBody CreateUserRequest request) {
        var user = service.create(request.name());
        return ResponseEntity.created(URI.create("/api/users/" + user.getId())).body(user);
    }

    @GetMapping
    public List<TradeUser> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TradeUser find(@PathVariable("id") String id) {
        var user = service.find(id);
        user.setInstanceId(instanceId);
        log.info("USER_LOOKUP instance={} buyerId={}", instanceId, id);
        return user;
    }
}
