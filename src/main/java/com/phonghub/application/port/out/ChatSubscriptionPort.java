package com.phonghub.application.port.out;

import java.util.UUID;

/** Cung cấp cơ chế đăng ký và duy trì kết nối luồng sự kiện chat (SSE). */
public interface ChatSubscriptionPort {
    <T> T subscribe(UUID userId);
    default void sendHeartbeat() {}
}

