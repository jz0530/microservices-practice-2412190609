package com.zjgsu.cyy.client;
import java.math.BigDecimal;
import com.zjgsu.cyy.exception.ApiFailure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class TradeClient {
    private final RestTemplate http;
    private final String userUrl,productUrl;
    public record UserView(String id,String name) {}
    public record ProductView(String id,String title,String description,BigDecimal price,String status) {}
    public TradeClient(RestTemplate http,@Value("${clients.user-url}") String userUrl,
            @Value("${clients.product-url}") String productUrl) {
        this.http=http;this.userUrl=userUrl.replaceAll("/+$","");this.productUrl=productUrl.replaceAll("/+$","");
    }
    public void requireUser(String id) {
        try {
            var user=http.getForObject(userUrl+"/api/users/{id}",UserView.class,id);
            if(user==null || !id.equals(user.id())) throw new ApiFailure(HttpStatus.BAD_GATEWAY,"Invalid user service response");
        } catch (RestClientException e) { throw translate(e,"User"); }
    }
    public ProductView reserve(String id) {
        try {
            var product=http.postForObject(productUrl+"/api/products/{id}/reservations",null,ProductView.class,id);
            if(product==null || !id.equals(product.id()) || product.title()==null || product.title().length()>120
                    || product.price()==null || product.price().signum()<=0 || !"RESERVED".equals(product.status())) {
                throw new ApiFailure(HttpStatus.BAD_GATEWAY,"Invalid product service response");
            }
            return product;
        } catch (RestClientException e) { throw translate(e,"Product"); }
    }
    private ApiFailure translate(RestClientException e,String dependency) {
        if(e instanceof HttpClientErrorException client) {
            if(client.getStatusCode().value()==404) return new ApiFailure(HttpStatus.NOT_FOUND,dependency+" not found");
            if(client.getStatusCode().value()==409) return new ApiFailure(HttpStatus.CONFLICT,"Product already reserved");
        }
        return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE,dependency+" service unavailable or timed out; order not created");
    }
}
