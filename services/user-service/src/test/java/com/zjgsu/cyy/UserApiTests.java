package com.zjgsu.cyy;
import com.zjgsu.cyy.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository repository;
    final RestTemplate http=new RestTemplate();
    String url(String path){return "http://localhost:"+port+path;}
    @BeforeEach void clear(){repository.deleteAll();}
    @AfterEach void clean(){repository.deleteAll();}
    @Test void createsAndReadsUser() {
        var response=http.postForEntity(url("/api/users"),Map.of("name"," Alice "),Map.class);
        assertEquals(201,response.getStatusCode().value());
        var user=http.getForObject(url("/api/users/"+response.getBody().get("id")),Map.class);
        assertEquals("Alice",user.get("name")); assertEquals(1,repository.count());
    }
    @Test void rejectsBlankNameWithoutWriting(){
        var error=assertThrows(RestClientResponseException.class,()->http.postForEntity(url("/api/users"),Map.of("name"," "),Map.class));
        assertEquals(400,error.getStatusCode().value());assertEquals(0,repository.count());
    }
    @Test void missingUserReturns404(){
        var error=assertThrows(RestClientResponseException.class,()->http.getForObject(url("/api/users/missing"),Map.class));
        assertEquals(404,error.getStatusCode().value());
    }
    @Test void healthIsUp(){assertEquals("UP",http.getForObject(url("/actuator/health"),Map.class).get("status"));}
}
