package com.zjgsu.cyy;
import com.zjgsu.cyy.repository.ProductRepository;
import com.zjgsu.cyy.service.ProductService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiTests {
    @Value("${local.server.port}") int port;
    @Autowired ProductRepository repository;
    @Autowired ProductService service;
    final RestTemplate http=new RestTemplate();
    String url(String path){return "http://localhost:"+port+path;}
    @BeforeEach void clear(){repository.deleteAll();}
    @AfterEach void clean(){repository.deleteAll();}
    @Test void createsReadsAndReserves(){
        var response=http.postForEntity(url("/api/products"),Map.of("title","Book","description","Used","price",25.50),Map.class);
        assertEquals(201,response.getStatusCode().value());
        String id=(String)response.getBody().get("id");
        assertEquals("AVAILABLE",http.getForObject(url("/api/products/"+id),Map.class).get("status"));
        assertEquals("RESERVED",http.postForObject(url("/api/products/"+id+"/reservations"),null,Map.class).get("status"));
        var e=assertThrows(RestClientResponseException.class,()->http.postForObject(url("/api/products/"+id+"/reservations"),null,Map.class));
        assertEquals(409,e.getStatusCode().value());
    }
    @Test void invalidPriceDoesNotWrite(){
        for(var price:List.of(0,-1,0.001)) {
            var e=assertThrows(RestClientResponseException.class,()->http.postForObject(url("/api/products"),Map.of("title","Book","price",price),Map.class));
            assertEquals(400,e.getStatusCode().value());
        }
        assertEquals(0,repository.count());
    }
    @Test void missingProductReturns404(){
        var e=assertThrows(RestClientResponseException.class,()->http.postForObject(url("/api/products/missing/reservations"),null,Map.class));
        assertEquals(404,e.getStatusCode().value());
    }
    @Test void concurrentReservationsHaveOnlyOneWinner() throws Exception {
        String id=service.publish("Book","",new BigDecimal("25.50")).id();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> request=()->{start.await();try{return http.postForEntity(url("/api/products/"+id+"/reservations"),null,Map.class).getStatusCode().value();}
                catch(RestClientResponseException e){return e.getStatusCode().value();}};
            var a=pool.submit(request);var b=pool.submit(request);start.countDown();
            var results=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
            Collections.sort(results);assertEquals(List.of(200,409),results);
        }
        assertEquals("RESERVED",service.find(id).status());
    }
    @Test void healthIsUp(){assertEquals("UP",http.getForObject(url("/actuator/health"),Map.class).get("status"));}
}
