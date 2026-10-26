package com.phonghub.adapter.in.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutPolicy;
import com.phonghub.domain.exception.InvalidMaintenanceStateException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** #83: các endpoint báo giá/xác nhận chỉ chuyển cho use case; phân quyền và trạng thái do use case quyết định. */
class MaintenanceQuoteApiControllerTest {

    private final UUID ticketId = UUID.randomUUID();
    private MaintenanceUseCase useCase;
    private MaintenanceApiController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(MaintenanceUseCase.class);
        controller = new MaintenanceApiController(useCase, MaintenanceAssignmentTimeoutPolicy.defaults());
    }

    private MaintenanceTicket ticket(MaintenanceStatus status) {
        return new MaintenanceTicket(ticketId, UUID.randomUUID(), UUID.randomUUID(), null, null, "Hỏng", "mô tả",
            MaintenancePriority.LOW, status, BigDecimal.ZERO, null, null, LiableParty.OWNER, null, null, null,
            Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("lưu báo giá và trả lại báo giá đã lưu")
    void quote() {
        MaintenanceTicket quoted = ticket(MaintenanceStatus.IN_PROGRESS);
        UUID actor = UUID.randomUUID();
        quoted.quote(new BigDecimal("250000"), "Thay vòi", actor);
        when(useCase.quoteTicket(ticketId, new BigDecimal("250000"), "Thay vòi")).thenReturn(quoted);

        var body = controller.quoteTicket(ticketId,
            new MaintenanceApiController.QuoteRequest(new BigDecimal("250000"), "Thay vòi")).getBody();

        assertEquals(ticketId, body.ticketId());
        assertEquals(new BigDecimal("250000"), body.amount());
        assertEquals(actor, body.quotedBy());
    }

    @Test
    @DisplayName("xem báo giá: 204 khi chưa có hoặc bị ẩn, 200 khi được xem")
    void getQuote() {
        when(useCase.visibleQuote(ticketId)).thenReturn(null);
        ResponseEntity<MaintenanceApiController.QuoteResponse> hidden = controller.getQuote(ticketId);
        assertEquals(HttpStatus.NO_CONTENT, hidden.getStatusCode());
        assertNull(hidden.getBody());

        when(useCase.visibleQuote(ticketId)).thenReturn(
            new MaintenanceTicket.Quote(BigDecimal.TEN, null, null, Instant.now()));
        assertEquals(BigDecimal.TEN, controller.getQuote(ticketId).getBody().amount());
    }

    @Test
    @DisplayName("xác nhận và phản hồi chưa đạt chuyển đúng tham số; lỗi 403/409 đi nguyên vẹn")
    void verifyAndReopen() {
        when(useCase.verifyTicket(ticketId)).thenReturn(ticket(MaintenanceStatus.VERIFIED));
        when(useCase.reopenTicket(ticketId, "còn rỉ")).thenReturn(ticket(MaintenanceStatus.ASSIGNED));

        assertEquals(MaintenanceStatus.VERIFIED, controller.verifyTicket(ticketId).getBody().status());
        assertEquals(MaintenanceStatus.ASSIGNED, controller.reopenTicket(ticketId,
            new MaintenanceApiController.ReopenTicketRequest("còn rỉ")).getBody().status());
        verify(useCase).reopenTicket(ticketId, "còn rỉ");

        UUID other = UUID.randomUUID();
        when(useCase.verifyTicket(other)).thenThrow(new InvalidMaintenanceStateException("chưa xong"));
        when(useCase.visibleQuote(other)).thenThrow(new UnauthorizedPropertyAccessException("no"));
        assertThrows(InvalidMaintenanceStateException.class, () -> controller.verifyTicket(other));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> controller.getQuote(other));
    }
}
