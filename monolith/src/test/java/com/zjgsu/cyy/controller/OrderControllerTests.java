package com.zjgsu.cyy.controller;
import com.zjgsu.cyy.exception.ApiExceptionHandler;
import com.zjgsu.cyy.service.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class OrderControllerTests {
    @Autowired ProductService products;
    @Autowired OrderService orders;
    @Test void createsOrderThenRejectsDuplicate() throws Exception {
        var p=products.publish("Book","",new BigDecimal("25.00"));
        var mvc=MockMvcBuilders.standaloneSetup(new OrderController(orders))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        String body="{\"productId\":\""+p.id()+"\",\"buyerName\":\"Alice\"}";
        var result=mvc.perform(post("/api/orders").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(25.00)).andReturn();
        mvc.perform(get(result.getResponse().getHeader("Location"))).andExpect(status().isOk());
        mvc.perform(post("/api/orders").contentType("application/json").content(body))
            .andExpect(status().isConflict());
    }
}
