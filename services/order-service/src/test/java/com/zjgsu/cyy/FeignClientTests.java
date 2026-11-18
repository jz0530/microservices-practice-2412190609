package com.zjgsu.cyy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zjgsu.cyy.client.ProductFeignClient;
import com.zjgsu.cyy.client.TradeClient;
import com.zjgsu.cyy.client.UserFeignClient;
import com.zjgsu.cyy.exception.ApiFailure;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import feign.codec.Decoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 不连接 Nacos，直接用 Feign 访问本地 HTTP 服务，验证 Feign 调用与错误映射。
 */
class FeignClientTests {
    static final String USER = "11111111-1111-4111-8111-111111111111";
    static final String PRODUCT = "22222222-2222-4222-8222-222222222222";
    static final AtomicReference<String> userMode = new AtomicReference<>("ok");
    static final AtomicReference<String> productMode = new AtomicReference<>("ok");
    static final AtomicInteger productCalls = new AtomicInteger();
    static final HttpServer remote = startRemote();

    static HttpServer startRemote() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/users/", exchange -> {
                if ("missing".equals(userMode.get())) {
                    reply(exchange, 404, "{}");
                } else {
                    reply(exchange, 200, "{\"id\":\"" + USER + "\",\"name\":\"Alice\",\"instanceId\":\"user-1\"}");
                }
            });
            server.createContext("/api/products/", exchange -> {
                productCalls.incrementAndGet();
                switch (productMode.get()) {
                    case "conflict" -> reply(exchange, 409, "{}");
                    case "invalid" -> reply(exchange, 200, "{\"id\":\"" + PRODUCT
                            + "\",\"title\":\"Book\",\"description\":\"Used\",\"price\":25.50,\"status\":\"AVAILABLE\"}");
                    case "slow" -> {
                        try {
                            Thread.sleep(1200);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        reply(exchange, 200, "{\"id\":\"" + PRODUCT
                                + "\",\"title\":\"Book\",\"description\":\"Used\",\"price\":25.50,\"status\":\"RESERVED\"}");
                    }
                    default -> reply(exchange, 200, "{\"id\":\"" + PRODUCT
                            + "\",\"title\":\"Book\",\"description\":\"Used\",\"price\":25.50,\"status\":\"RESERVED\"}");
                }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length);
        try (var out = exchange.getResponseBody()) {
            out.write(data);
        } finally {
            exchange.close();
        }
    }

    @AfterAll
    static void shutdown() {
        remote.stop(0);
    }

    @BeforeEach
    void reset() {
        userMode.set("ok");
        productMode.set("ok");
        productCalls.set(0);
    }

    Decoder decoder() {
        return (response, type) -> new ObjectMapper()
                .readValue(response.body().asInputStream(), (Class<?>) type);
    }

    UserFeignClient userClient(String baseUrl) {
        return Feign.builder()
                .contract(new SpringMvcContract())
                .decoder(decoder())
                .retryer(Retryer.NEVER_RETRY)
                .target(UserFeignClient.class, baseUrl);
    }

    ProductFeignClient productClient(String baseUrl) {
        return productClient(baseUrl, 3000);
    }

    ProductFeignClient productClient(String baseUrl, int readTimeoutMs) {
        return Feign.builder()
                .contract(new SpringMvcContract())
                .decoder(decoder())
                .retryer(Retryer.NEVER_RETRY)
                .options(new Request.Options(300, TimeUnit.MILLISECONDS,
                        readTimeoutMs, TimeUnit.MILLISECONDS, true))
                .target(ProductFeignClient.class, baseUrl);
    }

    TradeClient tradeClient(String baseUrl) {
        return new TradeClient(userClient(baseUrl), productClient(baseUrl));
    }

    String baseUrl() {
        return "http://127.0.0.1:" + remote.getAddress().getPort();
    }

    @Test
    void feignCallsBothServicesAndReturnsRemotePrice() {
        TradeClient client = tradeClient(baseUrl());
        client.requireUser(USER);
        var product = client.reserve(PRODUCT);
        assertEquals("RESERVED", product.status());
        assertEquals(0, product.price().compareTo(new java.math.BigDecimal("25.50")));
        assertEquals(1, productCalls.get());
    }

    @Test
    void missingUserMapsTo404BeforeProductCall() {
        userMode.set("missing");
        ApiFailure error = assertThrows(ApiFailure.class,
                () -> tradeClient(baseUrl()).requireUser(USER));
        assertEquals(HttpStatus.NOT_FOUND, error.status());
        assertEquals(0, productCalls.get());
    }

    @Test
    void occupiedProductMapsTo409() {
        productMode.set("conflict");
        ApiFailure error = assertThrows(ApiFailure.class,
                () -> tradeClient(baseUrl()).reserve(PRODUCT));
        assertEquals(HttpStatus.CONFLICT, error.status());
    }

    @Test
    void invalidProductResponseMapsTo502() {
        productMode.set("invalid");
        ApiFailure error = assertThrows(ApiFailure.class,
                () -> tradeClient(baseUrl()).reserve(PRODUCT));
        assertEquals(HttpStatus.BAD_GATEWAY, error.status());
    }

    @Test
    void unavailableServiceMapsTo503() {
        TradeClient client = tradeClient("http://127.0.0.1:1");
        ApiFailure error = assertThrows(ApiFailure.class, () -> client.requireUser(USER));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
    }

    @Test
    void readTimeoutMapsTo503AndReservationIsAttemptedOnlyOnce() {
        productMode.set("slow");
        TradeClient client = new TradeClient(userClient(baseUrl()), productClient(baseUrl(), 300));
        ApiFailure error = assertThrows(ApiFailure.class, () -> client.reserve(PRODUCT));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        assertEquals(1, productCalls.get(), "写请求不应自动重试");
    }
}
