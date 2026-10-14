package com.zjgsu.cyy.service;

import com.zjgsu.cyy.exception.ProductUnavailableException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.transaction.annotation.Transactional
class OrderServiceTests {
    @org.springframework.beans.factory.annotation.Autowired private ProductService products;
    @org.springframework.beans.factory.annotation.Autowired private OrderService orders;

    @Test
    void orderReservesProductAndCopiesPrice() {
        var product = products.publish("教材", "", new BigDecimal("25.50"));
        var order = orders.create(product.id(), "买家甲");
        assertEquals(product.id(), order.productId());
        assertEquals(product.price(), order.amount());
        assertEquals("PENDING_PAYMENT", order.status());
        assertEquals("RESERVED", products.find(product.id()).status());
        assertEquals(order, orders.find(order.id()));
    }

    @Test
    void secondBuyerCannotBuyReservedProduct() {
        var product = products.publish("教材", "", BigDecimal.TEN);
        var first = orders.create(product.id(), "买家甲");
        assertThrows(ProductUnavailableException.class,
                () -> orders.create(product.id(), "买家乙"));
        assertEquals(first, orders.find(first.id()));
        assertEquals("RESERVED", products.find(product.id()).status());
    }

    @Test
    void invalidBuyerDoesNotReserveProduct() {
        var product = products.publish("教材", "", BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> orders.create(product.id(), " "));
        assertEquals("AVAILABLE", products.find(product.id()).status());
    }

    @Test
    void missingProductOrOrderIsRejected() {
        assertThrows(NoSuchElementException.class, () -> orders.create("missing", "买家甲"));
        assertThrows(NoSuchElementException.class, () -> orders.find("missing"));
    }
}