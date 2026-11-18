package com.zjgsu.cyy;

import com.zjgsu.cyy.client.ProductView;
import com.zjgsu.cyy.client.TradeClient;
import com.zjgsu.cyy.exception.ApiFailure;
import com.zjgsu.cyy.model.TradeOrder;
import com.zjgsu.cyy.repository.OrderRepository;
import com.zjgsu.cyy.service.OrderService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTests {
    static final String USER = "11111111-1111-4111-8111-111111111111";
    static final String PRODUCT = "22222222-2222-4222-8222-222222222222";

    private TradeClient client;
    private OrderRepository orders;
    private OrderService service;

    @BeforeEach
    void setUp() {
        client = mock(TradeClient.class);
        orders = mock(OrderRepository.class);
        service = new OrderService(client, orders);
    }

    @Test
    void successUsesRemotePriceAndSavesOrder() {
        when(client.reserve(PRODUCT)).thenReturn(
                new ProductView(PRODUCT, "Book", "Used", new BigDecimal("25.50"), "RESERVED"));
        when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TradeOrder order = service.create(USER, PRODUCT);

        assertEquals(USER, order.getBuyerId());
        assertEquals(PRODUCT, order.getProductId());
        assertEquals(0, order.getAmount().compareTo(new BigDecimal("25.50")));
        assertEquals("PENDING_PAYMENT", order.getStatus());
        verify(client).requireUser(USER);
        verify(client).reserve(PRODUCT);
        verify(orders).saveAndFlush(any());
    }

    @Test
    void userFailureDoesNotReserveOrSave() {
        doThrow(new ApiFailure(HttpStatus.NOT_FOUND, "User not found"))
                .when(client).requireUser(USER);

        ApiFailure error = assertThrows(ApiFailure.class, () -> service.create(USER, PRODUCT));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(client, never()).reserve(any());
        verifyNoInteractions(orders);
    }

    @Test
    void productFailureDoesNotSave() {
        when(client.reserve(PRODUCT)).thenThrow(
                new ApiFailure(HttpStatus.CONFLICT, "Product already reserved"));

        ApiFailure error = assertThrows(ApiFailure.class, () -> service.create(USER, PRODUCT));

        assertEquals(HttpStatus.CONFLICT, error.status());
        verifyNoInteractions(orders);
    }

    @Test
    void productReadTimeoutDoesNotSaveAndIsCalledOnce() {
        when(client.reserve(PRODUCT)).thenThrow(
                new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable or timed out"));

        ApiFailure error = assertThrows(ApiFailure.class, () -> service.create(USER, PRODUCT));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        verify(client, times(1)).reserve(PRODUCT);
        verifyNoInteractions(orders);
    }

    @Test
    void dependencyFailureDoesNotSave() {
        doThrow(new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable"))
                .when(client).requireUser(USER);

        ApiFailure error = assertThrows(ApiFailure.class, () -> service.create(USER, PRODUCT));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        verifyNoInteractions(orders);
    }

    @Test
    void invalidUuidDoesNotCallDependencies() {
        assertThrows(IllegalArgumentException.class, () -> service.create("not-a-uuid", PRODUCT));
        verifyNoInteractions(client, orders);
    }
}
