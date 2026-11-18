package com.zjgsu.cyy.service;

import com.zjgsu.cyy.exception.ApiFailure;
import com.zjgsu.cyy.model.Account;
import com.zjgsu.cyy.repository.AccountRepository;
import com.zjgsu.cyy.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AccountRepository accounts, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            throw new ApiFailure(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        Account account = accounts.findByUsername(username.trim())
                .orElseThrow(() -> new ApiFailure(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new ApiFailure(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return new LoginResult(
                jwtService.issue(account),
                jwtService.expirationSeconds(),
                account.getUsername(),
                account.getRole(),
                account.getBuyerId()
        );
    }

    public record LoginResult(String token, long expiresIn, String username,
                              String role, String buyerId) {
    }
}
