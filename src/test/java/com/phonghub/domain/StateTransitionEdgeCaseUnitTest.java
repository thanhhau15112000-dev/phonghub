package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.InvalidContractStateException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Issue #159 + #94: Kiểm thử ma trận chuyển trạng thái toàn diện (State Transition Matrix).
 * Rà soát tất cả các cặp trạng thái hợp lệ và không hợp lệ bằng @ParameterizedTest.
 */
class StateTransitionEdgeCaseUnitTest {

    // =========================================================================
    // 1. ROOM STATE TRANSITIONS MATRIX
    // =========================================================================

    @ParameterizedTest(name = "Room {0} -> {1} hợp lệ? {2}")
    @CsvSource({
        // Từ AVAILABLE
        "AVAILABLE, RESERVED, true",
        "AVAILABLE, OCCUPIED, true",
        "AVAILABLE, MAINTENANCE, true",
        "AVAILABLE, AVAILABLE, true",
        // Từ RESERVED
        "RESERVED, AVAILABLE, true",
        "RESERVED, OCCUPIED, true",
        "RESERVED, MAINTENANCE, false",
        "RESERVED, RESERVED, true",
        // Từ OCCUPIED
        "OCCUPIED, AVAILABLE, false",
        "OCCUPIED, MAINTENANCE, true",
        "OCCUPIED, RESERVED, false",
        "OCCUPIED, OCCUPIED, true",
        // Từ MAINTENANCE
        "MAINTENANCE, AVAILABLE, true",
        "MAINTENANCE, OCCUPIED, false",
        "MAINTENANCE, RESERVED, false",
        "MAINTENANCE, MAINTENANCE, true"
    })
    @DisplayName("Ma trận chuyển trạng thái phòng: canTransitionTo")
    void testRoomTransitionMatrix(RoomStatus from, RoomStatus to, boolean expectedValid) {
        Room room = new Room(
            UUID.randomUUID(), UUID.randomUUID(), "P101", 1,
            new BigDecimal("25.0"), new BigDecimal("3500000"), 2,
            from, Instant.now(), Instant.now()
        );

        assertEquals(expectedValid, room.canTransitionTo(to));
    }

    @Test
    @DisplayName("Room: Chuyển trạng thái không hợp lệ ném ngoại lệ InvalidRoomStateException")
    void testInvalidRoomTransitionsThrow() {
        Room reservedRoom = new Room(
            UUID.randomUUID(), UUID.randomUUID(), "P101", 1,
            new BigDecimal("25.0"), new BigDecimal("3500000"), 2,
            RoomStatus.RESERVED, Instant.now(), Instant.now()
        );

        assertThrows(InvalidRoomStateException.class, () -> reservedRoom.transitionTo(RoomStatus.MAINTENANCE));

        Room maintenanceRoom = new Room(
            UUID.randomUUID(), UUID.randomUUID(), "P102", 1,
            new BigDecimal("25.0"), new BigDecimal("3500000"), 2,
            RoomStatus.MAINTENANCE, Instant.now(), Instant.now()
        );

        assertThrows(InvalidRoomStateException.class, () -> maintenanceRoom.transitionTo(RoomStatus.OCCUPIED));
        assertThrows(InvalidRoomStateException.class, () -> maintenanceRoom.transitionTo(RoomStatus.RESERVED));
    }

    // =========================================================================
    // 2. CONTRACT STATE TRANSITIONS
    // =========================================================================

    @Test
    @DisplayName("Contract: Hủy hợp đồng DRAFT -> CANCELLED thành công")
    void testCancelDraftContract() {
        Contract contract = Contract.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"),
            LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1), 5
        );
        assertEquals(ContractStatus.DRAFT, contract.getStatus());

        contract.cancelDraft();
        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertFalse(contract.isActive());
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"ACTIVE", "TERMINATED", "CANCELLED"})
    @DisplayName("Contract: Không thể hủy hoặc sửa điều khoản khi không phải DRAFT")
    void testCannotCancelOrUpdateNonDraftContract(ContractStatus nonDraftStatus) {
        Contract contract = new Contract(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"),
            LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1), 5,
            nonDraftStatus, List.of(), Instant.now(), Instant.now()
        );

        assertThrows(InvalidContractStateException.class, contract::cancelDraft);
        assertThrows(InvalidContractStateException.class, () -> contract.updateDraftTerms(
            new BigDecimal("2000000"), new BigDecimal("3000000"),
            LocalDate.of(2026, 11, 5), LocalDate.of(2027, 11, 5), 10
        ));
    }

    // =========================================================================
    // 3. MAINTENANCE TICKET TRANSITIONS
    // =========================================================================

    @Test
    @DisplayName("Maintenance: Vòng đời chuyển trạng thái hợp lệ REPORTED -> IN_PROGRESS -> RESOLVED")
    void testMaintenanceValidLifecycle() {
        UUID technicianId = UUID.randomUUID();
        MaintenanceTicket ticket = MaintenanceTicket.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Bóng đèn chập chờn", "Cần thay bóng LED mới", MaintenancePriority.LOW
        );

        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertFalse(ticket.isResolved());

        ticket.accept(technicianId);
        assertEquals(MaintenanceStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(technicianId, ticket.getAssignedTechnicianId());

        ticket.resolve("Đã thay bóng LED", new BigDecimal("50000"));
        assertEquals(MaintenanceStatus.RESOLVED, ticket.getStatus());
        assertTrue(ticket.isResolved());
    }

    @Test
    @DisplayName("Maintenance: Phiếu đã RESOLVED từ chối tiếp tục thay đổi trạng thái")
    void testResolvedTicketCannotBeChanged() {
        UUID technicianId = UUID.randomUUID();
        MaintenanceTicket ticket = MaintenanceTicket.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Bóng đèn chập chờn", "Cần thay bóng LED mới", MaintenancePriority.LOW
        );
        ticket.accept(technicianId);
        ticket.resolve("Đã xong", BigDecimal.ZERO);

        assertThrows(MaintenanceTicketException.class, () -> ticket.accept(UUID.randomUUID()));
        assertThrows(MaintenanceTicketException.class, () -> ticket.resolve("Thử lại", BigDecimal.ZERO));
    }

    // =========================================================================
    // 4. INVOICE TERMINAL STATE GUARDS
    // =========================================================================

    @Test
    @DisplayName("Invoice: Hóa đơn đã PAID hoặc VOIDED từ chối nhận thêm thanh toán")
    void testPaidOrVoidedInvoiceRejectsPayment() {
        Contract contract = new Contract(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()
        );

        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 2), "PHINV00001");
        // Thanh toán toàn bộ -> PAID
        invoice.applyPayment(new BigDecimal("3500000"), Instant.now());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());

        // Cố thanh toán thêm vào hóa đơn đã PAID
        assertThrows(DomainException.class, () -> invoice.applyPayment(new BigDecimal("100000"), Instant.now()));

        // Hóa đơn bị hủy (VOIDED)
        Invoice voidedInvoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 3), "PHINV00002");
        voidedInvoice.voidInvoice();
        assertEquals(InvoiceStatus.VOIDED, voidedInvoice.getStatus());

        // Cố thanh toán vào hóa đơn đã VOIDED ném DomainException
        assertThrows(DomainException.class, () -> voidedInvoice.applyPayment(new BigDecimal("500000"), Instant.now()));
    }
}
