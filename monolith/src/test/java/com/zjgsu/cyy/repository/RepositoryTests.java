package com.zjgsu.cyy.repository;
import com.zjgsu.cyy.model.*;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class RepositoryTests {
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired EntityManager em;
    @Test void savesAndReloadsProductAndOrder() {
        var p=products.saveAndFlush(new Product(UUID.randomUUID().toString(),"教材","九成新",new BigDecimal("25.00"),"AVAILABLE"));
        var o=orders.saveAndFlush(new TradeOrder(UUID.randomUUID().toString(),p,"买家"));
        em.clear();
        assertEquals("教材",products.findById(p.id()).orElseThrow().title());
        assertEquals(p.id(),orders.findById(o.id()).orElseThrow().productId());
        assertEquals(new BigDecimal("25.00"),orders.findById(o.id()).orElseThrow().amount());
    }
    @Test void databaseRejectsSecondOrderForSameProduct() {
        var p=products.saveAndFlush(new Product(UUID.randomUUID().toString(),"教材","",BigDecimal.TEN,"AVAILABLE"));
        orders.saveAndFlush(new TradeOrder(UUID.randomUUID().toString(),p,"甲"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> orders.saveAndFlush(new TradeOrder(UUID.randomUUID().toString(),p,"乙")));
    }
}
