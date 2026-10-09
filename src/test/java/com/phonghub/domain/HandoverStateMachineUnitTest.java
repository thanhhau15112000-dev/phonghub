package com.phonghub.domain;

import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandoverStateMachineUnitTest {

    private final UUID actor = UUID.randomUUID();

    private Handover draft() {
        return Handover.draft(UUID.randomUUID(), HandoverType.CHECK_IN, new BigDecimal("1420.5"), new BigDecimal("35"),
            "Phòng sạch", "2 chìa khóa", actor);
    }

    private Handover pendingTenant() {
        return draft().send("terms", "rules", "rulesHash", actor);
    }

    private Handover pendingApproval() {
        return pendingTenant().tenantConfirm(actor);
    }

    @Test
    void happyPathWalksEveryStep() {
        Handover sent = draft().send("terms", "rules", "rulesHash", actor);
        assertEquals(HandoverStatus.PENDING_TENANT, sent.status());
        assertNotNull(sent.sentAt());
        assertEquals("terms", sent.contractTermsHash());

        Handover confirmed = sent.tenantConfirm(actor);
        assertEquals(HandoverStatus.PENDING_APPROVAL, confirmed.status());
        assertTrue(confirmed.confirmedByTenant());
        assertNotNull(confirmed.confirmedAt());

        Handover approved = confirmed.approve(actor);
        assertEquals(HandoverStatus.APPROVED, approved.status());
        assertEquals(actor, approved.approvedBy());
        assertNotNull(approved.approvedAt());
    }

    @Test
    void steps_cannotBeSkipped() {
        assertThrows(InvalidHandoverStateException.class, () -> draft().tenantConfirm(actor));
        assertThrows(InvalidHandoverStateException.class, () -> draft().approve(actor));
        assertThrows(InvalidHandoverStateException.class, () -> draft().reject("x", actor));
        assertThrows(InvalidHandoverStateException.class, () -> draft().reopen(actor));
        assertThrows(InvalidHandoverStateException.class, () -> pendingTenant().approve(actor));
        assertThrows(InvalidHandoverStateException.class, () -> pendingTenant().send("t", "r", "h", actor));
        assertThrows(InvalidHandoverStateException.class, () -> pendingApproval().tenantConfirm(actor));
        assertThrows(InvalidHandoverStateException.class, () -> pendingApproval().send("t", "r", "h", actor));
    }

    @Test
    void approvedIsFinal() {
        Handover approved = pendingApproval().approve(actor);

        assertThrows(InvalidHandoverStateException.class, () -> approved.approve(actor));
        assertThrows(InvalidHandoverStateException.class, () -> approved.reject("late", actor));
        assertThrows(InvalidHandoverStateException.class, () -> approved.reopen(actor));
        assertThrows(InvalidHandoverStateException.class,
            () -> approved.edit(BigDecimal.ONE, BigDecimal.ONE, "x", null, actor));
    }

    @Test
    void rejectNeedsAReasonAndReopenReturnsToDraft() {
        assertThrows(IllegalArgumentException.class, () -> pendingApproval().reject(" ", actor));

        Handover rejected = pendingApproval().reject("Thiếu ảnh đồng hồ", actor);
        assertEquals(HandoverStatus.REJECTED, rejected.status());
        assertEquals("Thiếu ảnh đồng hồ", rejected.rejectionReason());

        Handover reopened = rejected.reopen(actor);
        assertEquals(HandoverStatus.DRAFT, reopened.status());
        assertFalse(reopened.confirmedByTenant());
        assertNull(reopened.rejectionReason());
        assertNull(reopened.contractTermsHash());
        assertEquals(HandoverStatus.PENDING_TENANT, reopened.send("t2", null, null, actor).status());
    }

    @Test
    void editIsOnlyAllowedInDraft() {
        Handover edited = draft().edit(new BigDecimal("1500"), new BigDecimal("40"), "Mới", " ", actor);

        assertEquals(new BigDecimal("1500"), edited.initialElectricMeter());
        assertNull(edited.keysProvided());
        assertThrows(InvalidHandoverStateException.class,
            () -> pendingTenant().edit(BigDecimal.ONE, BigDecimal.ONE, "x", null, actor));
    }

    @Test
    void invalidInputIsRejected() {
        UUID contract = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> Handover.draft(contract, HandoverType.CHECK_IN, new BigDecimal("-1"), BigDecimal.ONE, "x", null, actor));
        assertThrows(IllegalArgumentException.class,
            () -> Handover.draft(contract, HandoverType.CHECK_IN, BigDecimal.ONE, null, "x", null, actor));
        assertThrows(IllegalArgumentException.class,
            () -> Handover.draft(contract, HandoverType.CHECK_IN, BigDecimal.ONE, BigDecimal.ONE, " ", null, actor));
    }
}
