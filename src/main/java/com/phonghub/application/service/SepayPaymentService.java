package com.phonghub.application.service;

import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.SepayGatewayOrderRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.PaymentCode;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.SepayGatewayOrder;
import com.phonghub.domain.model.SepayGatewayOrder.Status;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SepayPaymentService implements SepayWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(SepayPaymentService.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})"
    );
    private static final Pattern SANDBOX_ORDER_NUMBER = Pattern.compile("PH[A-F0-9]{24}");
    private static final String SANDBOX_CHECKOUT_ENDPOINT = "https://pay-sandbox.sepay.vn/v1/checkout/init";
    private static final List<String> SIGNED_CHECKOUT_FIELDS = List.of(
        "order_amount", "merchant", "currency", "operation", "order_description",
        "order_invoice_number", "customer_id", "payment_method", "success_url", "error_url", "cancel_url"
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
    private final SepayGatewayOrderRepositoryPort gatewayOrderRepository;

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
        this(paymentRepository, invoiceRepository, ticketRepository, contractRepository, propertyRepository,
            notificationRepository, userRepository, currentUserPort, authorizationService, sepayProperties, null);
    }

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
        SepayProperties sepayProperties,
        SepayGatewayOrderRepositoryPort gatewayOrderRepository
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
        this.gatewayOrderRepository = gatewayOrderRepository;
    }

    @Override
    public WebhookProcessResult processWebhook(String authorizationHeader, SepayWebhookCommand command) {
        validateAuthorization(authorizationHeader);
        return processTransfer(command);
    }

    @Override
    public SandboxCheckout createSandboxCheckout(UUID invoiceId) {
        if (gatewayOrderRepository == null || sepayProperties == null || !sepayProperties.hasSandboxCheckoutConfiguration()) {
            throw new DomainException("SePay Sandbox checkout chưa được cấu hình.");
        }
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null || (user.role() != UserRole.TENANT && user.role() != UserRole.ADMIN)) {
            throw new UnauthorizedPropertyAccessException("Chỉ người thuê hoặc quản trị viên được thanh toán.");
        }

        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new DomainException("Không tìm thấy hóa đơn."));
        authorizationService.assertCanViewInvoice(user, invoice);
        if (!invoice.isPayable()) {
            throw new DomainException("Hóa đơn này không còn cần thanh toán.");
        }
        BigDecimal amount = invoice.remainingAmount();
        if (!isWholeVndAmount(amount)) {
            throw new DomainException("SePay Sandbox chỉ hỗ trợ số tiền VND nguyên dương.");
        }

        SepayGatewayOrder order = gatewayOrderRepository.findPendingByInvoiceIdForUpdate(invoiceId).orElse(null);
        if (order != null && order.amount().compareTo(amount) != 0) {
            gatewayOrderRepository.expire(order.orderInvoiceNumber());
            order = null;
        }
        if (order == null) {
            order = new SepayGatewayOrder(
                newSandboxOrderNumber(), invoiceId, amount, Status.PENDING, null, Instant.now(), null
            );
            gatewayOrderRepository.savePending(order);
        }

        String baseUrl = requirePublicBaseUrl();
        String invoiceUrl = baseUrl + "/invoices/" + invoiceId + "/pay";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", amount.toBigIntegerExact().toString());
        fields.put("merchant", sepayProperties.getMerchantId().trim());
        fields.put("currency", "VND");
        fields.put("operation", "PURCHASE");
        fields.put("order_description", "PhongHub " + invoice.getPaymentCode());
        fields.put("order_invoice_number", order.orderInvoiceNumber());
        fields.put("payment_method", "BANK_TRANSFER");
        fields.put("success_url", invoiceUrl + "?sepay=result");
        fields.put("error_url", invoiceUrl + "?sepay=error");
        fields.put("cancel_url", invoiceUrl + "?sepay=cancelled");
        fields.put("signature", signCheckoutFields(fields));
        return new SandboxCheckout(
            SANDBOX_CHECKOUT_ENDPOINT,
            Collections.unmodifiableMap(new LinkedHashMap<>(fields))
        );
    }

    @Override
    public SandboxIpnResult processSandboxIpn(String suppliedSecret, SandboxIpnCommand command) {
        validateSandboxSecret(suppliedSecret);
        if (command == null || !"ORDER_PAID".equals(command.notificationType())) {
            return new SandboxIpnResult("ignored", false);
        }
        if (command.order() == null || command.transaction() == null) {
            throw new DomainException("IPN thiếu thông tin đơn hàng hoặc giao dịch.");
        }

        SandboxOrder orderPayload = command.order();
        SandboxTransaction transactionPayload = command.transaction();
        if (!"CAPTURED".equals(orderPayload.orderStatus())
            || !"VND".equals(orderPayload.orderCurrency())
            || !"PAYMENT".equals(transactionPayload.transactionType())
            || !"APPROVED".equals(transactionPayload.transactionStatus())
            || !"VND".equals(transactionPayload.transactionCurrency())
            || transactionPayload.transactionId() == null
            || transactionPayload.transactionId().isBlank()
            || transactionPayload.transactionId().length() > 160
            || orderPayload.orderInvoiceNumber() == null
            || !SANDBOX_ORDER_NUMBER.matcher(orderPayload.orderInvoiceNumber()).matches()) {
            throw new DomainException("IPN thanh toán không hợp lệ.");
        }

        BigDecimal orderAmount = parseWholeVndAmount(orderPayload.orderAmount());
        BigDecimal transactionAmount = parseWholeVndAmount(transactionPayload.transactionAmount());
        if (orderAmount == null || transactionAmount == null || orderAmount.compareTo(transactionAmount) != 0) {
            throw new DomainException("Số tiền IPN không hợp lệ hoặc không khớp.");
        }

        String transactionId = transactionPayload.transactionId();
        SepayGatewayOrder order = gatewayOrderRepository.findByInvoiceNumberForUpdate(orderPayload.orderInvoiceNumber())
            .orElseThrow(() -> new DomainException("Không tìm thấy đơn hàng SePay Sandbox."));
        if (order.amount().compareTo(orderAmount) != 0) {
            throw new DomainException("Số tiền IPN không khớp với đơn hàng.");
        }
        if (order.status() != Status.PENDING) {
            if (order.status() == Status.PAID && transactionId.equals(order.transactionId())) {
                return new SandboxIpnResult("paid", true);
            }
            throw new DomainException("Trạng thái đơn hàng không hợp lệ.");
        }

        SepayGatewayOrder priorTransaction = gatewayOrderRepository.findByTransactionId(transactionId).orElse(null);
        if (priorTransaction != null) {
            if (!priorTransaction.orderInvoiceNumber().equals(order.orderInvoiceNumber())) {
                throw new DomainException("Giao dịch đã được gắn với đơn khác.");
            }
            throw new DomainException("Giao dịch đã được ghi nhận nhưng đơn hàng chưa hoàn tất.");
        }

        Invoice invoice = invoiceRepository.findByIdForUpdate(order.invoiceId())
            .orElseThrow(() -> new DomainException("Không tìm thấy hóa đơn."));
        if (!invoice.isPayable() || invoice.remainingAmount().compareTo(order.amount()) != 0) {
            throw new DomainException("Hóa đơn đã thay đổi sau khi tạo đơn thanh toán; IPN bị từ chối.");
        }

        long internalId = internalGatewayTransactionId(transactionId);
        WebhookProcessResult result = processTransfer(new SepayWebhookCommand(
            internalId,
            "SEPAY_SANDBOX",
            Instant.now().toString(),
            "SEPAY_SANDBOX",
            null,
            invoice.getPaymentCode(),
            "SEPAY SANDBOX " + invoice.getPaymentCode(),
            "in",
            transactionAmount,
            null,
            transactionId,
            "SePay Sandbox IPN"
        ), transactionId);
        if (!result.success() || !gatewayOrderRepository.markPaid(order.orderInvoiceNumber(), transactionId, Instant.now())) {
            throw new DomainException("Không thể ghi nhận giao dịch SePay Sandbox.");
        }
        return new SandboxIpnResult("paid", false);
    }

    private WebhookProcessResult processTransfer(SepayWebhookCommand command) {
        return processTransfer(command, null);
    }

    private WebhookProcessResult processTransfer(SepayWebhookCommand command, String gatewayTransactionId) {
        if (command.id() == null) {
            throw new IllegalArgumentException("Thiếu id giao dịch SePay");
        }

        Optional<PaymentTransaction> gatewayExisting = paymentRepository.findByGatewayTransactionId(gatewayTransactionId);
        if (gatewayExisting.isPresent()) {
            return new WebhookProcessResult(true, "Giao dịch đã được ghi nhận trước đó", gatewayExisting.get());
        }

        // 1. Idempotency (đường nhanh): SePay gửi lại webhook khi chưa nhận 200
        Optional<PaymentTransaction> existing = paymentRepository.findBySepayId(command.id());
        if (existing.isPresent()) {
            if (gatewayTransactionId != null && !gatewayTransactionId.equals(existing.get().gatewayTransactionId())) {
                throw new DomainException("Mã giao dịch nội bộ đã được sử dụng bởi một giao dịch khác.");
            }
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

        // 3b. Chia số tiền: phần cộng vào hóa đơn (tối đa số còn nợ) và phần dư cần người quản lý xem xét.
        // Hóa đơn đã được khóa dòng ở bước khớp nên số còn nợ không đổi giữa lúc tính và lúc cộng.
        BigDecimal amount = command.transferAmount() != null ? command.transferAmount() : BigDecimal.ZERO;
        BigDecimal applied = BigDecimal.ZERO;
        BigDecimal excess = BigDecimal.ZERO;
        PaymentReviewReason reviewReason = null;
        if (status == PaymentStatus.SUCCESS && amount.signum() > 0) {
            if (invoice == null) {
                excess = amount;
                reviewReason = PaymentReviewReason.UNMATCHED;
            } else if (!invoice.isPayable()) {
                excess = amount;
                reviewReason = PaymentReviewReason.INVOICE_NOT_PAYABLE;
            } else {
                applied = amount.min(invoice.remainingAmount());
                excess = amount.subtract(applied);
                if (excess.signum() > 0) {
                    reviewReason = PaymentReviewReason.OVERPAID;
                }
            }
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
            Instant.now(),
            applied,
            excess,
            reviewReason,
            gatewayTransactionId
        );
        if (!paymentRepository.saveIfNew(tx)) {
            PaymentTransaction duplicate = paymentRepository.findByGatewayTransactionId(gatewayTransactionId)
                .or(() -> paymentRepository.findBySepayId(command.id()))
                .orElse(null);
            if (duplicate == null) {
                throw new DomainException("Mã giao dịch đã tồn tại nhưng không khớp IPN hiện tại.");
            }
            return new WebhookProcessResult(true, "Giao dịch đã được ghi nhận trước đó", duplicate);
        }
        log.info("Sepay webhook: Đã lưu giao dịch ID={}, SePayId={}, Số tiền={}, Đã cộng={}, Dư={}, Status={}, Invoice={}",
            tx.id(), tx.sepayId(), tx.transferAmount(), applied, excess, tx.status(), tx.invoiceId());
        if (reviewReason != null) {
            log.warn("Sepay webhook: giao dịch SePayId={} cần xem xét ({}), số tiền dư {}", tx.sepayId(), reviewReason, excess);
        }

        // 5. Cộng tiền vào kỳ thanh toán (chỉ khi giao dịch mới và kỳ còn nợ)
        if (invoice != null && applied.signum() > 0) {
            invoice.applyPayment(applied, tx.createdAt());
            invoiceRepository.save(invoice);
            completeMaintenanceTicketIfPaid(invoice);
        }

        // 6. Gửi thông báo đến người quản lý nếu là tiền vào thành công
        if (status == PaymentStatus.SUCCESS) {
            sendPaymentNotification(tx, invoice);
        }

        return new WebhookProcessResult(true, "Xử lý giao dịch SePay thành công", tx);
    }

    private void validateSandboxSecret(String suppliedSecret) {
        String configuredSecret = sepayProperties != null ? sepayProperties.getSecretKey() : null;
        if (configuredSecret == null || configuredSecret.isBlank() || suppliedSecret == null
            || !constantTimeEquals(suppliedSecret.trim(), configuredSecret.trim())) {
            throw new UnauthorizedWebhookException("IPN SePay Sandbox không được xác thực.");
        }
    }

    private String requirePublicBaseUrl() {
        String configuredUrl = sepayProperties.getPublicBaseUrl();
        try {
            URI uri = new URI(configuredUrl == null ? "" : configuredUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean localHost = host.equals("localhost") || host.endsWith(".localhost")
                || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank() || localHost
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
                throw new URISyntaxException(configuredUrl, "Expected a public HTTPS origin");
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (URISyntaxException ex) {
            throw new DomainException("SEPAY_PUBLIC_BASE_URL cần là domain HTTPS công khai, không kèm path hoặc query.");
        }
    }

    private String signCheckoutFields(Map<String, String> fields) {
        String signedString = SIGNED_CHECKOUT_FIELDS.stream()
            .filter(fields::containsKey)
            .map(field -> field + "=" + fields.get(field))
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sepayProperties.getSecretKey().trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(signedString.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể ký yêu cầu SePay Sandbox.", ex);
        }
    }

    private static String newSandboxOrderNumber() {
        return "PH" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private static boolean isWholeVndAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 0;
    }

    private static BigDecimal parseWholeVndAmount(String rawAmount) {
        if (rawAmount == null || !rawAmount.matches("\\d+(?:\\.0{1,2})?")) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(rawAmount);
            return isWholeVndAmount(amount) ? amount : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long internalGatewayTransactionId(String transactionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(transactionId.getBytes(StandardCharsets.UTF_8));
            long hash = ByteBuffer.wrap(digest).getLong();
            return Long.MIN_VALUE | (hash & Long.MAX_VALUE);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể tạo mã giao dịch SePay Sandbox.", ex);
        }
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
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            return;
        }
        // Hóa đơn phí riêng: phiếu của nó. Hóa đơn tháng: phiếu của các khoản phí đã gộp (dòng chi tiết).
        List<UUID> ticketIds = new java.util.ArrayList<>();
        if (invoice.getType() == InvoiceType.MAINTENANCE && invoice.getTicketId() != null) {
            ticketIds.add(invoice.getTicketId());
        }
        invoice.getItems().stream()
            .filter(item -> item.sourceInvoiceId() != null)
            .forEach(item -> invoiceRepository.findById(item.sourceInvoiceId())
                .map(Invoice::getTicketId)
                .ifPresent(ticketIds::add));

        for (UUID ticketId : ticketIds) {
            ticketRepository.findById(ticketId)
                .filter(ticket -> ticket.getStatus() == MaintenanceStatus.AWAITING_PAYMENT)
                .ifPresent(ticket -> {
                    ticket.markFeePaid();
                    ticketRepository.save(ticket);
                });
        }
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
            String label = switch (invoice.getType()) {
                case MAINTENANCE -> "Phí sửa chữa";
                case CHECK_OUT -> "Hóa đơn chốt điện/nước";
                case RENT -> String.format("Kỳ %02d/%d", invoice.getMonth(), invoice.getYear());
            };
            message += String.format(
                ". %s (mã %s): đã thu %,.0f / %,.0f đ.",
                label, invoice.getPaymentCode(), invoice.getPaidAmount(), invoice.getTotalAmount()
            );
        }

        if (tx.needsReview()) {
            title = "Cần xem xét giao dịch SePay: " + String.format("%,.0f đ", amount);
            message += switch (tx.reviewReason()) {
                case OVERPAID -> String.format(" Người thuê chuyển dư %,.0f đ so với số còn nợ; cần hoàn lại hoặc cấn trừ kỳ sau.",
                    tx.excessAmount());
                case INVOICE_NOT_PAYABLE -> String.format(
                    " Hóa đơn này không còn nhận tiền (đã trả đủ, hủy hoặc đã gộp); %,.0f đ chưa được ghi nhận, cần hoàn lại.",
                    tx.excessAmount());
                case UNMATCHED -> String.format(
                    " Không khớp được hóa đơn nào theo nội dung chuyển khoản; %,.0f đ chưa được ghi nhận.", tx.excessAmount());
            };
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
