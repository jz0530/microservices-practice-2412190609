package com.zjgsu.cyy;
import com.zjgsu.cyy.config.PasswordConfig;
import com.zjgsu.cyy.model.Account;
import com.zjgsu.cyy.repository.AccountRepository;
import com.zjgsu.cyy.security.JwtService;
import com.zjgsu.cyy.service.AuthService;
import com.zjgsu.cyy.exception.ApiFailure;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthTests {
    final byte[] key=new byte[64];
    final JwtService jwt=new JwtService(Base64.getEncoder().encodeToString(key),3600);
    final AccountRepository accounts=mock(AccountRepository.class);
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    final AuthService auth=new AuthService(accounts,encoder,jwt);
    @Test void hashedPasswordLoginIssuesExpiringBuyerToken() {
        String hash=encoder.encode("test-only-password");
        assertNotEquals("test-only-password",hash);
        when(accounts.findByUsername("buyer")).thenReturn(Optional.of(new Account("account-1","buyer",hash,"BUYER","buyer-1")));
        var result=auth.login("buyer","test-only-password");
        var parsed=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key)).build().parseSignedClaims(result.token());
        assertEquals("HS512",parsed.getHeader().getAlgorithm());
        assertEquals("BUYER",parsed.getPayload().get("role"));
        assertEquals("buyer-1",parsed.getPayload().get("buyerId"));
        assertTrue(parsed.getPayload().getExpiration().after(new Date()));
    }
    @Test void wrongPasswordRejected() {
        when(accounts.findByUsername("buyer")).thenReturn(Optional.of(new Account("id","buyer",encoder.encode("correct"),"BUYER","id")));
        assertThrows(ApiFailure.class,()->auth.login("buyer","wrong"));
    }
    @Test void missingAccountRejected() {
        when(accounts.findByUsername("absent")).thenReturn(Optional.empty());
        assertThrows(ApiFailure.class,()->auth.login("absent","wrong"));
    }
}
