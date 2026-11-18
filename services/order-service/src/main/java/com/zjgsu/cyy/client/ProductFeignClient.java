package com.zjgsu.cyy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "product-service")
public interface ProductFeignClient {

    @PostMapping("/api/products/{id}/reservations")
    ProductView reserve(@PathVariable("id") String id);
}
