package com.zjgsu.cyy.service;

import com.zjgsu.cyy.model.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import com.zjgsu.cyy.repository.ProductRepository;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

@Service
public class ProductService {
    private final ProductRepository products;
    public ProductService(ProductRepository products) { this.products=products; }

    @Transactional
    public Product publish(String title, String description, BigDecimal price) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("商品价格必须大于 0");
        }
        if (title.trim().length() > 120 || (description != null && description.length() > 2000)
                || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("商品名称、描述或价格超出允许范围");
        }
        BigDecimal amount;
        try {
            amount = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("商品价格最多保留两位小数");
        }
        Product product = new Product(UUID.randomUUID().toString(), title.trim(),
                description == null ? "" : description.trim(), amount, "AVAILABLE");
        products.save(product);
        return product;
    }

    @Transactional
    public Product reserve(String id) {
        var product=products.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Product not found"));
        if (!"AVAILABLE".equals(product.status())) {
            throw new com.zjgsu.cyy.exception.ApiFailure(org.springframework.http.HttpStatus.CONFLICT,"Product already reserved");
        }
        product.reserve();
        return products.saveAndFlush(product);
    }

    @Transactional(readOnly=true)
    public List<Product> list() {
        return products.findAll().stream().sorted(Comparator.comparing(Product::id)).toList();
    }
    @Transactional(readOnly=true)
    public Product find(String id) {
        return products.findById(id).orElseThrow(() -> new NoSuchElementException("商品不存在"));
    }
}
