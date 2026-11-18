package com.zjgsu.cyy.config;

import com.zjgsu.cyy.model.Account;
import com.zjgsu.cyy.model.TradeUser;
import com.zjgsu.cyy.repository.AccountRepository;
import com.zjgsu.cyy.repository.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Only instance 1 seeds the shared database. Existing accounts are never overwritten. */
@Component
public class DemoAccountInitializer implements CommandLineRunner {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String buyerPassword, buyer2Password, adminPassword;
    public DemoAccountInitializer(AccountRepository accounts, UserRepository users, PasswordEncoder encoder,
            @Value("${demo.seed.enabled:false}") boolean enabled,
            @Value("${BUYER_PASSWORD:}") String buyerPassword,
            @Value("${BUYER2_PASSWORD:}") String buyer2Password,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.accounts=accounts; this.users=users; this.encoder=encoder; this.enabled=enabled;
        this.buyerPassword=buyerPassword; this.buyer2Password=buyer2Password; this.adminPassword=adminPassword;
    }
    @Override @Transactional
    public void run(String... args) {
        if (!enabled) return;
        seed("buyer", "BUYER", "a1111111-1111-4111-8111-111111111111", buyerPassword);
        seed("buyer2", "BUYER", "a2222222-2222-4222-8222-222222222222", buyer2Password);
        seed("admin", "ADMIN", null, adminPassword);
    }
    private void seed(String username, String role, String buyerId, String password) {
        if (accounts.findByUsername(username).isPresent()) return;
        if (password == null || password.length() < 12) throw new IllegalStateException("Demo passwords must contain at least 12 characters");
        if (buyerId != null && !users.existsById(buyerId)) users.saveAndFlush(new TradeUser(buyerId, "Demo " + username));
        accounts.saveAndFlush(new Account(UUID.randomUUID().toString(),username,encoder.encode(password),role,buyerId));
    }
}
