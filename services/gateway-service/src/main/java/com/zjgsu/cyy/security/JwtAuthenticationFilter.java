package com.zjgsu.cyy.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private final SecretKey key;
    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        key=Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        if (key.getEncoded().length < 64) throw new IllegalArgumentException("HS512 requires 64 secret bytes");
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String raw=exchange.getRequest().getURI().getRawPath();
        String path=exchange.getRequest().getURI().getPath();
        String method=exchange.getRequest().getMethod().name();
        // Reject alternate encodings/segment syntax before routing to Spring MVC.
        if (raw.contains("%") || path.contains(";") || path.contains("//") || path.contains("\\")
                || path.matches(".*(?:^|/)\\.\\.?(?:/|$).*") ) return fail(exchange,400,"Invalid path");
        if (path.length()>1 && path.endsWith("/")) path=path.substring(0,path.length()-1);
        if ("/api/auth/login".equals(path) && "POST".equals(method)) return chain.filter(identity(exchange,null));
        String auth=exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) return fail(exchange,401,"Authentication required");
        Claims claims;
        try {
            var jwt=Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7));
            claims=jwt.getPayload();
            if (!"HS512".equals(jwt.getHeader().getAlgorithm()) || claims.getExpiration()==null
                    || claims.getSubject()==null || claims.getSubject().isBlank()
                    || !Set.of("BUYER","ADMIN").contains(claims.get("role",String.class)))
                return fail(exchange,401,"Invalid token");
        } catch (RuntimeException error) { return fail(exchange,401,"Invalid or expired token"); }
        String role=claims.get("role",String.class);
        if (path.matches("/api/products/[^/]+/reservations")) return fail(exchange,403,"Internal endpoint");
        if ("POST".equals(method) && (path.equals("/api/products") || path.equals("/api/users"))
                && !"ADMIN".equals(role)) return fail(exchange,403,"Admin role required");
        if (path.equals("/api/users") && !"ADMIN".equals(role)) return fail(exchange,403,"Admin role required");
        if (path.startsWith("/api/users/") && !"ADMIN".equals(role)
                && !path.equals("/api/users/"+claims.get("buyerId",String.class))) return fail(exchange,403,"Own user only");
        if ("POST".equals(method) && path.equals("/api/orders") && !"BUYER".equals(role))
            return fail(exchange,403,"Buyer role required");
        return chain.filter(identity(exchange,claims));
    }
    private ServerWebExchange identity(ServerWebExchange exchange, Claims claims) {
        return exchange.mutate().request(exchange.getRequest().mutate().headers(h -> {
            for (String name: new String[]{"X-User-Id","X-Username","X-User-Role","X-Buyer-Id","X-Student-Id"}) h.remove(name);
            if (claims!=null) {
                h.set("X-User-Id",claims.getSubject());
                h.set("X-User-Role",claims.get("role",String.class));
                String username=claims.get("username",String.class), buyer=claims.get("buyerId",String.class);
                if(username!=null) h.set("X-Username",username);
                if(buyer!=null) h.set("X-Buyer-Id",buyer);
            }
        }).build()).build();
    }
    private Mono<Void> fail(ServerWebExchange exchange,int status,String message) {
        var response=exchange.getResponse(); response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body=("{\"status\":"+status+",\"message\":\""+message+"\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
    @Override public int getOrder() {return -100;}
}
