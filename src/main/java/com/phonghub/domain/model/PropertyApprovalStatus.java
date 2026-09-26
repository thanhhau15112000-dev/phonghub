package com.phonghub.domain.model;

public enum PropertyApprovalStatus {
    PENDING,
    VERIFIED,
    REJECTED;

    public static PropertyApprovalStatus fromString(String val) {
        if (val == null || val.isBlank()) {
            return VERIFIED;
        }
        if ("APPROVED".equalsIgnoreCase(val.trim())) {
            return VERIFIED;
        }
        return PropertyApprovalStatus.valueOf(val.trim().toUpperCase());
    }
}
