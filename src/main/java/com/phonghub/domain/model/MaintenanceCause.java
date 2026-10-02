package com.phonghub.domain.model;

/**
 * Nguyên nhân sự cố do người báo khai. Chỉ là lời khai ban đầu: bên chịu phí chính thức
 * ({@link LiableParty}) do kỹ thuật viên / quản lý xác nhận khi xử lý.
 */
public enum MaintenanceCause {
    NATURAL_WEAR(LiableParty.OWNER),
    INFRASTRUCTURE(LiableParty.OWNER),
    TENANT_USAGE(LiableParty.TENANT),
    UNKNOWN(LiableParty.UNDETERMINED);

    private final LiableParty defaultLiableParty;

    MaintenanceCause(LiableParty defaultLiableParty) {
        this.defaultLiableParty = defaultLiableParty;
    }

    public LiableParty defaultLiableParty() {
        return defaultLiableParty;
    }
}
