package com.phonghub.application.service;

import com.phonghub.domain.model.MaintenancePriority;
import java.time.Duration;

/** Hạn xử lý mặc định theo mức ưu tiên (giờ), cấu hình qua {@code phonghub.maintenance.sla-hours.*}. */
public record MaintenanceSlaPolicy(long urgentHours, long highHours, long mediumHours, long lowHours) {

    public static MaintenanceSlaPolicy defaults() {
        return new MaintenanceSlaPolicy(4, 24, 72, 168);
    }

    public MaintenanceSlaPolicy {
        if (urgentHours <= 0 || highHours <= 0 || mediumHours <= 0 || lowHours <= 0) {
            throw new IllegalArgumentException("SLA hours must be positive");
        }
    }

    public Duration forPriority(MaintenancePriority priority) {
        MaintenancePriority effective = priority == null ? MaintenancePriority.MEDIUM : priority;
        return Duration.ofHours(switch (effective) {
            case URGENT -> urgentHours;
            case HIGH -> highHours;
            case MEDIUM -> mediumHours;
            case LOW -> lowHours;
        });
    }
}
