package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaintenanceTicketFeeUnitTest {

    private MaintenanceTicket ticket(MaintenanceCause cause) {
        MaintenanceTicket t = MaintenanceTicket.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Hỏng vòi", "Vòi nước bị hỏng", MaintenancePriority.MEDIUM, cause
        );
        t.accept(UUID.randomUUID());
        return t;
    }

    @Test
    @DisplayName("Default liable party follows the reported cause; no cause means the owner pays")
    void defaultLiableParty() {
        assertEquals(LiableParty.OWNER, ticket(MaintenanceCause.NATURAL_WEAR).getLiableParty());
        assertEquals(LiableParty.OWNER, ticket(MaintenanceCause.INFRASTRUCTURE).getLiableParty());
        assertEquals(LiableParty.TENANT, ticket(MaintenanceCause.TENANT_USAGE).getLiableParty());
        assertEquals(LiableParty.UNDETERMINED, ticket(MaintenanceCause.UNKNOWN).getLiableParty());
        assertEquals(LiableParty.OWNER, ticket(null).getLiableParty());
    }

    @Test
    @DisplayName("Tenant-liable ticket with cost > 0 goes to AWAITING_PAYMENT, then RESOLVED once paid")
    void tenantLiableResolveAndPay() {
        MaintenanceTicket t = ticket(MaintenanceCause.TENANT_USAGE);
        t.resolve("Thay vòi", new BigDecimal("250000"));
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, t.getStatus());
        assertFalse(t.isResolved());
        assertTrue(t.isWorkFinished());

        t.markFeePaid();
        assertEquals(MaintenanceStatus.RESOLVED, t.getStatus());
        assertThrows(MaintenanceTicketException.class, t::markFeePaid);
    }

    @Test
    @DisplayName("Owner-liable or zero-cost tickets resolve immediately")
    void ownerLiableOrFreeResolvesImmediately() {
        MaintenanceTicket owner = ticket(MaintenanceCause.NATURAL_WEAR);
        owner.resolve("Thay vòi", new BigDecimal("250000"));
        assertEquals(MaintenanceStatus.RESOLVED, owner.getStatus());

        MaintenanceTicket free = ticket(MaintenanceCause.TENANT_USAGE);
        free.resolve("Chỉ siết lại", BigDecimal.ZERO);
        assertEquals(MaintenanceStatus.RESOLVED, free.getStatus());
    }

    @Test
    @DisplayName("Undetermined liable party cannot record a cost until it is decided")
    void undeterminedWithCostIsRejected() {
        MaintenanceTicket t = ticket(MaintenanceCause.UNKNOWN);
        assertThrows(MaintenanceTicketException.class, () -> t.resolve("Sửa", new BigDecimal("100000")));
        assertEquals(MaintenanceStatus.IN_PROGRESS, t.getStatus());

        t.changeLiableParty(LiableParty.TENANT);
        t.resolve("Sửa", new BigDecimal("100000"));
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, t.getStatus());
    }

    @Test
    @DisplayName("Liable party cannot change after the work is finished; waiving moves the cost to the owner")
    void liablePartyLockedAndWaive() {
        MaintenanceTicket t = ticket(MaintenanceCause.TENANT_USAGE);
        t.resolve("Thay vòi", new BigDecimal("250000"));
        assertThrows(MaintenanceTicketException.class, () -> t.changeLiableParty(LiableParty.OWNER));

        t.waiveFee();
        assertEquals(MaintenanceStatus.RESOLVED, t.getStatus());
        assertEquals(LiableParty.OWNER, t.getLiableParty());
        assertThrows(MaintenanceTicketException.class, t::waiveFee);
    }

    @Test
    @DisplayName("A ticket awaiting payment cannot be re-assigned or re-accepted")
    void awaitingPaymentCannotBeReopened() {
        MaintenanceTicket t = ticket(MaintenanceCause.TENANT_USAGE);
        t.resolve("Thay vòi", new BigDecimal("250000"));
        assertThrows(MaintenanceTicketException.class, () -> t.accept(UUID.randomUUID()));
    }
}
