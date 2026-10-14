package com.zjgsu.cyy.service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ProductServiceTests {
    @org.springframework.beans.factory.annotation.Autowired private ProductService service;

    @Test
    void publishedProductCanBeListedAndFound() {
        var product = service.publish(" 二手教材 ", " 九成新 ", new BigDecimal("25"));
        assertEquals("二手教材", product.title());
        assertEquals("九成新", product.description());
        assertEquals(new BigDecimal("25.00"), product.price());
        assertEquals("AVAILABLE", product.status());
        assertEquals(product.id(), service.find(product.id()).id());
        assertEquals(1, service.list().size());
    }

    @Test
    void invalidPriceDoesNotPublishAnything() {
        for (BigDecimal price : new BigDecimal[] {null, BigDecimal.ZERO,
                new BigDecimal("-1"), new BigDecimal("0.001")}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.publish("教材", "", price));
        }
        assertTrue(service.list().isEmpty());
    }

    @Test
    void blankTitleDoesNotPublishAnything() {
        assertThrows(IllegalArgumentException.class,
                () -> service.publish(" ", "", BigDecimal.ONE));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void missingProductIsRejected() {
        assertThrows(NoSuchElementException.class, () -> service.find("missing"));
    }
}