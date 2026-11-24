package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;

import java.util.Comparator;
import java.util.List;

/**
 * Thứ tự hiển thị danh sách nhà trọ ở trang Điều khiển và tab Nhà trọ (#362): chờ duyệt lên đầu, bị từ chối xuống
 * cuối, cùng trạng thái thì theo tên.
 */
final class PropertyDisplayOrder {

    private static final Comparator<Property> ORDER = Comparator
        .comparingInt((Property p) -> rank(p.approvalStatus()))
        .thenComparing(p -> p.name() == null ? "" : p.name(), String.CASE_INSENSITIVE_ORDER);

    private PropertyDisplayOrder() {
    }

    static List<Property> sort(List<Property> properties) {
        return properties.stream().sorted(ORDER).toList();
    }

    private static int rank(PropertyApprovalStatus status) {
        if (status == PropertyApprovalStatus.PENDING) {
            return 0;
        }
        return status == PropertyApprovalStatus.REJECTED ? 2 : 1;
    }
}
