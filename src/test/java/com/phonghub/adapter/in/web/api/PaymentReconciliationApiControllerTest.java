package com.phonghub.adapter.in.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationFilter;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationReport;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationStatus;
import com.phonghub.domain.exception.ReceiptNotAvailableException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** #82: controller chỉ dựng bộ lọc và chuyển cho use case; phân quyền và lỗi nghiệp vụ đi nguyên vẹn. */
class PaymentReconciliationApiControllerTest {

    private PaymentReconciliationUseCase useCase;
    private PaymentReconciliationApiController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(PaymentReconciliationUseCase.class);
        controller = new PaymentReconciliationApiController(useCase);
    }

    @Test
    @DisplayName("chuyển đủ bộ lọc sang use case")
    void passesFilter() {
        ReconciliationReport report = new ReconciliationReport(List.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO);
        when(useCase.listTransactions(any())).thenReturn(report);
        UUID propertyId = UUID.randomUUID();

        assertSame(report, controller.reconciliation(propertyId, ReconciliationStatus.NEEDS_REVIEW,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).getBody());

        ArgumentCaptor<ReconciliationFilter> captor = ArgumentCaptor.forClass(ReconciliationFilter.class);
        verify(useCase).listTransactions(captor.capture());
        assertEquals(new ReconciliationFilter(propertyId, ReconciliationStatus.NEEDS_REVIEW, LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30)), captor.getValue());
    }

    @Test
    @DisplayName("khoảng ngày ngược bị chặn trước use case (400)")
    void invalidRange() {
        assertThrows(IllegalArgumentException.class, () -> controller.reconciliation(null, null,
            LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("lỗi phân quyền và chưa có biên lai được ném nguyên vẹn để trả 403/409")
    void errorsPropagate() {
        UUID invoiceId = UUID.randomUUID();
        when(useCase.getReceipt(invoiceId)).thenThrow(new ReceiptNotAvailableException("chưa trả"));
        when(useCase.listTransactions(any())).thenThrow(new UnauthorizedPropertyAccessException("no"));

        assertThrows(ReceiptNotAvailableException.class, () -> controller.receipt(invoiceId));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> controller.reconciliation(null, null, null, null));
    }
}
