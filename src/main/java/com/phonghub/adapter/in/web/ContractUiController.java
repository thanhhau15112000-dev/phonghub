package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/contracts")
public class ContractUiController {

    private final ContractUseCase contractUseCase;
    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final TenantUseCase tenantUseCase;
    private final InvoiceUseCase invoiceUseCase;
    private final SepayProperties sepayProperties;

    public ContractUiController(
        ContractUseCase contractUseCase,
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        TenantUseCase tenantUseCase,
        InvoiceUseCase invoiceUseCase,
        SepayProperties sepayProperties
    ) {
        this.contractUseCase = contractUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.tenantUseCase = tenantUseCase;
        this.invoiceUseCase = invoiceUseCase;
        this.sepayProperties = sepayProperties;
    }

    @GetMapping
    public String listContracts(Model model) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        List<Contract> contracts = new ArrayList<>();

        if (currentUser.role() == UserRole.TENANT) {
            contracts.addAll(contractUseCase.listContractsForTenant(currentUser.id()));
        } else {
            List<Property> properties = propertyUseCase.listAccessibleProperties();
            for (Property p : properties) {
                contracts.addAll(contractUseCase.listContractsForProperty(p.id()));
            }
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("contracts", contracts);
        return "contracts/list";
    }

    @GetMapping("/new")
    public String newContractForm(
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) UUID roomId,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && currentUser.role() != UserRole.STAFF) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên hoặc nhân viên mới có thể tạo hợp đồng.");
            return "redirect:/contracts";
        }

        List<Property> properties = propertyUseCase.listAccessibleProperties().stream()
            .filter(p -> p.approvalStatus() == PropertyApprovalStatus.VERIFIED)
            .toList();

        List<Room> rooms = new ArrayList<>();
        if (propertyId != null) {
            UUID selectedId = propertyId;
            boolean isVerified = properties.stream().anyMatch(p -> p.id().equals(selectedId));
            if (isVerified) {
                rooms = roomUseCase.listRoomsForProperty(propertyId);
            } else {
                propertyId = !properties.isEmpty() ? properties.getFirst().id() : null;
                if (propertyId != null) {
                    rooms = roomUseCase.listRoomsForProperty(propertyId);
                }
            }
        } else if (!properties.isEmpty()) {
            propertyId = properties.getFirst().id();
            rooms = roomUseCase.listRoomsForProperty(propertyId);
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("properties", properties);
        model.addAttribute("rooms", rooms);
        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("defaultStartDate", LocalDate.now());
        model.addAttribute("defaultEndDate", LocalDate.now().plusYears(1));

        return "contracts/new";
    }

    @PostMapping
    public String createContract(
        @RequestParam UUID propertyId,
        @RequestParam UUID roomId,
        @RequestParam String primaryTenantFullName,
        @RequestParam String primaryTenantIdCard,
        @RequestParam String primaryTenantPhone,
        @RequestParam(required = false) String primaryTenantEmail,
        @RequestParam BigDecimal depositAmount,
        @RequestParam BigDecimal rentAmount,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(defaultValue = "5") int paymentDay,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Contract contract = contractUseCase.createContract(new ContractUseCase.CreateContractCommand(
                propertyId,
                roomId,
                primaryTenantFullName,
                primaryTenantIdCard,
                primaryTenantPhone,
                primaryTenantEmail,
                null,
                depositAmount,
                rentAmount,
                startDate,
                endDate,
                paymentDay
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo hợp đồng ở trạng thái bản nháp. Bạn có thể kích hoạt hợp đồng.");
            return "redirect:/contracts/" + contract.getId();
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts/new?propertyId=" + propertyId + "&roomId=" + roomId;
        }
    }

    @GetMapping("/{id}")
    public String viewContract(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        try {
            Contract contract = contractUseCase.getContract(id);
            Property property = propertyUseCase.getProperty(contract.getPropertyId());
            Room room = roomUseCase.getRoom(contract.getRoomId());
            Tenant primaryTenant = tenantRepository.findById(contract.getPrimaryTenantId()).orElse(null);

            java.util.Map<UUID, Tenant> occupantTenants = new java.util.HashMap<>();
            if (contract.getOccupants() != null) {
                for (com.phonghub.domain.model.ContractOccupant occ : contract.getOccupants()) {
                    tenantRepository.findById(occ.tenantId()).ifPresent(t -> occupantTenants.put(occ.tenantId(), t));
                }
            }

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("contract", contract);
            model.addAttribute("property", property);
            model.addAttribute("room", room);
            model.addAttribute("primaryTenant", primaryTenant);
            model.addAttribute("occupantTenants", occupantTenants);
            addPaymentAttributes(model, currentUser, contract, property);

            return "contracts/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts";
        }
    }

    @PostMapping("/{id}/invoices")
    public String issueInvoice(
        @PathVariable UUID id,
        @RequestParam String period,
        RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth yearMonth = YearMonth.parse(period);
            Invoice invoice = invoiceUseCase.issueMonthlyInvoice(id, yearMonth);
            redirectAttributes.addFlashAttribute("successMessage", String.format(
                "Đã tạo kỳ thanh toán %02d/%d, mã chuyển khoản %s.",
                invoice.getMonth(), invoice.getYear(), invoice.getPaymentCode()
            ));
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kỳ thanh toán không hợp lệ, định dạng yêu cầu: yyyy-MM.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    private void addPaymentAttributes(Model model, CurrentUser currentUser, Contract contract, Property property) {
        List<Invoice> invoices = invoiceUseCase.listInvoicesForContract(contract.getId());
        LocalDate today = LocalDate.now();
        // Kỳ cũ nhất còn nợ được ưu tiên hiển thị hướng dẫn thanh toán.
        Invoice payableInvoice = invoices.stream()
            .filter(Invoice::isPayable)
            .min(Comparator.comparing(Invoice::getYear).thenComparing(Invoice::getMonth))
            .orElse(null);

        boolean canIssueInvoice = contract.isActive() && currentUser != null && (
            currentUser.role() == UserRole.ADMIN
                || currentUser.role() == UserRole.STAFF
                || (currentUser.role() == UserRole.OWNER && currentUser.id().equals(property.ownerId()))
        );

        model.addAttribute("invoices", invoices);
        model.addAttribute("today", today);
        model.addAttribute("payableInvoice", payableInvoice);
        model.addAttribute("canIssueInvoice", canIssueInvoice);
        model.addAttribute("defaultInvoicePeriod", YearMonth.now().toString());
        model.addAttribute("bankConfigured", sepayProperties.hasBankAccount());
        model.addAttribute("bankCode", sepayProperties.getBankCode());
        model.addAttribute("bankAccountNumber", sepayProperties.getBankAccountNumber());
        model.addAttribute("bankAccountName", sepayProperties.getBankAccountName());
        model.addAttribute("paymentQrUrl", payableInvoice != null && sepayProperties.hasBankAccount()
            ? sepayQrUrl(payableInvoice)
            : null);
    }

    private String sepayQrUrl(Invoice invoice) {
        return UriComponentsBuilder.fromUriString("https://qr.sepay.vn/img")
            .queryParam("acc", sepayProperties.getBankAccountNumber().trim())
            .queryParam("bank", sepayProperties.getBankCode().trim())
            .queryParam("amount", invoice.remainingAmount().setScale(0, RoundingMode.UP).toPlainString())
            .queryParam("des", invoice.getPaymentCode())
            .encode()
            .toUriString();
    }

    @PostMapping("/{id}/occupants")
    public String addOccupant(
        @PathVariable UUID id,
        @RequestParam String fullName,
        @RequestParam String identityCardNumber,
        @RequestParam String phone,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String permanentAddress,
        @RequestParam(required = false) LocalDate checkInDate,
        @RequestParam(defaultValue = "false") boolean createAccount,
        RedirectAttributes redirectAttributes
    ) {
        try {
            contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
                id,
                fullName,
                identityCardNumber,
                phone,
                email,
                permanentAddress,
                checkInDate != null ? checkInDate : LocalDate.now(),
                createAccount
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Thêm người thuê vào phòng thành công.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activateContract(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            contractUseCase.activateContract(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã kích hoạt hợp đồng. Phòng hiện ở trạng thái đang thuê.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{id}/terminate")
    public String terminateContract(
        @PathVariable UUID id,
        @RequestParam(name = "requiresMaintenance", defaultValue = "false") boolean requiresMaintenance,
        RedirectAttributes redirectAttributes
    ) {
        try {
            contractUseCase.terminateContract(id, requiresMaintenance);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Đã chấm dứt hợp đồng. Phòng hiện ở trạng thái "
                    + UiText.INSTANCE.roomStatus(requiresMaintenance ? RoomStatus.MAINTENANCE : RoomStatus.AVAILABLE)
                    + "."
            );
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{contractId}/tenants/{tenantId}/edit")
    public String updateTenant(
        @PathVariable UUID contractId,
        @PathVariable UUID tenantId,
        @RequestParam String fullName,
        @RequestParam String identityCardNumber,
        @RequestParam String phone,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String permanentAddress,
        RedirectAttributes redirectAttributes
    ) {
        try {
            tenantUseCase.updateTenant(new TenantUseCase.UpdateTenantCommand(
                tenantId,
                fullName,
                identityCardNumber,
                phone,
                email,
                permanentAddress
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin người thuê thành công.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + contractId;
    }

    @PostMapping("/{contractId}/occupants/{tenantId}/check-out")
    public String checkOutOccupant(
        @PathVariable UUID contractId,
        @PathVariable UUID tenantId,
        @RequestParam(required = false) LocalDate checkOutDate,
        RedirectAttributes redirectAttributes
    ) {
        try {
            contractUseCase.checkOutOccupant(new ContractUseCase.CheckOutOccupantCommand(
                contractId,
                tenantId,
                checkOutDate != null ? checkOutDate : LocalDate.now()
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Ghi nhận người thuê rời phòng thành công.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + contractId;
    }
}
