package com.phonghub.application.service;

import com.phonghub.domain.model.MaintenancePriority;
import java.time.Duration;

/** Hạn xử lý mặc định theo mức ưu tiên (giờ), cấu hình qua {@code phonghub.maintenance.sla-hours.*}. */
public record MaintenanceSlaPolicy(long highHours, long mediumHours, long lowHours) {

    public static MaintenanceSlaPolicy defaults() {
        return new MaintenanceSlaPolicy(24, 72, 168);
    }

    public MaintenanceSlaPolicy {
        if (highHours <= 0 || mediumHours <= 0 || lowHours <= 0) {
            throw new IllegalArgumentException("SLA hours must be positive");
        }
    }

    public Duration forPriority(MaintenancePriority priority) {
        MaintenancePriority effective = priority == null ? MaintenancePriority.UNCLASSIFIED : priority;
        return Duration.ofHours(switch (effective) {
            // Keep the existing default ticket SLA for unclassified requests.
            case UNCLASSIFIED, MEDIUM -> mediumHours;
            case HIGH -> highHours;
            case LOW -> lowHours;
        });
    }
}
