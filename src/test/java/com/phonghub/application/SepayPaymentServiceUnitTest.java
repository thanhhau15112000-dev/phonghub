package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.service.SepayPaymentService;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SepayPaymentServiceUnitTest {

    private InMemoryPaymentRepository paymentRepo;
    private InMemoryContractRepository contractRepo;
    private InMemoryPropertyRepository propertyRepo;
    private InMemoryNotificationRepository notifRepo;
    private InMemoryUserRepository userRepo;
    private SepayProperties sepayProperties;
    private SepayPaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepo = new InMemoryPaymentRepository();
        contractRepo = new InMemoryContractRepository();
        propertyRepo = new InMemoryPropertyRepository();
        notifRepo = new InMemoryNotificationRepository();
        userRepo = new InMemoryUserRepository();
        sepayProperties = new SepayProperties();

        service = new SepayPaymentService(
            paymentRepo,
            contractRepo,
            propertyRepo,
            notifRepo,
            userRepo,
            sepayProperties
        );
    }

    private SepayWebhookUseCase.SepayWebhookCommand createSampleCommand(Long sepayId, String content, String transferType) {
        return new SepayWebhookUseCase.SepayWebhookCommand(
            sepayId,
            "MBBank",
            "2026-10-01 23:30:00",
            "0389999999",
            null,
            null,
            content,
            transferType,
            new BigDecimal("2500000"),
            new BigDecimal("15000000"),
            "MBVCB.123456",
            "Thanh toan tien phong"
        );
    }

    @Test
    @DisplayName("Process webhook successfully without API key configured (dev mode)")
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

        UUID contractId = UUID.randomUUID();
        Contract contract = new Contract(
            contractId, propId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()
        );
        contractRepo.save(contract);

        // Content contains contractId UUID
        String content = "PHONGHUB HD " + contractId + " THANH TOAN COC";
        var command = createSampleCommand(4001L, content, "in");

        var result = service.processWebhook(null, command);

        assertTrue(result.success());
        assertNotNull(result.transaction().contractId());
        assertEquals(contractId, result.transaction().contractId());

        List<Notification> notifs = notifRepo.findAll();
        assertEquals(1, notifs.size());
        Notification notif = notifs.get(0);
        assertEquals(UserRole.OWNER, notif.targetRole());
        assertEquals(ownerId, notif.targetUserId());
    }
}
