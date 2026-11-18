package com.zjgsu.cyy.client;

import com.zjgsu.cyy.exception.ApiFailure;
import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 通过 OpenFeign 按服务名调用用户服务和商品服务。
 * 服务地址由 Nacos + Spring Cloud LoadBalancer 解析，代码中不保存固定地址。
 */
@Component
public class TradeClient {
    private static final Logger log = LoggerFactory.getLogger(TradeClient.class);
    private final UserFeignClient users;
    private final ProductFeignClient products;

    public TradeClient(UserFeignClient users, ProductFeignClient products) {
        this.users = users;
        this.products = products;
    }

    public void requireUser(String id) {
        try {
            UserView user = users.getUser(id);
            if (user == null || !id.equals(user.id())) {
                throw new ApiFailure(HttpStatus.BAD_GATEWAY, "Invalid user service response");
            }
            log.info("Feign user lookup succeeded: buyerId={}", id);
        } catch (ApiFailure e) {
            throw e;
        } catch (FeignException e) {
            throw translate(e, "User");
        }
    }

    public ProductView reserve(String id) {
        try {
            ProductView product = products.reserve(id);
            if (product == null || !id.equals(product.id()) || product.title() == null
                    || product.title().length() > 120 || product.price() == null
                    || product.price().signum() <= 0 || !"RESERVED".equals(product.status())) {
                throw new ApiFailure(HttpStatus.BAD_GATEWAY, "Invalid product service response");
            }
            return product;
        } catch (ApiFailure e) {
            throw e;
        } catch (FeignException e) {
            throw translate(e, "Product");
        }
    }

    private ApiFailure translate(FeignException error, String dependency) {
        int status = error.status();
        if (status == 404) {
            return new ApiFailure(HttpStatus.NOT_FOUND, dependency + " not found");
        }
        if (status == 409) {
            return new ApiFailure(HttpStatus.CONFLICT, "Product already reserved");
        }
        if (error instanceof RetryableException || status <= 0 || status >= 500) {
            return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    dependency + " service unavailable or timed out; order not created");
        }
        return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE,
                dependency + " service returned HTTP " + status + "; order not created");
    }
}
