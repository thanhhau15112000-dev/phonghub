package com.phonghub.domain.model;

public enum ContractStatus {
    DRAFT,
    ACTIVE,
    EXPIRED,
    TERMINATED,
    /** Hợp đồng nháp bị hủy trước khi kích hoạt (#89); giữ lại bản ghi thay vì xóa cứng. */
    CANCELLED
}
