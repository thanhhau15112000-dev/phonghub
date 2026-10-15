package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.Receipt;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationFilter;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationReport;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationRow;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationStatus;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.PaymentReconciliationService;
import com.phonghub.domain.exception.InvoiceNotFoundException;
import com.phonghub.domain.exception.ReceiptNotAvailableException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #82: đối soát giao dịch theo phạm vi thu tiền và biên lai theo quyền xem hóa đơn. */
class PaymentReconciliationServiceUnitTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID tenantUserId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    private InMemoryPaymentRepository payments;
    private InMemoryInvoiceRepository invoices;
    private InMemoryStaffPropertyAssignmentRepository assignments;
    private CurrentUserPort currentUserPort;
    private PaymentReconciliationService service;
    private Contract c1;
    private Contract c2;
    private Tenant tenant;
    private Invoice paidInvoice;
    private Invoice partialInvoice;
    private Invoice unpaidInvoice;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        invoices = new InMemoryInvoiceRepository();
        assignments = new InMemoryStaffPropertyAssignmentRepository();
        InMemoryContractRepository contracts = new InMemoryContractRepository();
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        InMemoryRoomRepository rooms = new InMemoryRoomRepository();
        InMemoryTenantRepository tenants = new InMemoryTenantRepository();
        currentUserPort = mock(CurrentUserPort.class);
        AuthorizationService authorization = new AuthorizationService(assignments, properties, tenants, contracts);
        service = new PaymentReconciliationService(payments, invoices, contracts, properties, rooms, tenants, assignments,
            currentUserPort, authorization);

        properties.save(new Property(p1, "Nhà Một", "1 Đường A", null, 2, ownerId, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        properties.save(new Property(p2, "Nhà Hai", "2 Đường B", null, 2, otherOwnerId, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        Room r1 = rooms.save(new Room(UUID.randomUUID(), p1, "A101", 1, null, new BigDecimal("3000000"), 2, RoomStatus.OCCUPIED, Instant.now(), Instant.now()));
        Room r2 = rooms.save(new Room(UUID.randomUUID(), p2, "B201", 2, null, new BigDecimal("3000000"), 2, RoomStatus.OCCUPIED, Instant.now(), Instant.now()));
        tenant = tenants.save(Tenant.create(tenantUserId, "Nguyễn Thuê", "079201005555", "0905555555", null, null));
        c1 = contracts.save(new Contract(UUID.randomUUID(), p1, r1.getId(), tenant.id(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(10), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()));
        c2 = contracts.save(new Contract(UUID.randomUUID(), p2, r2.getId(), UUID.randomUUID(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(10), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()));

        paidInvoice = invoices.save(invoice(c1, 8, "3000000", "3000000", InvoiceStatus.PAID, "PHPAID0001"));
        partialInvoice = invoices.save(invoice(c1, 9, "3000000", "1000000", InvoiceStatus.PARTIALLY_PAID, "PHPART0001"));
        unpaidInvoice = invoices.save(invoice(c1, 10, "3000000", "0", InvoiceStatus.ISSUED, "PHNONE0001"));

        // P1: khớp đủ (2 lần), cần xem xét (dư), bỏ qua (tiền ra); P2: khớp; ngoài hợp đồng: không khớp
        payments.saveIfNew(tx(1, c1.getId(), paidInvoice.getId(), "2000000", "2000000", "0", null, PaymentStatus.SUCCESS, "2026-08-02T01:00:00Z"));
        payments.saveIfNew(tx(2, c1.getId(), paidInvoice.getId(), "1500000", "1000000", "500000", PaymentReviewReason.OVERPAID, PaymentStatus.SUCCESS, "2026-08-03T01:00:00Z"));
        payments.saveIfNew(tx(3, c1.getId(), partialInvoice.getId(), "1000000", "1000000", "0", null, PaymentStatus.SUCCESS, "2026-09-30T18:00:00Z"));
        payments.saveIfNew(tx(4, c1.getId(), null, "50000", "0", "0", null, PaymentStatus.IGNORED, "2026-09-10T01:00:00Z"));
        payments.saveIfNew(tx(5, c2.getId(), null, "3000000", "3000000", "0", null, PaymentStatus.SUCCESS, "2026-09-05T01:00:00Z"));
        payments.saveIfNew(tx(6, null, null, "700000", "0", "700000", PaymentReviewReason.UNMATCHED, PaymentStatus.SUCCESS, "2026-09-06T01:00:00Z"));
    }

    private static Invoice invoice(Contract contract, int month, String total, String paid, InvoiceStatus status, String code) {
        return new Invoice(UUID.randomUUID(), contract.getId(), contract.getRoomId(), contract.getPrimaryTenantId(), month,
            2026, new BigDecimal(total), new BigDecimal(total), new BigDecimal(paid), LocalDate.of(2026, month, 5), status,
            code, InvoiceType.RENT, null, status == InvoiceStatus.PAID ? Instant.parse("2026-08-03T01:00:00Z") : null,
            Instant.now(), Instant.now());
    }

    private static PaymentTransaction tx(long sepayId, UUID contractId, UUID invoiceId, String amount, String applied,
                                         String excess, PaymentReviewReason reason, PaymentStatus status, String at) {
        return new PaymentTransaction(UUID.randomUUID(), sepayId, "MBBank", at, "0389999999", null, "in",
            new BigDecimal(amount), null, null, "noi dung", "REF" + sepayId, null, status, contractId, invoiceId,
            Instant.parse(at), new BigDecimal(applied), new BigDecimal(excess), reason);
    }

    private void login(UUID id, UserRole role) {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(id, role + "@x", role.name(), role));
    }

    private static ReconciliationFilter all() {
        return new ReconciliationFilter(null, null, null, null);
    }

    private static List<Long> sepayIds(ReconciliationReport report) {
        return report.rows().stream().map(ReconciliationRow::sepayId).toList();
    }

    @Test
    @DisplayName("trạng thái đối soát suy ra từ kết quả khớp")
    void statusMapping() {
        assertEquals(ReconciliationStatus.MATCHED, ReconciliationStatus.of(payments.findBySepayId(1L).orElseThrow()));
        assertEquals(ReconciliationStatus.NEEDS_REVIEW, ReconciliationStatus.of(payments.findBySepayId(2L).orElseThrow()));
        assertEquals(ReconciliationStatus.IGNORED, ReconciliationStatus.of(payments.findBySepayId(4L).orElseThrow()));
        assertEquals(ReconciliationStatus.UNMATCHED, ReconciliationStatus.of(payments.findBySepayId(5L).orElseThrow()),
            "khớp hợp đồng nhưng không khớp hóa đơn");
        assertEquals(ReconciliationStatus.UNMATCHED, ReconciliationStatus.of(payments.findBySepayId(6L).orElseThrow()));
        assertEquals(ReconciliationStatus.NEEDS_REVIEW, ReconciliationStatus.of(tx(7, null, UUID.randomUUID(), "1", "0", "1",
            PaymentReviewReason.INVOICE_NOT_PAYABLE, PaymentStatus.SUCCESS, "2026-09-06T01:00:00Z")));
    }

    @Test
    @DisplayName("admin thấy mọi giao dịch kể cả chưa khớp hợp đồng; mới nhất trước; có tên nhà và phòng")
    void adminSeesEverything() {
        login(UUID.randomUUID(), UserRole.ADMIN);

        ReconciliationReport report = service.listTransactions(all());

        assertEquals(List.of(3L, 4L, 6L, 5L, 2L, 1L), sepayIds(report));
        assertEquals(2, report.count("MATCHED"));
        assertEquals(1, report.count("NEEDS_REVIEW"));
        assertEquals(2, report.count("UNMATCHED"));
        assertEquals(1, report.count("IGNORED"));
        ReconciliationRow first = report.rows().get(0);
        assertEquals("Nhà Một", first.propertyName());
        assertEquals("A101", first.roomNumber());
        assertNull(report.rows().get(2).propertyName(), "giao dịch không khớp hợp đồng không có nhà trọ");
    }

    @Test
    @DisplayName("owner chỉ thấy giao dịch hợp đồng nhà mình; lọc trạng thái, khoảng ngày theo giờ VN và tổng tiền")
    void ownerScopeAndFilters() {
        login(ownerId, UserRole.OWNER);

        assertEquals(List.of(3L, 4L, 2L, 1L), sepayIds(service.listTransactions(all())));

        ReconciliationReport matched = service.listTransactions(new ReconciliationFilter(null, ReconciliationStatus.MATCHED, null, null));
        assertEquals(List.of(3L, 1L), sepayIds(matched));
        assertEquals(1, matched.count("NEEDS_REVIEW"), "thẻ đếm không bị bộ lọc trạng thái thu hẹp");
        assertEquals(new BigDecimal("3000000"), matched.totalReceived());
        assertEquals(new BigDecimal("3000000"), matched.totalApplied());

        // 18:00 UTC 30/09 là 01/10 giờ Việt Nam
        assertEquals(List.of(3L), sepayIds(service.listTransactions(
            new ReconciliationFilter(null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))));
        assertEquals(List.of(2L, 1L), sepayIds(service.listTransactions(
            new ReconciliationFilter(p1, null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))));
        ReconciliationReport review = service.listTransactions(new ReconciliationFilter(null, ReconciliationStatus.NEEDS_REVIEW, null, null));
        assertEquals(new BigDecimal("500000"), review.totalExcess());
    }

    @Test
    @DisplayName("staff: chỉ nhà được phân công có quyền thu tiền; lọc nhà không có quyền bị 403")
    void staffNeedsCollectPermission() {
        login(staffId, UserRole.STAFF);
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staffId, p1, false, true, Instant.now()));

        assertTrue(service.listTransactions(all()).rows().isEmpty(), "chưa có quyền thu tiền thì không thấy giao dịch");
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.listTransactions(new ReconciliationFilter(p1, null, null, null)));

        assignments.deleteByUserIdAndPropertyId(staffId, p1);
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staffId, p1, true, false, Instant.now()));
        assertEquals(List.of(3L, 4L, 2L, 1L), sepayIds(service.listTransactions(all())));
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.listTransactions(new ReconciliationFilter(p2, null, null, null)));
    }

    @Test
    @DisplayName("owner lọc nhà người khác, kỹ thuật viên, người thuê và chưa đăng nhập bị từ chối")
    void forbiddenRoles() {
        login(ownerId, UserRole.OWNER);
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.listTransactions(new ReconciliationFilter(p2, null, null, null)));
        for (UserRole role : List.of(UserRole.TECHNICIAN, UserRole.TENANT)) {
            login(UUID.randomUUID(), role);
            assertThrows(UnauthorizedPropertyAccessException.class, () -> service.listTransactions(all()));
        }
        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.listTransactions(all()));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.getReceipt(paidInvoice.getId()));
        assertThrows(IllegalArgumentException.class,
            () -> new ReconciliationFilter(null, null, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)));
    }

    @Test
    @DisplayName("biên lai: người thuê của hợp đồng xem được; chỉ các lần tiền có cộng vào hóa đơn")
    void receiptForTenant() {
        login(tenantUserId, UserRole.TENANT);

        Receipt receipt = service.getReceipt(paidInvoice.getId());

        assertEquals("BL-PHPAID0001", receipt.receiptNumber());
        assertEquals("Nhà Một", receipt.propertyName());
        assertEquals("A101", receipt.roomNumber());
        assertEquals("Nguyễn Thuê", receipt.tenantName());
        assertTrue(receipt.fullyPaid());
        assertEquals(Instant.parse("2026-08-03T01:00:00Z"), receipt.paidAt());
        assertEquals(List.of(new BigDecimal("2000000"), new BigDecimal("1000000")),
            receipt.payments().stream().map(p -> p.appliedAmount()).toList());
        assertEquals("REF1", receipt.payments().get(0).referenceCode());
    }

    @Test
    @DisplayName("biên lai thanh toán một phần lấy ngày của lần tiền về gần nhất")
    void partialReceipt() {
        login(ownerId, UserRole.OWNER);

        Receipt receipt = service.getReceipt(partialInvoice.getId());

        assertFalse(receipt.fullyPaid());
        assertEquals(new BigDecimal("2000000"), receipt.remainingAmount());
        assertEquals(Instant.parse("2026-09-30T18:00:00Z"), receipt.paidAt());
    }

    @Test
    @DisplayName("biên lai: chưa thanh toán 409, không tồn tại 404, người ngoài và kỹ thuật viên 403")
    void receiptRules() {
        login(ownerId, UserRole.OWNER);
        assertThrows(ReceiptNotAvailableException.class, () -> service.getReceipt(unpaidInvoice.getId()));
        assertThrows(InvoiceNotFoundException.class, () -> service.getReceipt(UUID.randomUUID()));

        login(otherOwnerId, UserRole.OWNER);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.getReceipt(paidInvoice.getId()));
        login(UUID.randomUUID(), UserRole.TENANT);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.getReceipt(paidInvoice.getId()));
        login(UUID.randomUUID(), UserRole.TECHNICIAN);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.getReceipt(paidInvoice.getId()));
    }
}
