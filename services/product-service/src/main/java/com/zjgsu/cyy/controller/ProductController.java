package com.zjgsu.cyy.controller;

import com.zjgsu.cyy.dto.CreateProductRequest;
import com.zjgsu.cyy.model.Product;
import com.zjgsu.cyy.dto.ProductResponse;
import com.zjgsu.cyy.service.ProductService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> publish(@RequestBody CreateProductRequest request) {
        Product product = service.publish(request.title(), request.description(), request.price());
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(ProductResponse.from(product));
    }

    @PostMapping("/{id}/reservations")
    public ProductResponse reserve(@PathVariable("id") String id) {
        return ProductResponse.from(service.reserve(id));
    }

    @GetMapping
    public List<ProductResponse> list() {
        return service.list().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse detail(@PathVariable("id") String id) {
        return ProductResponse.from(service.find(id));
    }
}
