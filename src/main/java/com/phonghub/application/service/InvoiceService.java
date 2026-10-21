package com.phonghub.application.service;

import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.exception.InvoiceNotFoundException;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.UserRole;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InvoiceService implements InvoiceUseCase {

    private final InvoiceRepositoryPort invoiceRepository;
    private final MonthlyInvoiceIssuer issuer;
    private final ContractUseCase contractUseCase;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final UtilityBilling utilityBilling;
    private final AuditPort auditPort;

    public InvoiceService(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(invoiceRepository, ticketRepository, contractUseCase, currentUserPort, authorizationService, null, null);
    }

    public InvoiceService(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository
    ) {
        this(invoiceRepository, ticketRepository, contractUseCase, currentUserPort, authorizationService,
            readingRepository, rateRepository, null);
    }

    public InvoiceService(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository,
        AuditPort auditPort
    ) {
        this.auditPort = auditPort != null ? auditPort : event -> {};
        this.invoiceRepository = invoiceRepository;
        this.utilityBilling = readingRepository == null || rateRepository == null
            ? UtilityBilling.none() : new UtilityBilling(readingRepository, rateRepository);
        this.issuer = new MonthlyInvoiceIssuer(invoiceRepository, ticketRepository, utilityBilling);
        this.contractUseCase = contractUseCase;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<Invoice> listInvoicesForContract(UUID contractId) {
        assertCanViewFinance();
        // getContract áp dụng quy tắc xem hợp đồng (người thuê chỉ xem hợp đồng của mình).
        assertCanViewPropertyFinance(contractUseCase.getContract(contractId));
        return invoiceRepository.findByContractId(contractId).stream()
            .sorted(Comparator.comparing(Invoice::getYear).thenComparing(Invoice::getMonth).thenComparing(Invoice::getCreatedAt).reversed())
            .toList();
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new DomainException("Không tìm thấy hóa đơn."));
        assertCanViewFinance();
        // getContract áp dụng quy tắc xem hợp đồng (người thuê chỉ xem hợp đồng của mình).
        assertCanViewPropertyFinance(contractUseCase.getContract(invoice.getContractId()));
        return invoice;
    }

    @Override
    public Map<UUID, List<String>> utilityWarnings(UUID contractId) {
        assertCanViewFinance();
        Contract contract = contractUseCase.getContract(contractId);
        assertCanViewPropertyFinance(contract);
        Map<UUID, List<String>> warnings = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findByContractId(contractId)) {
            List<String> found = utilityBilling.warnings(invoice, contract);
            if (!found.isEmpty()) {
                warnings.put(invoice.getId(), found);
            }
        }
        return warnings;
    }

    @Override
    public Invoice issueMonthlyInvoice(UUID contractId, YearMonth period) {
        assertCanViewFinance();
        Contract contract = contractUseCase.getContract(contractId);
        authorizationService.assertCanManageContracts(currentUserPort.getCurrentUser(), contract.getPropertyId());

        if (issuer.exists(contract, period)) {
            throw new DomainException(String.format(
                "Kỳ thanh toán %02d/%d đã tồn tại cho hợp đồng này.", period.getMonthValue(), period.getYear()
            ));
        }
        return issuer.issue(contract, period);
    }

    @Override
    public Invoice voidInvoice(UUID invoiceId, String reason) {
        assertCanViewFinance();
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException("Không tìm thấy hóa đơn: " + invoiceId));
        // Quyền trước trạng thái: người ngoài phạm vi nhận 403, không biết được tình trạng hóa đơn.
        Contract contract = contractUseCase.getContract(invoice.getContractId());
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhập lý do hủy hóa đơn.");
        }

        if (invoice.getType() == InvoiceType.MAINTENANCE) {
            throw new ConflictException("Đây là phí sửa chữa; hãy dùng chức năng miễn phí ở phiếu bảo trì.");
        }
        if (invoice.isConsolidated()) {
            throw new ConflictException("Hóa đơn đã được gộp vào hóa đơn tháng khác, không hủy riêng được.");
        }
        if (!invoice.isPayable()) {
            throw new ConflictException("Không thể hủy hóa đơn ở trạng thái " + invoice.getStatus() + ".");
        }
        if (invoice.getPaidAmount().signum() > 0) {
            throw new ConflictException("Hóa đơn đã nhận " + invoice.getPaidAmount().toPlainString()
                + " đ nên không hủy được; hãy xử lý khoản đã thu trước.");
        }
        long rolledFees = invoiceRepository.findByContractId(invoice.getContractId()).stream()
            .filter(other -> invoice.getId().equals(other.getConsolidatedIntoInvoiceId()))
            .count();
        if (rolledFees > 0) {
            throw new ConflictException(String.format(
                "Hóa đơn đang gộp %d khoản phí sửa chữa; hủy sẽ làm mất các khoản này nên không thực hiện được.",
                rolledFees));
        }

        invoice.voidInvoice();
        Invoice saved = invoiceRepository.save(invoice);
        auditPort.recordEvent(AuditPort.AuditEvent.of("INVOICE_VOIDED", currentUser.id(), "INVOICE",
            saved.getId().toString(), contract.getPropertyId(), Map.of(
                "period", String.format("%02d/%d", saved.getMonth(), saved.getYear()),
                "totalAmount", saved.getTotalAmount().toPlainString(),
                "reason", reason.trim())));
        return saved;
    }

    @Override
    public java.util.Optional<Invoice> findFeeInvoiceForTicket(UUID ticketId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user != null && user.role() == UserRole.ADMIN) {
            return java.util.Optional.empty();
        }
        return invoiceRepository.findActiveByTicketId(ticketId).filter(invoice -> {
            try {
                contractUseCase.getContract(invoice.getContractId());
                return true;
            } catch (UnauthorizedPropertyAccessException ex) {
                return false;
            }
        });
    }

    /** Hóa đơn là số liệu tiền của nhà trọ nên quản trị viên và kỹ thuật viên không xem (#163). */
    private void assertCanViewFinance() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user != null) {
            authorizationService.assertCanViewFinance(user);
        }
    }

    /** Nhân viên chỉ xem hóa đơn ở nhà trọ mình có quyền thu tiền hoặc quản lý hợp đồng. */
    private void assertCanViewPropertyFinance(Contract contract) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user != null) {
            authorizationService.assertCanViewPropertyFinance(user, contract.getPropertyId());
        }
    }
}
