package com.phonghub.application.service;

import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SepayPaymentService implements SepayWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(SepayPaymentService.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})"
    );

    private final PaymentRepositoryPort paymentRepository;
    private final ContractRepositoryPort contractRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final UserRepositoryPort userRepository;
    private final SepayProperties sepayProperties;

    public SepayPaymentService(
        PaymentRepositoryPort paymentRepository,
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        NotificationRepositoryPort notificationRepository,
        UserRepositoryPort userRepository,
        SepayProperties sepayProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.propertyRepository = propertyRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.sepayProperties = sepayProperties;
    }

    @Override
    public WebhookProcessResult processWebhook(String authorizationHeader, SepayWebhookCommand command) {
        validateAuthorization(authorizationHeader);

        // 1. Idempotency check: Tránh ghi nhận nhiều lần cùng một giao dịch từ SePay
        if (command.id() != null && paymentRepository.existsBySepayId(command.id())) {
            log.info("Sepay webhook: Giao dịch ID {} đã tồn tại trước đó (idempotent ignore)", command.id());
            PaymentTransaction existing = paymentRepository.findBySepayId(command.id())
                .orElse(null);
            return new WebhookProcessResult(true, "Giao dịch đã được ghi nhận trước đó", existing);
        }

        // 2. Phân loại biến động số dư
        PaymentStatus status = PaymentStatus.SUCCESS;
        if (command.transferType() != null && "out".equalsIgnoreCase(command.transferType().trim())) {
            status = PaymentStatus.IGNORED;
        }

        // 3. Khớp nối hợp đồng nếu nội dung chứa mã UUID hợp đồng
        UUID contractId = extractAndMatchContractId(command.content(), command.code());

        // 4. Lưu vết giao dịch thanh toán
        PaymentTransaction tx = new PaymentTransaction(
            UUID.randomUUID(),
            command.id(),
            command.gateway(),
            command.transactionDate(),
            command.accountNumber(),
            command.subAccount(),
            command.transferType(),
            command.transferAmount(),
            command.accumulated(),
            command.code(),
            command.content(),
            command.referenceCode(),
            command.description(),
            status,
            contractId,
            Instant.now()
        );
        PaymentTransaction saved = paymentRepository.save(tx);
        log.info("Sepay webhook: Đã lưu giao dịch ID={}, SePayId={}, Số tiền={}, Status={}",
            saved.id(), saved.sepayId(), saved.transferAmount(), saved.status());

        // 5. Gửi thông báo đến người quản lý nếu là tiền vào thành công
        if (status == PaymentStatus.SUCCESS) {
            sendPaymentNotification(saved);
        }

        return new WebhookProcessResult(true, "Xử lý giao dịch SePay thành công", saved);
    }

    @Override
    public List<PaymentTransaction> getAllTransactions() {
        return paymentRepository.findAll();
    }

    @Override
    public Optional<PaymentTransaction> getTransactionById(UUID id) {
        return paymentRepository.findById(id);
    }

    private void validateAuthorization(String authorizationHeader) {
        String configuredKey = sepayProperties != null ? sepayProperties.getWebhookApiKey() : null;
        if (configuredKey == null || configuredKey.isBlank()) {
            return;
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedWebhookException("Thiếu header Authorization xác thực SePay webhook");
        }

        String raw = authorizationHeader.trim();
        if (raw.equals(configuredKey)) {
            return;
        }

        String[] parts = raw.split("\\s+", 2);
        if (parts.length == 2 && (parts[0].equalsIgnoreCase("Apikey") || parts[0].equalsIgnoreCase("Bearer"))) {
            if (parts[1].trim().equals(configuredKey)) {
                return;
            }
        }

        throw new UnauthorizedWebhookException("API Key hoặc chữ ký webhook SePay không chính xác");
    }

    private UUID extractAndMatchContractId(String content, String code) {
        UUID matched = tryParseContractUuid(code);
        if (matched != null) {
            return matched;
        }
        return tryParseContractUuid(content);
    }

    private UUID tryParseContractUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = UUID_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                UUID candidate = UUID.fromString(matcher.group(1));
                if (contractRepository.findById(candidate).isPresent()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Continue searching
            }
        }
        return null;
    }

    private void sendPaymentNotification(PaymentTransaction tx) {
        UUID targetUserId = null;
        String targetUsername = null;
        String targetFullName = null;
        UserRole targetRole = UserRole.ADMIN;

        if (tx.contractId() != null) {
            Optional<Contract> contractOpt = contractRepository.findById(tx.contractId());
            if (contractOpt.isPresent()) {
                Contract contract = contractOpt.get();
                Optional<Property> propertyOpt = propertyRepository.findById(contract.getPropertyId());
                if (propertyOpt.isPresent() && propertyOpt.get().ownerId() != null) {
                    Optional<User> ownerOpt = userRepository.findById(propertyOpt.get().ownerId());
                    if (ownerOpt.isPresent()) {
                        User owner = ownerOpt.get();
                        targetUserId = owner.id();
                        targetUsername = owner.username();
                        targetFullName = owner.fullName();
                        targetRole = UserRole.OWNER;
                    }
                }
            }
        }

        BigDecimal amount = tx.transferAmount() != null ? tx.transferAmount() : BigDecimal.ZERO;
        String gateway = tx.gateway() != null ? tx.gateway() : "Ngân hàng";
        String acc = tx.accountNumber() != null ? tx.accountNumber() : "N/A";
        String content = tx.content() != null ? tx.content() : "";

        String title = "Nhận thanh toán SePay: " + String.format("%,.0f đ", amount);
        String message = String.format(
            "Đã nhận %,.0f VNĐ từ cổng %s (STK: %s). Nội dung: %s",
            amount, gateway, acc, content
        );

        Notification notification = Notification.createPaymentReceived(
            title,
            message,
            targetUserId,
            targetUsername,
            targetFullName,
            targetRole
        );
        notificationRepository.save(notification);
    }
}
