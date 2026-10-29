package com.zjgsu.cyy;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.zjgsu.cyy.repository.OrderRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderHttpTests {
    static final String USER="11111111-1111-4111-8111-111111111111";
    static final String PRODUCT="22222222-2222-4222-8222-222222222222";
    static final AtomicReference<String> mode=new AtomicReference<>("ok");
    static final AtomicInteger reservations=new AtomicInteger();
    static final ExecutorService executor=Executors.newCachedThreadPool();
    static final HttpServer remote=startRemote();
    static HttpServer startRemote(){
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(executor);
            server.createContext("/api/users/",e->{
                if(mode.get().equals("userMissing")) reply(e,404,"{}");
                else if(mode.get().equals("userDown")) reply(e,503,"{}");
                else reply(e,200,"{\"id\":\""+USER+"\",\"name\":\"Alice\"}");
            });
            server.createContext("/api/products/",e->{
                reservations.incrementAndGet();
                if(mode.get().equals("productMissing")) reply(e,404,"{}");
                else if(mode.get().equals("productDown")) reply(e,503,"{}");
                else if(mode.get().equals("conflict")) reply(e,409,"{}");
                else if(mode.get().equals("slow")) {try{Thread.sleep(1200);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}reply(e,200,"{}");}
                else reply(e,200,"{\"id\":\""+PRODUCT+"\",\"title\":\"Book\",\"description\":\"Used\",\"price\":25.50,\"status\":\"RESERVED\"}");
            });
            server.start();return server;
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    static void reply(HttpExchange e,int status,String body) throws java.io.IOException {
        byte[] data=body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type","application/json");
        e.sendResponseHeaders(status,data.length);try(var out=e.getResponseBody()){out.write(data);}finally{e.close();}
    }
    @DynamicPropertySource static void addresses(DynamicPropertyRegistry r){
        r.add("clients.user-url",()->"http://127.0.0.1:"+remote.getAddress().getPort());
        r.add("clients.product-url",()->"http://127.0.0.1:"+remote.getAddress().getPort());
    }
    @AfterAll static void shutdown(){remote.stop(0);executor.shutdownNow();}
    @Value("${local.server.port}") int port;
    @Autowired OrderRepository repository;
    final RestTemplate http=new RestTemplate();
    String url(String path){return "http://localhost:"+port+path;}
    @BeforeEach void clear(){repository.deleteAll();mode.set("ok");reservations.set(0);}
    @AfterEach void clean(){repository.deleteAll();}
    Map<String,Object> request(){return new HashMap<>(Map.of("buyerId",USER,"productId",PRODUCT,"amount",0.01));}
    int fail(){return assertThrows(RestClientResponseException.class,()->http.postForEntity(url("/api/orders"),request(),Map.class)).getStatusCode().value();}
    @Test void realHttpFlowUsesRemotePrice(){
        var response=http.postForEntity(url("/api/orders"),request(),Map.class);
        assertEquals(201,response.getStatusCode().value());
        var body=response.getBody();assertEquals(25.5,((Number)body.get("amount")).doubleValue());
        assertEquals(USER,body.get("buyerId"));assertEquals(PRODUCT,body.get("productId"));
        assertEquals("PENDING_PAYMENT",body.get("status"));assertEquals(1,repository.count());assertEquals(1,reservations.get());
        assertEquals(body.get("id"),http.getForObject(url("/api/orders/"+body.get("id")),Map.class).get("id"));
    }
    @Test void missingUserStopsBeforeReservation(){mode.set("userMissing");assertEquals(404,fail());assertEquals(0,reservations.get());assertEquals(0,repository.count());}
    @Test void unavailableUserDoesNotWrite(){mode.set("userDown");assertEquals(503,fail());assertEquals(0,reservations.get());assertEquals(0,repository.count());}
    @Test void missingProductDoesNotWrite(){mode.set("productMissing");assertEquals(404,fail());assertEquals(0,repository.count());}
    @Test void unavailableProductDoesNotWrite(){mode.set("productDown");assertEquals(503,fail());assertEquals(0,repository.count());}
    @Test void occupiedProductDoesNotWrite(){mode.set("conflict");assertEquals(409,fail());assertEquals(0,repository.count());}
    @Test void readTimeoutDoesNotWrite(){mode.set("slow");assertEquals(503,fail());assertEquals(0,repository.count());}
    @Test void invalidIdDoesNotCallDependency(){
        var body=request();body.put("buyerId","not-a-uuid");
        var e=assertThrows(RestClientResponseException.class,()->http.postForEntity(url("/api/orders"),body,Map.class));
        assertEquals(400,e.getStatusCode().value());assertEquals(0,reservations.get());assertEquals(0,repository.count());
    }
    @Test void healthIsUp(){assertEquals("UP",http.getForObject(url("/actuator/health"),Map.class).get("status"));}
}
