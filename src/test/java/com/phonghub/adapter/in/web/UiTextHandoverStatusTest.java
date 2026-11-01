package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.phonghub.domain.model.HandoverStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #292: trang biên bản không còn hiện tên kỹ thuật như PENDING_TENANT. */
class UiTextHandoverStatusTest {

    private final UiText uiText = UiText.INSTANCE;

    @Test
    @DisplayName("mọi trạng thái biên bản đều có nhãn tiếng Việt, không lộ mã gốc")
    void everyStatusHasVietnameseLabel() {
        assertEquals("Chờ người thuê xác nhận", uiText.handoverStatus(HandoverStatus.PENDING_TENANT));
        for (HandoverStatus status : HandoverStatus.values()) {
            assertFalse(uiText.handoverStatus(status).contains("_"), status.name());
        }
        assertEquals("Chưa xác định", uiText.handoverStatus(null));
    }
}
