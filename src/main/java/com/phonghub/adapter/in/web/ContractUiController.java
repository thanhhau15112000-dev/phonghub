package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

@Controller
@RequestMapping("/contracts")
public class ContractUiController {

    private final ContractUseCase contractUseCase;
    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;

    public ContractUiController(
        ContractUseCase contractUseCase,
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort
    ) {
        this.contractUseCase = contractUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
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

        List<Property> properties = propertyUseCase.listAccessibleProperties();
        List<Room> rooms = new ArrayList<>();
        if (propertyId != null) {
            rooms = roomUseCase.listRoomsForProperty(propertyId);
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

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("contract", contract);
            model.addAttribute("property", property);
            model.addAttribute("room", room);
            model.addAttribute("primaryTenant", primaryTenant);

            return "contracts/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts";
        }
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
}
