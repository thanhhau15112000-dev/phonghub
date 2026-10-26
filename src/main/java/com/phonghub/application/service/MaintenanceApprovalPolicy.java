package com.phonghub.application.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Hạn duyệt trước khi sửa phiếu bảo trì: chủ trọ duyệt nhánh hao mòn trong 3 ngày (#202), người thuê phê duyệt báo giá
 * lỗi do mình trong 7 ngày (#203), cùng khoảng nhắc trước hạn.
 */
public record MaintenanceApprovalPolicy(
    Duration ownerApprovalWindow,
    Duration quoteApprovalWindow,
    Duration ownerReminderBefore,
    Duration quoteReminderBefore
) {

    public static MaintenanceApprovalPolicy defaults() {
        return new MaintenanceApprovalPolicy(Duration.ofDays(3), Duration.ofDays(7), Duration.ofDays(1),
            Duration.ofDays(2));
    }

    public MaintenanceApprovalPolicy {
        requirePositive(ownerApprovalWindow, "owner-approval-window");
        requirePositive(quoteApprovalWindow, "quote-approval-window");
        requirePositive(ownerReminderBefore, "owner-reminder-before");
        requirePositive(quoteReminderBefore, "quote-reminder-before");
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
