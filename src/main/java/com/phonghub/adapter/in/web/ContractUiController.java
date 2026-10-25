package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.ManualPaymentUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
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
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final TenantUseCase tenantUseCase;
    private final InvoiceUseCase invoiceUseCase;
    private final SepayProperties sepayProperties;
    private final HandoverUseCase handoverUseCase;
    private final AuthorizationService authorizationService;
    private final ManualPaymentUseCase manualPayments;

    public ContractUiController(
        ContractUseCase contractUseCase,
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        TenantUseCase tenantUseCase,
        InvoiceUseCase invoiceUseCase,
        SepayProperties sepayProperties,
        HandoverUseCase handoverUseCase,
        AuthorizationService authorizationService,
        ManualPaymentUseCase manualPayments
    ) {
        this.manualPayments = manualPayments;
        this.contractUseCase = contractUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.tenantUseCase = tenantUseCase;
        this.invoiceUseCase = invoiceUseCase;
        this.sepayProperties = sepayProperties;
        this.handoverUseCase = handoverUseCase;
        this.authorizationService = authorizationService;
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
        @RequestParam(required = false) UUID tenantProfileId,
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
        model.addAttribute("selectedTenantId", tenantProfileId);

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
            // getContract has already checked that a tenant is the primary tenant or a linked occupant.
            // A DRAFT contract is not a residing contract, so the usual property/room read use cases reject
            // its tenant even though they need to review the check-in before activation.
            Property property = currentUser.role() == UserRole.TENANT
                ? propertyRepository.findById(contract.getPropertyId())
                    .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + contract.getPropertyId()))
                : propertyUseCase.getProperty(contract.getPropertyId());
            Room room = currentUser.role() == UserRole.TENANT
                ? roomRepository.findById(contract.getRoomId())
                    .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + contract.getRoomId()))
                : roomUseCase.getRoom(contract.getRoomId());
            Tenant primaryTenant = tenantRepository.findById(contract.getPrimaryTenantId()).orElse(null);
            // Cùng một hàm đếm chỗ với tầng service, nên "Còn N chỗ" luôn khớp với việc chặn khi thêm người
            long currentOccupantCount = contract.residingOccupantCount(LocalDate.now());

            com.phonghub.application.port.in.HandoverUseCase.HandoverDetails checkIn = null;
            try {
                checkIn = handoverUseCase.get(com.phonghub.domain.model.HandoverType.CHECK_IN, id);
            } catch (com.phonghub.domain.exception.HandoverNotFoundException ignored) {
                // Hợp đồng mới chưa có biên bản check-in.
            } catch (UnauthorizedPropertyAccessException ignored) {
                // Người ở cùng có thể xem hợp đồng nhưng không xem bước xác nhận của người thuê chính.
            }
            com.phonghub.application.port.in.HandoverUseCase.HandoverDetails checkOut = null;
            try {
                checkOut = handoverUseCase.get(com.phonghub.domain.model.HandoverType.CHECK_OUT, id);
            } catch (com.phonghub.domain.exception.HandoverNotFoundException | UnauthorizedPropertyAccessException ignored) {
                // Chưa có check-out hoặc người xem không có quyền truy cập biên bản.
            }

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
                    remainingOccupantSlots = Math.max(0, room.getMaxOccupants() - (int) currentOccupantCount);
                    canManageOccupants = true;
                } catch (DomainException ex) {
                    // Hide occupant controls unless the use case confirms the user's contract permission.
                }
            }

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("contract", contract);
            model.addAttribute("checkIn", checkIn);
            model.addAttribute("billingCycles", contract.billingCycles());
            model.addAttribute("checkOut", checkOut);
            model.addAttribute("canManageContracts", authorizationService.canManageContracts(currentUser, contract.getPropertyId()));
            model.addAttribute("property", property);
            model.addAttribute("room", room);
            model.addAttribute("currentOccupantCount", currentOccupantCount);
            model.addAttribute("remainingCapacity", Math.max(0, room.getMaxOccupants() - (int) currentOccupantCount));
            model.addAttribute("primaryTenant", primaryTenant);
            model.addAttribute("occupantTenants", occupantTenants);
            model.addAttribute("availableOccupants", availableOccupants);
            model.addAttribute("remainingOccupantSlots", remainingOccupantSlots);
            model.addAttribute("canManageOccupants", canManageOccupants);
            List<Tenant> propertyTenantProfiles = new ArrayList<>();
            java.util.Set<UUID> busyTenantIds = new java.util.HashSet<>();
            if (canManageOccupants) {
                for (TenantUseCase.PropertyTenantView view
                    : tenantUseCase.listTenants(contract.getPropertyId(), null, false, 0, 50).items()) {
                    UUID profileId = view.tenant().id();
                    if (contract.getOccupants().stream().noneMatch(occupant -> occupant.tenantId().equals(profileId))) {
                        propertyTenantProfiles.add(view.tenant());
                        if (view.hasActiveContract()) {
                            busyTenantIds.add(profileId);
                        }
                    }
                }
            }
            model.addAttribute("tenantChoices", canManageOccupants
                ? TenantChoice.merge(propertyTenantProfiles, busyTenantIds, List.of())
                : List.<TenantChoice>of());
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
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
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
        model.addAttribute("paymentClaims", manualPayments.listForContract(contract.getId()));
        model.addAttribute("openInvoiceCount", invoices.stream().filter(Invoice::isPayable)
            .filter(invoice -> invoice.remainingAmount().signum() > 0).count());
        model.addAttribute("invoiceWarnings", invoiceUseCase.utilityWarnings(contract.getId()));
        model.addAttribute("today", today);
        model.addAttribute("payableInvoice", payableInvoice);
        model.addAttribute("canIssueInvoice", canIssueInvoice);
        // Hủy hóa đơn (#89) không phụ thuộc hợp đồng còn hiệu lực: hợp đồng đã kết thúc vẫn có thể còn kỳ lập sai.
        model.addAttribute("canVoidInvoice", currentUser != null && (
            currentUser.role() == UserRole.STAFF
                || (currentUser.role() == UserRole.OWNER && currentUser.id().equals(property.ownerId()))));
        model.addAttribute("defaultInvoicePeriod", YearMonth.from(today).toString());
        model.addAttribute("bankConfigured", sepayProperties.hasBankAccount());
        model.addAttribute("bankCode", sepayProperties.getBankCode());
        model.addAttribute("bankAccountNumber", sepayProperties.getBankAccountNumber());
        model.addAttribute("bankAccountName", sepayProperties.getBankAccountName());
        model.addAttribute("paymentQrUrl", payableInvoice != null && sepayProperties.hasBankAccount()
            ? PaymentQr.url(sepayProperties, payableInvoice)
            : null);
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
        @RequestParam(required = false) UUID tenantProfileId,
        @RequestParam(required = false) UUID tenantUserId,
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
                tenantProfileId,
                tenantUserId
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

    @PostMapping("/{id}/occupants/batch")
    public String addOccupantsBatch(
        @PathVariable UUID id,
        @RequestParam(name = "fullNames", required = false) List<String> fullNames,
        @RequestParam(name = "identityCardNumbers", required = false) List<String> identityCardNumbers,
        @RequestParam(name = "phones", required = false) List<String> phones,
        @RequestParam(name = "emails", required = false) List<String> emails,
        @RequestParam(name = "permanentAddresses", required = false) List<String> permanentAddresses,
        @RequestParam(name = "tenantProfileIds", required = false) List<String> tenantProfileIds,
        @RequestParam(name = "tenantUserIds", required = false) List<String> tenantUserIds,
        RedirectAttributes redirectAttributes
    ) {
        try {
            // Spring biến một giá trị rỗng đơn lẻ thành danh sách rỗng (một dòng chọn hồ sơ có CCCD để trống), nên đọc từng
            // cột theo vị trí thay vì so kích thước; thiếu thông tin thì AddOccupantCommand báo đúng trường còn thiếu
            int rows = fullNames == null ? 0 : fullNames.size();
            List<ContractUseCase.AddOccupantCommand> occupants = new ArrayList<>();
            for (int index = 0; index < rows; index++) {
                occupants.add(new ContractUseCase.AddOccupantCommand(id,
                    fullNames.get(index), valueAt(identityCardNumbers, index), valueAt(phones, index),
                    valueAt(emails, index), valueAt(permanentAddresses, index), LocalDate.now(),
                    parseOptionalUuid(valueAt(tenantProfileIds, index)), parseOptionalUuid(valueAt(tenantUserIds, index))));
            }
            ContractUseCase.AddOccupantsResult result = contractUseCase.addOccupants(
                new ContractUseCase.AddOccupantsCommand(id, occupants));
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm " + result.results().size()
                + " hồ sơ người thuê vào hợp đồng.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/contracts/" + id;
    }

    private static String valueAt(List<String> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    private static UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    @PostMapping("/{id}/activate")
    public String activateContract(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            handoverUseCase.prepareCheckIn(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã mở biên bản check-in. Hợp đồng chỉ có hiệu lực sau khi người thuê xác nhận và người quản lý duyệt.");
            return "redirect:/contracts/" + id + "/handover";
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
            handoverUseCase.prepareEarlyTerminationCheckout(id, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                "Đã tạo biên bản check-out kết thúc sớm. Hoàn thiện tài sản và chỉ số, rồi gửi người thuê xác nhận.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts/" + id;
        }
        return "redirect:/contracts/" + id + "/check-out";
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
