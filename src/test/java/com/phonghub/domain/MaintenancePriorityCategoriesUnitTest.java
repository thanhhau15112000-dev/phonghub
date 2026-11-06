package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.phonghub.application.service.MaintenanceSlaPolicy;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenancePriorityCategoriesUnitTest {
    @Test
    void defaultsToUnclassifiedAndPreservesDefaultSla() {
        MaintenanceTicket ticket = MaintenanceTicket.create(UUID.randomUUID(), UUID.randomUUID(), null,
            "Sự cố", "Mô tả", null);

        assertEquals(MaintenancePriority.UNCLASSIFIED, ticket.getPriority());
        assertEquals(Duration.ofHours(72), MaintenanceSlaPolicy.defaults().forPriority(ticket.getPriority()));
        assertIterableEquals(List.of("UNCLASSIFIED", "LOW", "MEDIUM", "HIGH"),
            java.util.Arrays.stream(MaintenancePriority.values()).map(Enum::name).toList());
    }
}
