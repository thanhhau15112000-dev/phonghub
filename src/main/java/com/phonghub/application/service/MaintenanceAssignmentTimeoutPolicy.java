package com.phonghub.application.service;

import java.time.Duration;
import java.util.Objects;

/** Configurable wait periods for technician assignment and understaffed auto-cancellation (#223). */
public record MaintenanceAssignmentTimeoutPolicy(Duration assignmentTimeout, Duration understaffedCancelAfter) {

    public static MaintenanceAssignmentTimeoutPolicy defaults() {
        return new MaintenanceAssignmentTimeoutPolicy(Duration.ofHours(72), Duration.ofHours(24));
    }

    public MaintenanceAssignmentTimeoutPolicy {
        requirePositive(assignmentTimeout, "assignment-timeout");
        requirePositive(understaffedCancelAfter, "understaffed-cancel-after");
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
