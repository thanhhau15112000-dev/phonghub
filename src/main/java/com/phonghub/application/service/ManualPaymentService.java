package com.phonghub.application.service;

import com.phonghub.application.port.in.ManualPaymentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PaymentClaimRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.InvoiceNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.PaymentClaim;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ManualPaymentService implements ManualPaymentUseCase {

    private final PaymentClaimRepositoryPort claimRepository;
    private final InvoiceRepositoryPort invoiceRepository;
    private final ContractRepositoryPort contractRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final NotificationDispatcher notifications;
    private final MaintenanceFeeSettlement feeSettlement;

    public ManualPaymentService(
        PaymentClaimRepositoryPort claimRepository,
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        NotificationDispatcher notifications,
        MaintenanceFeeSettlement feeSettlement
    ) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository");
        this.contractRepository = Objects.requireNonNull(contractRepository, "contractRepository");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.auditPort = auditPort;
        this.notifications = notifications;
        this.feeSettlement = Objects.requireNonNull(feeSettlement, "feeSettlement");
    }

    @Override
    public PaymentClaim submitClaim(UUID invoiceId, BigDecimal amount, PaymentClaim.Method method, String note) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Invoice invoice = lockInvoice(invoiceId);
        // Quyền trước trạng thái: người ngoài hợp đồng nhận 403, không biết tình trạng hóa đơn.
        if (user.role() != UserRole.TENANT) {
            throw new UnauthorizedPropertyAccessException(
                "Chỉ người thuê của hợp đồng mới gửi yêu cầu xác nhận thanh toán.");
        }
        authorizationService.assertCanViewInvoice(user, invoice);
        Contract contract = contractOf(invoice);
        assertAcceptsPayment(invoice, amount);
        if (claimRepository.findByInvoiceId(invoiceId).stream().anyMatch(c -> c.status() == PaymentClaim.Status.PENDING)) {
            throw new ConflictException("Hóa đơn đã có yêu cầu thanh toán đang chờ xác nhận.");
        }

        PaymentClaim claim = claimRepository.save(PaymentClaim.submitted(invoice, contract.getPropertyId(), amount,
            requireMethod(method), note, user.id()));
        audit("PAYMENT_CLAIM_SUBMITTED", user, claim, Map.of("amount", amount.toPlainString(), "method", method.name()));
        if (notifications != null) {
            notifications.notifyPaymentCollectors(contract.getPropertyId(), "Yêu cầu xác nhận thanh toán",
                "Người thuê báo đã thanh toán " + money(amount) + " cho " + label(invoice) + ". Vui lòng kiểm tra và xác nhận.",
                "/contracts/" + contract.getId() + "#payments");
        }
        return claim;
    }

    @Override
    public PaymentClaim recordPayment(UUID invoiceId, BigDecimal amount, PaymentClaim.Method method, String note) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Invoice invoice = lockInvoice(invoiceId);
        Contract contract = contractOf(invoice);
        authorizationService.assertCanCollectPayment(user, contract.getPropertyId());
        assertAcceptsPayment(invoice, amount);

        PaymentClaim claim = PaymentClaim.recorded(invoice, contract.getPropertyId(), amount, requireMethod(method), note,
            user.id());
        applyToInvoice(invoice, claim);
        claimRepository.save(claim);
        audit("PAYMENT_MANUAL_CONFIRMED", user, claim, Map.of("amount", amount.toPlainString(),
            "method", method.name(), "direct", true));
        notifyTenants(contract, "Đã ghi nhận thanh toán", "Đã ghi nhận " + money(amount) + " cho " + label(invoice) + ".");
        return claim;
    }

    @Override
    public PaymentClaim confirm(UUID claimId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        PaymentClaim claim = claimRepository.findByIdForUpdate(claimId)
            .orElseThrow(() -> new DomainException("Không tìm thấy yêu cầu thanh toán: " + claimId));
        authorizationService.assertCanCollectPayment(user, claim.propertyId());
        PaymentClaim confirmed = claim.confirmed(user.id());
        Invoice invoice = lockInvoice(claim.invoiceId());
        assertAcceptsPayment(invoice, claim.amount());

        applyToInvoice(invoice, confirmed);
        claimRepository.save(confirmed);
        audit("PAYMENT_MANUAL_CONFIRMED", user, confirmed, Map.of("amount", claim.amount().toPlainString(),
            "method", claim.method().name(), "direct", false));
        notifyTenants(contractOf(invoice), "Thanh toán đã được xác nhận",
            "Đã xác nhận " + money(claim.amount()) + " cho " + label(invoice) + ".");
        return confirmed;
    }

    @Override
    public PaymentClaim reject(UUID claimId, String reason) {
        CurrentUser user = currentUserPort.getCurrentUser();
        PaymentClaim claim = claimRepository.findByIdForUpdate(claimId)
            .orElseThrow(() -> new DomainException("Không tìm thấy yêu cầu thanh toán: " + claimId));
        authorizationService.assertCanCollectPayment(user, claim.propertyId());
        PaymentClaim rejected = claim.rejected(user.id(), reason);
        claimRepository.save(rejected);
        audit("PAYMENT_MANUAL_REJECTED", user, rejected, Map.of("reason", rejected.rejectReason()));
        Invoice invoice = invoiceRepository.findById(claim.invoiceId()).orElse(null);
        notifyTenants(contractRepository.findById(claim.contractId()).orElse(null), "Yêu cầu thanh toán bị từ chối",
            "Yêu cầu xác nhận " + money(claim.amount()) + (invoice == null ? "" : " cho " + label(invoice))
                + " bị từ chối. Lý do: " + rejected.rejectReason());
        return rejected;
    }

    @Override
    public List<PaymentClaim> listPending(UUID propertyId) {
        authorizationService.assertCanCollectPayment(currentUserPort.getCurrentUser(), propertyId);
        return claimRepository.findPendingByPropertyId(propertyId);
    }

    @Override
    public List<PaymentClaim> listForContract(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new UnauthorizedPropertyAccessException(
                "Không tìm thấy hợp đồng hoặc bạn không có quyền truy cập."));
        if (user.role() == UserRole.TENANT) {
            Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
            boolean member = tenant != null && (contract.getPrimaryTenantId().equals(tenant.id())
                || contract.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenant.id())));
            if (!member) {
                throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được thanh toán của mình.");
            }
        } else {
            authorizationService.assertCanViewPropertyFinance(user, contract.getPropertyId());
        }
        return claimRepository.findByContractId(contractId);
    }

    private Invoice lockInvoice(UUID invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException("Không tìm thấy hóa đơn: " + invoiceId));
    }

    private Contract contractOf(Invoice invoice) {
        return contractRepository.findById(invoice.getContractId())
            .orElseThrow(() -> new InvoiceNotFoundException("Không tìm thấy hợp đồng của hóa đơn."));
    }

    private static PaymentClaim.Method requireMethod(PaymentClaim.Method method) {
        if (method == null) {
            throw new IllegalArgumentException("Cần chọn hình thức thanh toán.");
        }
        return method;
    }

    private static void assertAcceptsPayment(Invoice invoice, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        }
        if (!invoice.isPayable()) {
            throw new ConflictException("Hóa đơn không còn ở trạng thái chờ thanh toán (" + invoice.getStatus() + ").");
        }
        if (amount.compareTo(invoice.remainingAmount()) > 0) {
            throw new ConflictException("Số tiền vượt quá số còn nợ của hóa đơn (" + money(invoice.remainingAmount()) + ").");
        }
    }

    private void applyToInvoice(Invoice invoice, PaymentClaim claim) {
        invoice.applyPayment(claim.amount(), claim.decidedAt() != null ? claim.decidedAt() : Instant.now());
        invoiceRepository.save(invoice);
        feeSettlement.completeIfPaid(invoice);
    }

    private void notifyTenants(Contract contract, String title, String message) {
        if (notifications != null && contract != null) {
            notifications.notifyTenants(contract, true, title, message, "/contracts/" + contract.getId() + "#payments");
        }
    }

    private void audit(String action, CurrentUser actor, PaymentClaim claim, Map<String, Object> details) {
        if (auditPort != null) {
            Map<String, Object> all = new java.util.LinkedHashMap<>(details);
            all.put("invoiceId", claim.invoiceId().toString());
            all.put("claimId", claim.id().toString());
            auditPort.recordEvent(AuditPort.AuditEvent.of(action, actor.id(), "PAYMENT_CLAIM", claim.id().toString(),
                claim.propertyId(), all));
        }
    }

    private static String label(Invoice invoice) {
        return switch (invoice.getType()) {
            case MAINTENANCE -> "phí sửa chữa";
            case CHECK_OUT -> "hóa đơn chốt điện/nước";
            case CANCELLATION -> "phí hủy hợp đồng";
            case RENT -> String.format("hóa đơn kỳ %02d/%d", invoice.getMonth(), invoice.getYear());
        };
    }

    private static String money(BigDecimal amount) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN")).format(amount) + " đ";
    }
}
