package com.phonghub.domain;

import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceTicketTransitionsUnitTest {

    private final UUID roomId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID technicianId = UUID.randomUUID();

    @Test
    @DisplayName("Ticket starts in REPORTED status")
    void testInitialStatus() {
        MaintenanceTicket ticket = MaintenanceTicket.create(
            roomId,
            propertyId,
            null,
            "Leaking pipe",
            "Water leaking in bathroom",
            MaintenancePriority.HIGH
        );

        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertNull(ticket.getAssignedTechnicianId());
        assertFalse(ticket.isResolved());
    }

    @Test
    @DisplayName("Technician accept transitions ticket to IN_PROGRESS")
    void testAcceptTicket() {
        MaintenanceTicket ticket = MaintenanceTicket.create(
            roomId,
            propertyId,
            null,
            "Leaking pipe",
            "Water leaking in bathroom",
            MaintenancePriority.HIGH
        );

        ticket.accept(technicianId);
        assertEquals(MaintenanceStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(technicianId, ticket.getAssignedTechnicianId());
    }

    @Test
    @DisplayName("Resolving ticket requires resolution notes and transitions to RESOLVED")
    void testResolveTicket() {
        MaintenanceTicket ticket = MaintenanceTicket.create(
            roomId,
            propertyId,
            null,
            "Leaking pipe",
            "Water leaking in bathroom",
            MaintenancePriority.HIGH
        );
        ticket.accept(technicianId);

        // Cannot resolve without notes
        assertThrows(MaintenanceTicketException.class, () -> ticket.resolve("", BigDecimal.ZERO));
        assertThrows(MaintenanceTicketException.class, () -> ticket.resolve(null, BigDecimal.ZERO));

        ticket.resolve("Replaced washer seal", new BigDecimal("50000"));
        assertEquals(MaintenanceStatus.RESOLVED, ticket.getStatus());
        assertTrue(ticket.isResolved());
        assertEquals("Replaced washer seal", ticket.getResolutionNotes());
        assertEquals(new BigDecimal("50000"), ticket.getRepairCost());
    }

    @Test
    @DisplayName("Cannot assign or accept a ticket already RESOLVED")
    void testCannotModifyResolvedTicket() {
        MaintenanceTicket ticket = MaintenanceTicket.create(
            roomId,
            propertyId,
            null,
            "Leaking pipe",
            "Water leaking in bathroom",
            MaintenancePriority.HIGH
        );
        ticket.accept(technicianId);
        ticket.resolve("Done", BigDecimal.ZERO);

        UUID otherTech = UUID.randomUUID();
        assertThrows(MaintenanceTicketException.class, () -> ticket.assignTo(otherTech));
    }
}
