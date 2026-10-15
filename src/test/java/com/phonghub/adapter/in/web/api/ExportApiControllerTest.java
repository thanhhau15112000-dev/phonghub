package com.phonghub.adapter.in.web.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.ExportUseCase;
import com.phonghub.application.port.in.ExportUseCase.Dataset;
import com.phonghub.application.port.in.ExportUseCase.ExportFile;
import com.phonghub.application.port.in.ExportUseCase.ExportRequest;
import com.phonghub.application.port.in.ExportUseCase.Format;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** #85: controller dựng yêu cầu xuất và trả tệp đính kèm; phân quyền ở use case. */
class ExportApiControllerTest {

    private ExportUseCase useCase;
    private ExportApiController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(ExportUseCase.class);
        controller = new ExportApiController(useCase);
    }

    @Test
    @DisplayName("chuyển đúng yêu cầu và trả tệp với tên, kiểu, không cache")
    void download() {
        UUID propertyId = UUID.randomUUID();
        ExportRequest expected = new ExportRequest(Dataset.DEBTS, Format.PDF, propertyId, YearMonth.of(2026, 1),
            YearMonth.of(2026, 6));
        when(useCase.export(expected)).thenReturn(new ExportFile("cong-no_2026-10-15.pdf", "application/pdf",
            new byte[] {9}));

        ResponseEntity<byte[]> response = controller.export("Debts", "PDF", propertyId, YearMonth.of(2026, 1),
            YearMonth.of(2026, 6));

        verify(useCase).export(expected);
        assertArrayEquals(new byte[] {9}, response.getBody());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"cong-no_2026-10-15.pdf\"",
            response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    @DisplayName("loại dữ liệu/định dạng sai bị chặn trước use case; lỗi phân quyền đi nguyên vẹn")
    void errors() {
        assertThrows(IllegalArgumentException.class, () -> controller.export("salaries", "xlsx", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> controller.export("invoices", "csv", null, null, null));
        verifyNoInteractions(useCase);

        when(useCase.export(any())).thenThrow(new UnauthorizedPropertyAccessException("no"));
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> controller.export("contracts", "xlsx", null, null, null));
    }
}
