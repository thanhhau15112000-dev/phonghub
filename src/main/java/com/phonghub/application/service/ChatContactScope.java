package com.phonghub.application.service;

import com.phonghub.application.port.out.CurrentUser;
import java.util.Set;
import java.util.UUID;

/**
 * Phạm vi người mà một tài khoản được liên hệ qua chat riêng (#345): vừa quyết định danh bạ hiện ra, vừa quyết định
 * có tạo được cuộc trò chuyện riêng với ai.
 */
public interface ChatContactScope {

    /** Mã tài khoản mà {@code viewer} được liên hệ, không gồm chính họ. {@code null} nghĩa là không giới hạn. */
    Set<UUID> reachableUserIds(CurrentUser viewer);

    /** Không giới hạn: chỉ dành cho test đơn vị của luồng chat không liên quan tới phạm vi. */
    static ChatContactScope unrestricted() {
        return viewer -> null;
    }
}
