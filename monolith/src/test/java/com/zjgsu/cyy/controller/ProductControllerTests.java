package com.zjgsu.cyy.controller;

import com.zjgsu.cyy.exception.ApiExceptionHandler;
import com.zjgsu.cyy.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ProductControllerTests {
    @org.springframework.beans.factory.annotation.Autowired private ProductService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void publishesAndQueriesProductThroughHttp() throws Exception {
        var result = mvc.perform(post("/api/products").contentType("application/json")
                .content("{\"title\":\"Textbook\",\"description\":\"Used\",\"price\":25.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Textbook"))
                .andExpect(jsonPath("$.price").value(25.50))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn();
        mvc.perform(get(result.getResponse().getHeader("Location")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Textbook"));
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsBadPriceAndReturnsNotFound() throws Exception {
        mvc.perform(post("/api/products").contentType("application/json")
                .content("{\"title\":\"Textbook\",\"price\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("商品价格必须大于 0"));
        mvc.perform(get("/api/products/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.length()").value(0));
    }
}