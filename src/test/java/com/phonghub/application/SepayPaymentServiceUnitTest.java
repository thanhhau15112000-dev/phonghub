package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.SepayPaymentService;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SepayPaymentServiceUnitTest {

    private InMemoryPaymentRepository paymentRepo;
    private InMemoryInvoiceRepository invoiceRepo;
    private InMemoryMaintenanceTicketRepository ticketRepo;
    private InMemoryContractRepository contractRepo;
    private InMemoryPropertyRepository propertyRepo;
    private InMemoryNotificationRepository notifRepo;
    private InMemoryUserRepository userRepo;
    private LocalDemoAuthenticationAdapter currentUserPort;
    private SepayProperties sepayProperties;
    private SepayPaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepo = new InMemoryPaymentRepository();
        invoiceRepo = new InMemoryInvoiceRepository();
        ticketRepo = new InMemoryMaintenanceTicketRepository();
        contractRepo = new InMemoryContractRepository();
        propertyRepo = new InMemoryPropertyRepository();
        notifRepo = new InMemoryNotificationRepository();
        userRepo = new InMemoryUserRepository();
        currentUserPort = new LocalDemoAuthenticationAdapter();
        currentUserPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);
        sepayProperties = new SepayProperties();
        sepayProperties.setAllowUnsignedWebhook(true);

        AuthorizationService authorizationService = new AuthorizationService(
            new InMemoryStaffPropertyAssignmentRepository(),
            propertyRepo,
            new InMemoryTenantRepository(),
            contractRepo
        );

        service = new SepayPaymentService(
            paymentRepo,
            invoiceRepo,
            ticketRepo,
            contractRepo,
            propertyRepo,
            notifRepo,
            userRepo,
            currentUserPort,
            authorizationService,
            sepayProperties
        );
    }

    private SepayWebhookUseCase.SepayWebhookCommand createSampleCommand(Long sepayId, String content, String transferType) {
        return createCommand(sepayId, null, content, transferType, new BigDecimal("2500000"));
    }

    private SepayWebhookUseCase.SepayWebhookCommand createCommand(
        Long sepayId, String code, String content, String transferType, BigDecimal amount
    ) {
        return new SepayWebhookUseCase.SepayWebhookCommand(
            sepayId,
            "MBBank",
            "2026-10-01 23:30:00",
            "0389999999",
            null,
            code,
            content,
            transferType,
            amount,
            new BigDecimal("15000000"),
            "MBVCB.123456",
            "Thanh toan tien phong"
        );
    }

    private Contract saveActiveContract(UUID propertyId, BigDecimal rent) {
        Contract contract = new Contract(
            UUID.randomUUID(), propertyId, UUID.randomUUID(), UUID.randomUUID(),
            rent, rent,
            LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()
        );
        contractRepo.save(contract);
        return contract;
    }

    private Invoice saveInvoice(Contract contract, String code) {
        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.now(), code);
        invoiceRepo.save(invoice);
        return invoice;
    }

    @Test
    @DisplayName("Process webhook without API key when unsigned webhooks are explicitly allowed (local/test)")
    void testProcessWebhookWithoutApiKeyConfigured() {
        var command = createSampleCommand(1001L, "Thanh toan tien phong 101", "in");

        var result = service.processWebhook(null, command);

        assertTrue(result.success());
        assertNotNull(result.transaction());
        assertEquals(1001L, result.transaction().sepayId());
        assertEquals(PaymentStatus.SUCCESS, result.transaction().status());
        assertEquals(1, paymentRepo.findAll().size());

        List<Notification> notifs = notifRepo.findAll();
        assertEquals(1, notifs.size());
        assertEquals(NotificationType.PAYMENT_RECEIVED, notifs.get(0).type());
        assertEquals(UserRole.ADMIN, notifs.get(0).targetRole());
    }

    @Test
    @DisplayName("Fail closed: missing API key configuration rejects webhook unless unsigned is allowed")
    void testMissingApiKeyRejectsByDefault() {
        sepayProperties.setAllowUnsignedWebhook(false);
        var command = createSampleCommand(1005L, "Tien phong", "in");

        assertThrows(UnauthorizedWebhookException.class, () -> service.processWebhook(null, command));
        assertThrows(UnauthorizedWebhookException.class, () -> service.processWebhook("Apikey anything", command));
        assertEquals(0, paymentRepo.findAll().size());
    }

    @Test
    @DisplayName("Enforce API key verification when key is configured")
    void testApiKeyAuthentication() {
        sepayProperties.setWebhookApiKey("secret-sepay-token");

        var command = createSampleCommand(1002L, "Tien coc", "in");

        // 1. Missing header -> throws UnauthorizedWebhookException
        assertThrows(UnauthorizedWebhookException.class, () ->
            service.processWebhook(null, command)
        );

        // 2. Wrong key -> throws UnauthorizedWebhookException
        assertThrows(UnauthorizedWebhookException.class, () ->
            service.processWebhook("Apikey wrong-secret", command)
        );

        // 3. Valid 'Apikey <key>' -> passes
        var res1 = service.processWebhook("Apikey secret-sepay-token", command);
        assertTrue(res1.success());

        // 4. Valid 'Bearer <key>' -> passes
        var cmd2 = createSampleCommand(1003L, "Tien coc 2", "in");
        var res2 = service.processWebhook("Bearer secret-sepay-token", cmd2);
        assertTrue(res2.success());

        // 5. Valid raw '<key>' -> passes
        var cmd3 = createSampleCommand(1004L, "Tien coc 3", "in");
        var res3 = service.processWebhook("secret-sepay-token", cmd3);
        assertTrue(res3.success());
    }

    @Test
    @DisplayName("Webhook without SePay transaction id is rejected")
    void testMissingSepayIdRejected() {
        var command = createSampleCommand(null, "Tien phong", "in");
        assertThrows(IllegalArgumentException.class, () -> service.processWebhook(null, command));
        assertEquals(0, paymentRepo.findAll().size());
    }

    @Test
    @DisplayName("Idempotency: duplicate sepay ID returns existing transaction without saving duplicate")
    void testIdempotency() {
        var command = createSampleCommand(2001L, "Tien phong thang 10", "in");

        var firstCall = service.processWebhook(null, command);
        assertTrue(firstCall.success());
        assertEquals(1, paymentRepo.findAll().size());
        assertEquals(1, notifRepo.findAll().size());

        // Duplicate call with same sepay ID
        var secondCall = service.processWebhook(null, command);
        assertTrue(secondCall.success());
        assertEquals("Giao dịch đã được ghi nhận trước đó", secondCall.message());
        assertEquals(firstCall.transaction().id(), secondCall.transaction().id());

        // DB still has only 1 record, no duplicate notifications sent
        assertEquals(1, paymentRepo.findAll().size());
        assertEquals(1, notifRepo.findAll().size());
    }

    @Test
    @DisplayName("Money out (transferType='out') is saved as IGNORED and does not trigger notification")
    void testMoneyOutIsIgnored() {
        var command = createSampleCommand(3001L, "Chuyen tien chi phi", "out");

        var result = service.processWebhook(null, command);

        assertTrue(result.success());
        assertEquals(PaymentStatus.IGNORED, result.transaction().status());
        assertEquals(1, paymentRepo.findAll().size());
        assertEquals(0, notifRepo.findAll().size());
    }

    @Test
    @DisplayName("Matching contract UUID in transfer content links contract and notifies property owner")
    void testMatchContractAndNotifyOwner() {
        UUID ownerId = UUID.randomUUID();
        User owner = new User(ownerId, "owner1@local", "Chủ Trọ 1", "0901234567", UserRole.OWNER, User.UserStatus.ACTIVE, Instant.now());
        userRepo.save(owner);

        UUID propId = UUID.randomUUID();
        Property property = new Property(propId, "Nhà Trọ Hoa Hồng", "123 Đường A", "Mô tả", 10, ownerId, PropertyApprovalStatus.VERIFIED, null, Instant.now());
        propertyRepo.save(property);

        Contract contract = saveActiveContract(propId, new BigDecimal("3000000"));
        UUID contractId = contract.getId();

        // Content contains contractId UUID
        String content = "PHONGHUB HD " + contractId + " THANH TOAN COC";
        var command = createSampleCommand(4001L, content, "in");

        var result = service.processWebhook(null, command);

        assertTrue(result.success());
        assertNotNull(result.transaction().contractId());
        assertEquals(contractId, result.transaction().contractId());
        assertNull(result.transaction().invoiceId());

        List<Notification> notifs = notifRepo.findAll();
        assertEquals(1, notifs.size());
        Notification notif = notifs.get(0);
        assertEquals(UserRole.OWNER, notif.targetRole());
        assertEquals(ownerId, notif.targetUserId());
    }

    @Test
    @DisplayName("Payment code in content (spaces stripped, lower case) settles the invoice in full")
    void testPaymentCodeSettlesInvoice() {
        Contract contract = saveActiveContract(UUID.randomUUID(), new BigDecimal("3000000"));
        Invoice invoice = saveInvoice(contract, "PHAB23CD45");

        var command = createCommand(5001L, null, "MBVCB123 phongHUBphab23cd45 chuyen tien", "in", new BigDecimal("3000000"));
        var result = service.processWebhook(null, command);

        assertEquals(invoice.getId(), result.transaction().invoiceId());
        assertEquals(contract.getId(), result.transaction().contractId());
        Invoice stored = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, stored.getStatus());
        assertEquals(0, stored.remainingAmount().signum());
        assertNotNull(stored.getPaidAt());
    }

    @Test
    @DisplayName("SePay code field takes precedence; partial transfers accumulate until fully paid")
    void testPartialPaymentsAccumulate() {
        Contract contract = saveActiveContract(UUID.randomUUID(), new BigDecimal("3000000"));
        Invoice invoice = saveInvoice(contract, "PHQWERTY23");

        service.processWebhook(null, createCommand(6001L, "PHQWERTY23", "tien phong dot 1", "in", new BigDecimal("1000000")));
        Invoice afterFirst = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PARTIALLY_PAID, afterFirst.getStatus());
        assertEquals(0, new BigDecimal("2000000").compareTo(afterFirst.remainingAmount()));

        service.processWebhook(null, createCommand(6002L, "PHQWERTY23", "tien phong dot 2", "in", new BigDecimal("2000000")));
        Invoice afterSecond = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, afterSecond.getStatus());
    }

    @Test
    @DisplayName("Duplicate webhook delivery does not credit the invoice twice")
    void testDuplicateDoesNotDoubleCredit() {
        Contract contract = saveActiveContract(UUID.randomUUID(), new BigDecimal("3000000"));
        Invoice invoice = saveInvoice(contract, "PHZXCVBN23");

        var command = createCommand(7001L, null, "PHZXCVBN23", "in", new BigDecimal("1000000"));
        service.processWebhook(null, command);
        service.processWebhook(null, command);

        Invoice stored = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000000").compareTo(stored.getPaidAmount()));
        assertEquals(1, paymentRepo.findAll().size());
    }

    @Test
    @DisplayName("Money out containing a payment code does not credit the invoice")
    void testMoneyOutDoesNotCreditInvoice() {
        Contract contract = saveActiveContract(UUID.randomUUID(), new BigDecimal("3000000"));
        Invoice invoice = saveInvoice(contract, "PHMNBVCX23");

        service.processWebhook(null, createCommand(8001L, null, "PHMNBVCX23", "out", new BigDecimal("3000000")));

        Invoice stored = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ISSUED, stored.getStatus());
        assertEquals(0, stored.getPaidAmount().signum());
    }

    private static BigDecimal vnd(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    @DisplayName("Overpayment: the invoice is capped at its total, the excess is recorded and the transaction needs review")
    void testOverpaymentIsSplit() {
        Contract contract = saveActiveContract(UUID.randomUUID(), vnd("3000000"));
        Invoice invoice = saveInvoice(contract, "PHOVERPAY2");

        var tx = service.processWebhook(null,
            createCommand(9101L, "PHOVERPAY2", "tien phong", "in", vnd("3500000"))).transaction();

        Invoice stored = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, stored.getStatus());
        assertEquals(0, vnd("3000000").compareTo(stored.getPaidAmount()));
        assertEquals(0, vnd("3000000").compareTo(tx.appliedAmount()));
        assertEquals(0, vnd("500000").compareTo(tx.excessAmount()));
        assertEquals(PaymentReviewReason.OVERPAID, tx.reviewReason());
        assertTrue(tx.needsReview());
        assertTrue(notifRepo.findAll().stream().anyMatch(n -> n.message().contains("chuyển dư")));
    }

    @Test
    @DisplayName("Overpaying the remainder of a partly paid invoice only credits what is owed")
    void testOverpaymentAfterPartial() {
        Contract contract = saveActiveContract(UUID.randomUUID(), vnd("3000000"));
        Invoice invoice = saveInvoice(contract, "PHPARTOVER");
        service.processWebhook(null, createCommand(9201L, "PHPARTOVER", "dot 1", "in", vnd("1000000")));

        var tx = service.processWebhook(null,
            createCommand(9202L, "PHPARTOVER", "dot 2", "in", vnd("2500000"))).transaction();

        assertEquals(0, vnd("2000000").compareTo(tx.appliedAmount()));
        assertEquals(0, vnd("500000").compareTo(tx.excessAmount()));
        Invoice stored = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, stored.getStatus());
        assertEquals(0, vnd("3000000").compareTo(stored.getPaidAmount()));
    }

    @Test
    @DisplayName("Underpayment and exact payment need no review")
    void testUnderAndExactNeedNoReview() {
        Contract contract = saveActiveContract(UUID.randomUUID(), vnd("3000000"));
        saveInvoice(contract, "PHUNDER234");

        var under = service.processWebhook(null,
            createCommand(9301L, "PHUNDER234", "thieu", "in", vnd("1000000"))).transaction();
        var exact = service.processWebhook(null,
            createCommand(9302L, "PHUNDER234", "du", "in", vnd("2000000"))).transaction();

        assertEquals(0, under.excessAmount().signum());
        assertEquals(0, vnd("1000000").compareTo(under.appliedAmount()));
        assertEquals(0, exact.excessAmount().signum());
        assertFalse(under.needsReview() || exact.needsReview());
    }

    @Test
    @DisplayName("A second transfer to an invoice that is already paid is recorded as unapplied and flagged")
    void testPaymentToSettledInvoiceIsFlagged() {
        Contract contract = saveActiveContract(UUID.randomUUID(), vnd("3000000"));
        Invoice invoice = saveInvoice(contract, "PHSETTLED2");
        service.processWebhook(null, createCommand(9401L, "PHSETTLED2", "du", "in", vnd("3000000")));

        var tx = service.processWebhook(null,
            createCommand(9402L, "PHSETTLED2", "chuyen nham lan 2", "in", vnd("3000000"))).transaction();

        assertEquals(PaymentReviewReason.INVOICE_NOT_PAYABLE, tx.reviewReason());
        assertEquals(0, tx.appliedAmount().signum());
        assertEquals(0, vnd("3000000").compareTo(tx.excessAmount()));
        assertEquals(0, vnd("3000000").compareTo(invoiceRepo.findById(invoice.getId()).orElseThrow().getPaidAmount()));
    }

    @Test
    @DisplayName("A transfer matching no invoice is kept, flagged as unmatched and credits nothing")
    void testUnmatchedTransferIsFlagged() {
        var tx = service.processWebhook(null,
            createCommand(9501L, null, "chuyen khoan khong ma", "in", vnd("700000"))).transaction();

        assertEquals(PaymentReviewReason.UNMATCHED, tx.reviewReason());
        assertEquals(0, vnd("700000").compareTo(tx.excessAmount()));
        assertEquals(PaymentStatus.SUCCESS, tx.status());
    }

    @Test
    @DisplayName("A redelivered overpayment webhook is neither applied nor recorded a second time")
    void testDuplicateOverpaymentNotRepeated() {
        Contract contract = saveActiveContract(UUID.randomUUID(), vnd("3000000"));
        Invoice invoice = saveInvoice(contract, "PHDUPOVER2");
        var command = createCommand(9601L, "PHDUPOVER2", "du", "in", vnd("4000000"));

        service.processWebhook(null, command);
        var again = service.processWebhook(null, command);

        assertEquals(1, paymentRepo.findAll().size());
        assertEquals(0, vnd("1000000").compareTo(again.transaction().excessAmount()));
        assertEquals(0, vnd("3000000").compareTo(invoiceRepo.findById(invoice.getId()).orElseThrow().getPaidAmount()));
    }
}
