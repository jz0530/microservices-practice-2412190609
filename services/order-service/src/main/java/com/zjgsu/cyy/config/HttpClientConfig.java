package com.zjgsu.cyy.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
@Configuration
public class HttpClientConfig {
    @Bean public RestTemplate restTemplate(@Value("${clients.connect-timeout-ms}") int connect,
            @Value("${clients.read-timeout-ms}") int read) {
        if(connect<=0 || read<=0) throw new IllegalArgumentException("HTTP timeouts must be positive");
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect); factory.setReadTimeout(read);
        return new RestTemplate(factory);
    }
}
