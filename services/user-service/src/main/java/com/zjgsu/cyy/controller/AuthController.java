package com.zjgsu.cyy.controller;

import com.zjgsu.cyy.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.LoginResult> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(service.login(request.username(), request.password()));
    }
}
