package com.zjgsu.cyy.client;

import com.zjgsu.cyy.exception.ApiFailure;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class TradeClient {
    private static final Logger log = LoggerFactory.getLogger(TradeClient.class);
    private final RestTemplate http;
    private final DiscoveryClient discovery;

    public record UserView(String id, String name) {
    }

    public record ProductView(String id, String title, String description,
                              BigDecimal price, String status) {
    }

    public TradeClient(RestTemplate http, DiscoveryClient discovery) {
        this.http = http;
        this.discovery = discovery;
    }

    /**
     * 每次业务操作都向 Nacos 重新获取实例，不在启动时缓存固定地址。
     * 没有可用实例时明确返回 503，调用方不会继续写本地订单。
     */
    private String baseUrl(String serviceName) {
        var instances = discovery.getInstances(serviceName);
        if (instances.isEmpty()) {
            throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instance: " + serviceName);
        }
        String address = instances.getFirst().getUri().toString().replaceAll("/+$", "");
        log.info("Nacos discovery: {} -> {}", serviceName, address);
        return address;
    }

    public void requireUser(String id) {
        try {
            var user = http.getForObject(baseUrl("user-service") + "/api/users/{id}",
                    UserView.class, id);
            if (user == null || !id.equals(user.id())) {
                throw new ApiFailure(HttpStatus.BAD_GATEWAY, "Invalid user service response");
            }
        } catch (RestClientException e) {
            throw translate(e, "User");
        }
    }

    public ProductView reserve(String id) {
        try {
            var product = http.postForObject(baseUrl("product-service")
                    + "/api/products/{id}/reservations", null, ProductView.class, id);
            if (product == null || !id.equals(product.id()) || product.title() == null
                    || product.title().length() > 120 || product.price() == null
                    || product.price().signum() <= 0 || !"RESERVED".equals(product.status())) {
                throw new ApiFailure(HttpStatus.BAD_GATEWAY, "Invalid product service response");
            }
            return product;
        } catch (RestClientException e) {
            throw translate(e, "Product");
        }
    }

    private ApiFailure translate(RestClientException e, String dependency) {
        if (e instanceof HttpClientErrorException client) {
            if (client.getStatusCode().value() == 404) {
                return new ApiFailure(HttpStatus.NOT_FOUND, dependency + " not found");
            }
            if (client.getStatusCode().value() == 409) {
                return new ApiFailure(HttpStatus.CONFLICT, "Product already reserved");
            }
        }
        return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE,
                dependency + " service unavailable or timed out; order not created");
    }
}
