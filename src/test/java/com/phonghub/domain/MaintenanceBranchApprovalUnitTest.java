package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.InvalidMaintenanceStateException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chốt nhánh hao mòn / lỗi người thuê, duyệt của chủ trọ (3 ngày) và báo giá người thuê phê duyệt (7 ngày) (#202, #203). */
class MaintenanceBranchApprovalUnitTest {

    private static final Duration OWNER_WINDOW = Duration.ofDays(3);
    private static final Duration QUOTE_WINDOW = Duration.ofDays(7);
    private static final UUID STAFF = UUID.randomUUID();

    private static MaintenanceTicket reported(MaintenanceCause cause) {
        MaintenanceTicket ticket = MaintenanceTicket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Vòi nước hỏng", "Vòi bồn rửa rò nước", MaintenancePriority.MEDIUM, cause);
        ticket.drainEvents();
        return ticket;
    }

    private static List<MaintenanceQuoteVersion.Line> parts(String unitPrice) {
        return List.of(new MaintenanceQuoteVersion.Line("Vòi nước", 1, new BigDecimal(unitPrice)));
    }

    private static MaintenanceTicket tenantFaultAwaitingQuote() {
        MaintenanceTicket ticket = reported(MaintenanceCause.TENANT_USAGE);
        ticket.confirmBranch(LiableParty.TENANT, null, STAFF, OWNER_WINDOW);
        return ticket;
    }

    // ---- nhánh A

    @Test
    @DisplayName("Wear branch: confirming puts the ticket in PENDING_OWNER_APPROVAL with a 3-day deadline")
    void wearBranchWaitsForOwner() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);

        Instant before = Instant.now();
        ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW);

        assertEquals(MaintenanceStatus.PENDING_OWNER_APPROVAL, ticket.getStatus());
        assertEquals(LiableParty.OWNER, ticket.getLiableParty());
        assertTrue(ticket.isBranchConfirmed());
        assertTrue(ticket.isWearBranch());
        assertFalse(ticket.getApprovalDueAt().isBefore(before.plus(OWNER_WINDOW)));
        assertFalse(ticket.getApprovalDueAt().isAfter(Instant.now().plus(OWNER_WINDOW)));
        assertFalse(ticket.canStartWork());
        assertEquals(MaintenanceStatus.PENDING_OWNER_APPROVAL, ticket.drainEvents().getFirst().toStatus());
    }

    @Test
    @DisplayName("A ticket cannot be assigned or started before its branch is confirmed")
    void assignRequiresConfirmedBranch() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);

        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.assign(UUID.randomUUID()));
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.accept(UUID.randomUUID()));

        ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW);
        assertThrows(MaintenanceTicketException.class, () -> ticket.assign(UUID.randomUUID()),
            "đang chờ chủ trọ duyệt thì chưa giao việc được");
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.accept(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Choosing a branch different from the reported cause (or changing it later) needs a reason")
    void changingBranchNeedsReason() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);

        assertThrows(IllegalArgumentException.class,
            () -> ticket.confirmBranch(LiableParty.TENANT, " ", STAFF, OWNER_WINDOW));
        assertThrows(IllegalArgumentException.class,
            () -> ticket.confirmBranch(LiableParty.UNDETERMINED, "x", STAFF, OWNER_WINDOW));

        ticket.confirmBranch(LiableParty.TENANT, "Kiểm tra thấy do va đập", STAFF, OWNER_WINDOW);
        assertEquals(LiableParty.TENANT, ticket.getLiableParty());
        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());

        assertThrows(IllegalArgumentException.class,
            () -> ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW));
        assertThrows(InvalidMaintenanceStateException.class,
            () -> ticket.confirmBranch(LiableParty.TENANT, "lặp", STAFF, OWNER_WINDOW));
        ticket.confirmBranch(LiableParty.OWNER, "Kiểm tra lại là hao mòn", STAFF, OWNER_WINDOW);
        assertEquals(MaintenanceStatus.PENDING_OWNER_APPROVAL, ticket.getStatus());
    }

    @Test
    @DisplayName("Owner approval returns the ticket to the normal flow; approving twice is a conflict")
    void ownerApproves() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);
        ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW);

        ticket.ownerApprove();

        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertNotNull(ticket.getOwnerApprovedAt());
        assertTrue(ticket.canStartWork());
        assertThrows(InvalidMaintenanceStateException.class, ticket::ownerApprove);
        ticket.assign(UUID.randomUUID());
        assertEquals(MaintenanceStatus.ASSIGNED, ticket.getStatus());
    }

    @Test
    @DisplayName("Owner can reject a ticket waiting for approval, with a reason")
    void ownerRejects() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);
        ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW);

        assertThrows(IllegalArgumentException.class, () -> ticket.reject(" "));
        ticket.reject("Không thuộc phạm vi");

        assertEquals(MaintenanceStatus.REJECTED, ticket.getStatus());
    }

    @Test
    @DisplayName("Past the 3-day deadline the ticket escalates once to staff/technician without the owner")
    void overdueApprovalEscalatesOnce() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);
        ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW);
        Instant due = ticket.getApprovalDueAt();

        assertFalse(ticket.escalateOverdueApproval(due.minusSeconds(1)));
        assertEquals(MaintenanceStatus.PENDING_OWNER_APPROVAL, ticket.getStatus());

        assertTrue(ticket.escalateOverdueApproval(due));
        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertNotNull(ticket.getApprovalEscalatedAt());
        assertNull(ticket.getOwnerApprovedAt());
        assertTrue(ticket.canStartWork());
        assertFalse(ticket.escalateOverdueApproval(due.plusSeconds(1)), "chạy lặp không chuyển hai lần");
        assertTrue(ticket.drainEvents().stream().anyMatch(event ->
            event.note() != null && event.note().contains("MAINTENANCE_AUTO_ESCALATED")));
    }

    @Test
    @DisplayName("Reminder is due only inside the window before the deadline and only once")
    void approvalReminderWindow() {
        MaintenanceTicket ticket = reported(MaintenanceCause.NATURAL_WEAR);
        ticket.confirmBranch(LiableParty.OWNER, null, STAFF, OWNER_WINDOW);
        Instant due = ticket.getApprovalDueAt();

        assertFalse(ticket.isApprovalReminderDue(due.minus(Duration.ofDays(2)), Duration.ofDays(1)));
        assertTrue(ticket.isApprovalReminderDue(due.minus(Duration.ofHours(12)), Duration.ofDays(1)));
        assertFalse(ticket.isApprovalReminderDue(due, Duration.ofDays(1)), "đã tới hạn thì leo thang, không nhắc");
        ticket.markApprovalReminderSent(Instant.now());
        assertFalse(ticket.isApprovalReminderDue(due.minus(Duration.ofHours(12)), Duration.ofDays(1)));
    }

    // ---- nhánh B

    @Test
    @DisplayName("Tenant-fault branch: work cannot start before the tenant approves a quote")
    void tenantFaultNeedsApprovedQuote() {
        MaintenanceTicket ticket = tenantFaultAwaitingQuote();
        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertTrue(ticket.requiresQuoteApproval());

        ticket.assign(UUID.randomUUID());
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.accept(UUID.randomUUID()));
        assertThrows(InvalidMaintenanceStateException.class, ticket::startWork);
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.resolve("Đã sửa", BigDecimal.ONE));
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.quote(BigDecimal.TEN, null, STAFF),
            "nhánh B dùng báo giá chi tiết");
    }

    @Test
    @DisplayName("Quote total is parts plus labor; it needs at least one amount and valid lines")
    void quoteValidation() {
        MaintenanceTicket ticket = tenantFaultAwaitingQuote();

        assertThrows(IllegalArgumentException.class, () -> ticket.submitQuote(List.of(), BigDecimal.ZERO, null, STAFF,
            QUOTE_WINDOW));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceQuoteVersion.Line(" ", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceQuoteVersion.Line("Vòi", 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceQuoteVersion.Line("Vòi", 1, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> ticket.submitQuote(parts("100"), new BigDecimal("-5"), null,
            STAFF, QUOTE_WINDOW));

        MaintenanceQuoteVersion version = ticket.submitQuote(
            List.of(new MaintenanceQuoteVersion.Line("Vòi", 2, new BigDecimal("150000")),
                new MaintenanceQuoteVersion.Line("Gioăng", 3, new BigDecimal("10000"))),
            new BigDecimal("100000"), "Gồm công", STAFF, QUOTE_WINDOW);
        assertEquals(0, new BigDecimal("430000").compareTo(version.totalAmount()));
        assertEquals(1, version.version());
        assertEquals(MaintenanceQuoteVersion.Status.PENDING, version.status());
    }

    @Test
    @DisplayName("Submitting a quote waits for the tenant 7 days; approving sends the ticket back to work, twice is a conflict")
    void tenantApprovesQuote() {
        MaintenanceTicket ticket = tenantFaultAwaitingQuote();
        UUID technician = UUID.randomUUID();
        ticket.assign(technician);

        ticket.submitQuote(parts("300000"), BigDecimal.ZERO, null, technician, QUOTE_WINDOW);
        assertEquals(MaintenanceStatus.PENDING_TENANT_APPROVAL, ticket.getStatus());
        assertNotNull(ticket.getQuoteDueAt());
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.submitQuote(parts("1"), BigDecimal.ZERO, null,
            technician, QUOTE_WINDOW), "đang chờ người thuê thì chưa gửi báo giá khác");

        ticket.tenantApproveQuote();
        assertEquals(MaintenanceStatus.ASSIGNED, ticket.getStatus());
        assertEquals(0, new BigDecimal("300000").compareTo(ticket.getApprovedQuoteTotal()));
        assertTrue(ticket.canStartWork());
        assertThrows(InvalidMaintenanceStateException.class, ticket::tenantApproveQuote);
        ticket.accept(technician);
        assertEquals(MaintenanceStatus.IN_PROGRESS, ticket.getStatus());
    }

    @Test
    @DisplayName("Negotiation: at most 2 rounds, then only approval or expiry remains")
    void negotiationIsLimitedToTwoRounds() {
        MaintenanceTicket ticket = tenantFaultAwaitingQuote();
        ticket.submitQuote(parts("500000"), BigDecimal.ZERO, null, STAFF, QUOTE_WINDOW);

        assertThrows(IllegalArgumentException.class, () -> ticket.tenantNegotiateQuote(" "));
        ticket.tenantNegotiateQuote("Bỏ khoản gioăng");
        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertTrue(ticket.isAwaitingRequote());
        assertNull(ticket.getQuoteDueAt());

        ticket.submitQuote(parts("450000"), BigDecimal.ZERO, null, STAFF, QUOTE_WINDOW);
        assertFalse(ticket.isAwaitingRequote());
        assertEquals(2, ticket.getQuoteVersion());
        ticket.tenantNegotiateQuote("Giảm công");
        ticket.submitQuote(parts("420000"), BigDecimal.ZERO, null, STAFF, QUOTE_WINDOW);
        assertEquals(2, ticket.getNegotiationRounds());

        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.tenantNegotiateQuote("Lần ba"));
        ticket.tenantApproveQuote();
        assertEquals(0, new BigDecimal("420000").compareTo(ticket.getApprovedQuoteTotal()));
    }

    @Test
    @DisplayName("An unapproved quote expires after 7 days into CANCELLED, exactly once")
    void quoteExpires() {
        MaintenanceTicket ticket = tenantFaultAwaitingQuote();
        ticket.submitQuote(parts("200000"), BigDecimal.ZERO, null, STAFF, QUOTE_WINDOW);
        Instant due = ticket.getQuoteDueAt();

        assertFalse(ticket.expireQuote(due.minusSeconds(1)));
        assertTrue(ticket.expireQuote(due));
        assertEquals(MaintenanceStatus.CANCELLED, ticket.getStatus());
        assertFalse(ticket.expireQuote(due.plusSeconds(1)));
        assertTrue(ticket.isWorkFinished());
    }

    @Test
    @DisplayName("Actual cost may not exceed the approved quote unless a supplementary quote is approved")
    void costIsCappedByApprovedQuote() {
        MaintenanceTicket ticket = tenantFaultAwaitingQuote();
        UUID technician = UUID.randomUUID();
        ticket.assign(technician);
        ticket.submitQuote(parts("300000"), BigDecimal.ZERO, null, technician, QUOTE_WINDOW);
        ticket.tenantApproveQuote();
        ticket.accept(technician);

        assertThrows(InvalidMaintenanceStateException.class,
            () -> ticket.resolve("Đã sửa", new BigDecimal("350000")));
        assertEquals(MaintenanceStatus.IN_PROGRESS, ticket.getStatus());

        ticket.submitQuote(parts("50000"), BigDecimal.ZERO, "Phát sinh", technician, QUOTE_WINDOW);
        assertEquals(MaintenanceStatus.PENDING_TENANT_APPROVAL, ticket.getStatus());
        assertEquals(0, ticket.getNegotiationRounds(), "báo giá bổ sung bắt đầu đợt thương lượng mới");
        ticket.tenantApproveQuote();
        assertEquals(MaintenanceStatus.IN_PROGRESS, ticket.getStatus());

        assertEquals(0, new BigDecimal("350000").compareTo(ticket.getApprovedQuoteTotal()));
        ticket.resolve("Đã sửa", new BigDecimal("350000"));
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, ticket.getStatus());
        assertEquals(0, new BigDecimal("350000").compareTo(ticket.getRepairCost()));
    }

    @Test
    @DisplayName("Tickets created by managers (no reporting tenant) keep the old direct flow")
    void managerCreatedTicketSkipsBranchGate() {
        MaintenanceTicket ticket = MaintenanceTicket.create(UUID.randomUUID(), UUID.randomUUID(), null,
            "Thay bóng", "Thay bóng hành lang", MaintenancePriority.LOW, null);
        ticket.assign(UUID.randomUUID());
        ticket.startWork();
        ticket.resolve("Xong", BigDecimal.ZERO);
        assertEquals(MaintenanceStatus.RESOLVED, ticket.getStatus());
    }
}
