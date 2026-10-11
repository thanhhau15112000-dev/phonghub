package com.phonghub.domain.model;

public enum AssetEventType {
    DAMAGED,
    REPAIRED,
    REPLACED,
    LOST_REPORTED;

    public AssetStatus resultingStatus() {
        return switch (this) {
            case DAMAGED -> AssetStatus.DAMAGED;
            case REPAIRED, REPLACED -> AssetStatus.GOOD;
            case LOST_REPORTED -> AssetStatus.LOST;
        };
    }
}
