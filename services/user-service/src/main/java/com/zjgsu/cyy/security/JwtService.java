package com.zjgsu.cyy.security;

import com.zjgsu.cyy.model.Account;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(Account account) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(account.getId())
                .claim("username", account.getUsername())
                .claim("role", account.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)));
        if (account.getBuyerId() != null) {
            builder.claim("buyerId", account.getBuyerId());
        }
        return builder.signWith(key, Jwts.SIG.HS512).compact();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }
}
