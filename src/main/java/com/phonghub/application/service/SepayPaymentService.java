package com.phonghub.application.service;

import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.PaymentCode;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final InvoiceRepositoryPort invoiceRepository;
    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final ContractRepositoryPort contractRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final UserRepositoryPort userRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final SepayProperties sepayProperties;

    public SepayPaymentService(
        PaymentRepositoryPort paymentRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        NotificationRepositoryPort notificationRepository,
        UserRepositoryPort userRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        SepayProperties sepayProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
        this.contractRepository = contractRepository;
        this.propertyRepository = propertyRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.sepayProperties = sepayProperties;
    }

    @Override
    public WebhookProcessResult processWebhook(String authorizationHeader, SepayWebhookCommand command) {
        validateAuthorization(authorizationHeader);
        if (command.id() == null) {
            throw new IllegalArgumentException("Thiếu id giao dịch SePay");
        }

        // 1. Idempotency (đường nhanh): SePay gửi lại webhook khi chưa nhận 200
        Optional<PaymentTransaction> existing = paymentRepository.findBySepayId(command.id());
        if (existing.isPresent()) {
            log.info("Sepay webhook: Giao dịch ID {} đã tồn tại trước đó (idempotent ignore)", command.id());
            return new WebhookProcessResult(true, "Giao dịch đã được ghi nhận trước đó", existing.get());
        }

        // 2. Phân loại biến động số dư
        PaymentStatus status = PaymentStatus.SUCCESS;
        if (command.transferType() != null && "out".equalsIgnoreCase(command.transferType().trim())) {
            status = PaymentStatus.IGNORED;
        }

        // 3. Khớp kỳ thanh toán theo mã nội dung CK; nếu không có thì khớp hợp đồng theo UUID
        Invoice invoice = null;
        UUID contractId = null;
        if (status == PaymentStatus.SUCCESS) {
            invoice = matchInvoice(command.code(), command.content());
            contractId = invoice != null
                ? invoice.getContractId()
                : extractAndMatchContractId(command.content(), command.code());
        }

        // 4. Lưu vết giao dịch. Ràng buộc UNIQUE(sepay_id) chặn trường hợp hai request trùng chạy đồng thời.
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
            invoice != null ? invoice.getId() : null,
            Instant.now()
        );
        if (!paymentRepository.saveIfNew(tx)) {
            PaymentTransaction duplicate = paymentRepository.findBySepayId(command.id()).orElse(null);
            return new WebhookProcessResult(true, "Giao dịch đã được ghi nhận trước đó", duplicate);
        }
        log.info("Sepay webhook: Đã lưu giao dịch ID={}, SePayId={}, Số tiền={}, Status={}, Invoice={}",
            tx.id(), tx.sepayId(), tx.transferAmount(), tx.status(), tx.invoiceId());

        // 5. Cộng tiền vào kỳ thanh toán (chỉ khi giao dịch mới và kỳ còn nợ)
        if (invoice != null && invoice.isPayable() && tx.transferAmount().signum() > 0) {
            invoice.applyPayment(tx.transferAmount(), tx.createdAt());
            invoiceRepository.save(invoice);
            completeMaintenanceTicketIfPaid(invoice);
        }

        // 6. Gửi thông báo đến người quản lý nếu là tiền vào thành công
        if (status == PaymentStatus.SUCCESS) {
            sendPaymentNotification(tx, invoice);
        }

        return new WebhookProcessResult(true, "Xử lý giao dịch SePay thành công", tx);
    }

    @Override
    public List<PaymentTransaction> getAllTransactions() {
        authorizationService.assertAdmin(currentUserPort.getCurrentUser());
        return paymentRepository.findAll();
    }

    @Override
    public Optional<PaymentTransaction> getTransactionById(UUID id) {
        authorizationService.assertAdmin(currentUserPort.getCurrentUser());
        return paymentRepository.findById(id);
    }

    private void validateAuthorization(String authorizationHeader) {
        String configuredKey = sepayProperties != null ? sepayProperties.getWebhookApiKey() : null;
        if (configuredKey == null || configuredKey.isBlank()) {
            if (sepayProperties != null && sepayProperties.isAllowUnsignedWebhook()) {
                return;
            }
            throw new UnauthorizedWebhookException("Chưa cấu hình SEPAY_WEBHOOK_API_KEY, từ chối webhook");
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedWebhookException("Thiếu header Authorization xác thực SePay webhook");
        }

        String raw = authorizationHeader.trim();
        if (constantTimeEquals(raw, configuredKey)) {
            return;
        }

        String[] parts = raw.split("\\s+", 2);
        if (parts.length == 2 && (parts[0].equalsIgnoreCase("Apikey") || parts[0].equalsIgnoreCase("Bearer"))) {
            if (constantTimeEquals(parts[1].trim(), configuredKey)) {
                return;
            }
        }

        throw new UnauthorizedWebhookException("API Key hoặc chữ ký webhook SePay không chính xác");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Hóa đơn phí sửa chữa đã trả đủ: phiếu bảo trì đang chờ thanh toán chuyển sang hoàn tất. */
    private void completeMaintenanceTicketIfPaid(Invoice invoice) {
        if (invoice.getType() != InvoiceType.MAINTENANCE
            || invoice.getTicketId() == null
            || invoice.getStatus() != InvoiceStatus.PAID) {
            return;
        }
        ticketRepository.findById(invoice.getTicketId())
            .filter(ticket -> ticket.getStatus() == MaintenanceStatus.AWAITING_PAYMENT)
            .ifPresent(ticket -> {
                ticket.markFeePaid();
                ticketRepository.save(ticket);
            });
    }

    private Invoice matchInvoice(String code, String content) {
        for (String text : new String[] { code, content }) {
            for (String candidate : PaymentCode.findCandidates(text)) {
                Optional<Invoice> invoice = invoiceRepository.findByPaymentCodeForUpdate(candidate);
                if (invoice.isPresent()) {
                    return invoice.get();
                }
            }
        }
        return null;
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

    private void sendPaymentNotification(PaymentTransaction tx, Invoice invoice) {
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
        if (invoice != null) {
            String label = invoice.getType() == InvoiceType.MAINTENANCE
                ? "Phí sửa chữa"
                : String.format("Kỳ %02d/%d", invoice.getMonth(), invoice.getYear());
            message += String.format(
                ". %s (mã %s): đã thu %,.0f / %,.0f đ.",
                label, invoice.getPaymentCode(), invoice.getPaidAmount(), invoice.getTotalAmount()
            );
        }

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
