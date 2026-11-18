package com.zjgsu.cyy;
import com.zjgsu.cyy.controller.OrderController;
import com.zjgsu.cyy.service.OrderService;
import com.zjgsu.cyy.model.TradeOrder;
import com.zjgsu.cyy.exception.ApiFailure;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderAuthorizationTests {
    final OrderService service=mock(OrderService.class);
    final OrderController controller=new OrderController(service);
    @Test void forgedBuyerRejectedBeforeSideEffects() {
        assertThrows(ApiFailure.class,()->controller.create(new OrderController.CreateOrderRequest("other","product"),"me","BUYER"));
        verifyNoInteractions(service);
    }
    @Test void missingIdentityRejected() {
        assertThrows(ApiFailure.class,()->controller.list(null,null));
        verifyNoInteractions(service);
    }
    @Test void buyerListOnlyContainsOwnOrdersAndCannotReadOther() {
        var mine=new TradeOrder("1","me","p1","book",BigDecimal.TEN);
        var other=new TradeOrder("2","other","p2","book",BigDecimal.TEN);
        when(service.list()).thenReturn(List.of(mine,other));when(service.find("2")).thenReturn(other);
        assertEquals(List.of(mine),controller.list("me","BUYER"));
        assertThrows(ApiFailure.class,()->controller.find("2","me","BUYER"));
        assertEquals(other,controller.find("2",null,"ADMIN"));
    }
}
