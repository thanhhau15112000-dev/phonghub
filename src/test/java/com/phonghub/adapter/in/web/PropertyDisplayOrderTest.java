package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyDisplayOrderTest {

    private static Property property(String name, PropertyApprovalStatus status) {
        return new Property(UUID.randomUUID(), name, "Địa chỉ", null, 0, UUID.randomUUID(), status, null,
            Instant.now(), null);
    }

    @Test
    @DisplayName("Chờ duyệt lên đầu, bị từ chối xuống cuối, cùng trạng thái thì theo tên (#362)")
    void pendingFirstRejectedLast() {
        List<Property> sorted = PropertyDisplayOrder.sort(List.of(
            property("Nhà D", PropertyApprovalStatus.REJECTED),
            property("nhà b", PropertyApprovalStatus.VERIFIED),
            property("Nhà C", PropertyApprovalStatus.PENDING),
            property("Nhà A", PropertyApprovalStatus.VERIFIED),
            property("Nhà A", PropertyApprovalStatus.REJECTED),
            property("Nhà E", PropertyApprovalStatus.PENDING)));

        assertThat(sorted).extracting(p -> p.approvalStatus() + " " + p.name()).containsExactly(
            "PENDING Nhà C", "PENDING Nhà E", "VERIFIED Nhà A", "VERIFIED nhà b", "REJECTED Nhà A", "REJECTED Nhà D");
    }
}
