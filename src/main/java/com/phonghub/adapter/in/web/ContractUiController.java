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
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/contracts")
public class ContractUiController {

    private final ContractUseCase contractUseCase;
    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
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
        UserRepositoryPort userRepository,
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
        this.userRepository = userRepository;
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
        java.util.Map<UUID, String> roomNames = new java.util.HashMap<>();
        java.util.Map<UUID, String> tenantNames = new java.util.HashMap<>();
        for (Contract contract : contracts) {
            roomRepository.findById(contract.getRoomId())
                .ifPresent(room -> roomNames.put(room.getId(), room.getRoomNumber()));
            tenantRepository.findById(contract.getPrimaryTenantId())
                .ifPresent(tenant -> tenantNames.put(tenant.id(), tenant.fullName()));
        }
        model.addAttribute("roomNames", roomNames);
        model.addAttribute("tenantNames", tenantNames);
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
        if (currentUser.role() != UserRole.ADMIN && currentUser.role() != UserRole.STAFF && currentUser.role() != UserRole.OWNER) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên, chủ nhà trọ hoặc nhân viên mới có thể tạo hợp đồng.");
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
        model.addAttribute("tenantAccounts", propertyId == null
            ? List.of() : contractUseCase.listAvailablePrimaryTenants(propertyId, null));
        model.addAttribute("defaultStartDate", LocalDate.now());
        model.addAttribute("defaultEndDate", LocalDate.now().plusYears(1));

        return "contracts/new";
    }

    @PostMapping
    public String createContract(
        @RequestParam UUID propertyId,
        @RequestParam UUID roomId,
        @RequestParam UUID primaryTenantId,
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
                primaryTenantId,
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

            List<Tenant> availableOccupants = List.of();
            int remainingOccupantSlots = 0;
            boolean canManageOccupants = false;
            if (currentUser != null && contract.isActive()) {
                try {
                    availableOccupants = contractUseCase.listAvailableOccupants(contract.getId());
                    long residingCount = countResidingOccupants(contract, LocalDate.now());
                    remainingOccupantSlots = Math.max(0, room.getMaxOccupants() - (int) residingCount);
                    canManageOccupants = true;
                } catch (DomainException ex) {
                    // Hide occupant controls unless the use case confirms the user's contract permission.
                }
            }

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("contract", contract);
            model.addAttribute("property", property);
            model.addAttribute("room", room);
            model.addAttribute("primaryTenant", primaryTenant);
            model.addAttribute("occupantTenants", occupantTenants);
            model.addAttribute("availableOccupants", availableOccupants);
            model.addAttribute("remainingOccupantSlots", remainingOccupantSlots);
            model.addAttribute("canManageOccupants", canManageOccupants);
            // Hóa đơn và thanh toán là số liệu tiền của nhà trọ: quản trị viên, kỹ thuật viên và nhân viên không có
            // quyền thu tiền/quản lý hợp đồng không xem (#163); trang hợp đồng vẫn hiện, chỉ ẩn phần thanh toán.
            if (currentUser.role() != UserRole.ADMIN && currentUser.role() != UserRole.TECHNICIAN) {
                try {
                    addPaymentAttributes(model, currentUser, contract, property);
                } catch (UnauthorizedPropertyAccessException ex) {
                    // không có quyền xem tiền ở nhà trọ này: bỏ qua phần thanh toán
                }
            }

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

    @PostMapping("/{id}/invoices/{invoiceId}/void")
    public String voidInvoice(
        @PathVariable UUID id,
        @PathVariable UUID invoiceId,
        @RequestParam(required = false) String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Invoice invoice = invoiceUseCase.voidInvoice(invoiceId, reason);
            redirectAttributes.addFlashAttribute("successMessage", String.format(
                "Đã hủy hóa đơn kỳ %02d/%d. Có thể phát hành lại kỳ này nếu cần.", invoice.getMonth(), invoice.getYear()));
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

        boolean canIssueInvoice = contract.isResiding() && currentUser != null && (
            currentUser.role() == UserRole.STAFF
                || (currentUser.role() == UserRole.OWNER && currentUser.id().equals(property.ownerId()))
        );

        model.addAttribute("invoices", invoices);
        model.addAttribute("invoiceWarnings", invoiceUseCase.utilityWarnings(contract.getId()));
        model.addAttribute("today", today);
        model.addAttribute("payableInvoice", payableInvoice);
        model.addAttribute("canIssueInvoice", canIssueInvoice);
        // Hủy hóa đơn (#89) không phụ thuộc hợp đồng còn hiệu lực: hợp đồng đã kết thúc vẫn có thể còn kỳ lập sai.
        model.addAttribute("canVoidInvoice", currentUser != null && (
            currentUser.role() == UserRole.STAFF
                || (currentUser.role() == UserRole.OWNER && currentUser.id().equals(property.ownerId()))));
        model.addAttribute("defaultInvoicePeriod", YearMonth.now().toString());
        model.addAttribute("bankConfigured", sepayProperties.hasBankAccount());
        model.addAttribute("bankCode", sepayProperties.getBankCode());
        model.addAttribute("bankAccountNumber", sepayProperties.getBankAccountNumber());
        model.addAttribute("bankAccountName", sepayProperties.getBankAccountName());
        model.addAttribute("paymentQrUrl", payableInvoice != null && sepayProperties.hasBankAccount()
            ? PaymentQr.url(sepayProperties, payableInvoice)
            : null);
    }

    private long countResidingOccupants(Contract contract, LocalDate today) {
        long count = contract.getOccupants().stream()
            .filter(occupant -> occupant.checkOutDate() == null || occupant.checkOutDate().isAfter(today))
            .count();
        boolean primaryCounted = contract.getOccupants().stream()
            .anyMatch(occupant -> occupant.isPrimary() && occupant.tenantId().equals(contract.getPrimaryTenantId())
                && (occupant.checkOutDate() == null || occupant.checkOutDate().isAfter(today)));
        return contract.isResiding() && !primaryCounted ? count + 1 : count;
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
        @RequestParam(defaultValue = "true") boolean createAccount,
        RedirectAttributes redirectAttributes
    ) {
        try {
            ContractUseCase.AddOccupantResult result = contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
                id,
                fullName,
                identityCardNumber,
                phone,
                email,
                permanentAddress,
                checkInDate != null ? checkInDate : LocalDate.now(),
                true
            ));
            String message = "Thêm người thuê vào phòng thành công.";
            if (result.temporaryPassword() != null) {
                // Mật khẩu tạm chỉ hiện một lần; người thuê phải đổi ở lần đăng nhập đầu
                message += " Đã tạo tài khoản đăng nhập: " + result.accountUsername() + " / mật khẩu tạm "
                    + result.temporaryPassword() + " (chỉ hiện một lần, hãy gửi cho người thuê).";
            } else if (result.accountUsername() != null) {
                message += " Đã liên kết với tài khoản có sẵn: " + result.accountUsername() + ".";
            }
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{id}/occupants/bulk")
    public String addExistingOccupants(
        @PathVariable UUID id,
        @RequestParam(required = false) List<UUID> tenantIds,
        @RequestParam(required = false) LocalDate checkInDate,
        RedirectAttributes redirectAttributes
    ) {
        try {
            List<com.phonghub.domain.model.ContractOccupant> added = contractUseCase.addExistingOccupants(
                new ContractUseCase.AddExistingOccupantsCommand(id, tenantIds, checkInDate));
            redirectAttributes.addFlashAttribute("successMessage", String.format(
                "Đã thêm %d người thuê vào phòng trong một lần xác nhận.", added.size()));
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

    @PostMapping("/{id}/edit")
    public String updateDraftContract(
        @PathVariable UUID id,
        @RequestParam java.math.BigDecimal depositAmount,
        @RequestParam java.math.BigDecimal rentAmount,
        @RequestParam java.time.LocalDate startDate,
        @RequestParam java.time.LocalDate endDate,
        @RequestParam int paymentDay,
        @RequestParam(required = false) Long version,
        RedirectAttributes redirectAttributes
    ) {
        try {
            contractUseCase.updateDraftContract(new ContractUseCase.UpdateDraftContractCommand(
                id, depositAmount, rentAmount, startDate, endDate, paymentDay, version));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật điều khoản hợp đồng nháp.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancelDraftContract(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            contractUseCase.cancelDraftContract(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy hợp đồng nháp. Hợp đồng vẫn được lưu để tra cứu.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{id}/renew")
    public String renewContract(
        @PathVariable UUID id,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false) java.math.BigDecimal rentAmount,
        @RequestParam(required = false) Long version,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Contract renewed = contractUseCase.renewContract(
                new ContractUseCase.RenewContractCommand(id, endDate, rentAmount, version));
            redirectAttributes.addFlashAttribute("successMessage", "Đã gia hạn hợp đồng đến ngày "
                + UiText.INSTANCE.date(renewed.getEndDate()) + ".");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    @PostMapping("/{id}/terminate")
    public String terminateContract(
        @PathVariable UUID id,
        @RequestParam(name = "requiresMaintenance", defaultValue = "false") boolean requiresMaintenance,
        @RequestParam(required = false) String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Contract terminated = contractUseCase.terminateEarly(id, requiresMaintenance, reason);
            // Lấy trạng thái thật: phòng đang sửa dở vẫn giữ Bảo trì dù không chọn "cần kiểm tra bảo trì"
            RoomStatus roomStatus = roomRepository.findById(terminated.getRoomId()).map(Room::getStatus)
                .orElse(requiresMaintenance ? RoomStatus.MAINTENANCE : RoomStatus.AVAILABLE);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Đã chấm dứt hợp đồng. Phòng hiện ở trạng thái " + UiText.INSTANCE.roomStatus(roomStatus) + "."
            );
        } catch (DomainException | IllegalArgumentException ex) {
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
