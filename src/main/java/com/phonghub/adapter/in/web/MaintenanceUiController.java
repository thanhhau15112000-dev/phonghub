package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
@RequestMapping("/maintenance")
public class MaintenanceUiController {

    private final MaintenanceUseCase maintenanceUseCase;
    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final CurrentUserPort currentUserPort;
    private final InvoiceUseCase invoiceUseCase;

    public MaintenanceUiController(
        MaintenanceUseCase maintenanceUseCase,
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        CurrentUserPort currentUserPort,
        InvoiceUseCase invoiceUseCase
    ) {
        this.invoiceUseCase = invoiceUseCase;
        this.maintenanceUseCase = maintenanceUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String listTickets(Model model) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        List<MaintenanceTicket> tickets = new ArrayList<>();

        if (currentUser.role() == UserRole.TECHNICIAN) {
            tickets.addAll(maintenanceUseCase.listAssignedTicketsForTechnician(currentUser.id()));
            // Also include unassigned tickets in technician's assigned properties
            List<Property> properties = propertyUseCase.listAccessibleProperties();
            for (Property p : properties) {
                for (MaintenanceTicket t : maintenanceUseCase.listTicketsForProperty(p.id())) {
                    if (!tickets.contains(t)) {
                        tickets.add(t);
                    }
                }
            }
        } else {
            List<Property> properties = propertyUseCase.listAccessibleProperties();
            for (Property p : properties) {
                tickets.addAll(maintenanceUseCase.listTicketsForProperty(p.id()));
            }
        }

        java.util.Map<UUID, com.phonghub.domain.model.Invoice> feeInvoices = new java.util.HashMap<>();
        for (MaintenanceTicket t : tickets) {
            if (t.getStatus() == com.phonghub.domain.model.MaintenanceStatus.AWAITING_PAYMENT) {
                invoiceUseCase.findFeeInvoiceForTicket(t.getId()).ifPresent(inv -> feeInvoices.put(t.getId(), inv));
            }
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("tickets", tickets);
        model.addAttribute("feeInvoices", feeInvoices);
        return "maintenance/list";
    }

    @GetMapping("/new")
    public String newTicketForm(
        @RequestParam(required = false) UUID roomId,
        Model model
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        List<Property> properties = propertyUseCase.listAccessibleProperties();
        List<Room> rooms = new ArrayList<>();
        for (Property p : properties) {
            rooms.addAll(roomUseCase.listRoomsForProperty(p.id()));
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("rooms", rooms);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("priorities", MaintenancePriority.values());
        model.addAttribute("causes", MaintenanceCause.values());

        return "maintenance/new";
    }

    @PostMapping
    public String createTicket(
        @RequestParam UUID roomId,
        @RequestParam String title,
        @RequestParam String description,
        @RequestParam(defaultValue = "MEDIUM") MaintenancePriority priority,
        @RequestParam(name = "setRoomMaintenance", defaultValue = "true") boolean setRoomMaintenance,
        @RequestParam(required = false) MaintenanceCause cause,
        @RequestParam(required = false) String causeDetail,
        RedirectAttributes redirectAttributes
    ) {
        if (cause == null && currentUserPort.getCurrentUser().role() == UserRole.TENANT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn nguyên nhân sự cố.");
            return "redirect:/maintenance/new?roomId=" + roomId;
        }
        String fullDescription = causeDetail != null && !causeDetail.isBlank()
            ? "[" + causeDetail.trim() + "] " + description
            : description;
        try {
            MaintenanceTicket ticket = maintenanceUseCase.createTicket(new MaintenanceUseCase.CreateMaintenanceTicketCommand(
                roomId, title, fullDescription, priority, setRoomMaintenance, cause
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo yêu cầu bảo trì '" + ticket.getTitle() + "'.");
            return "redirect:/maintenance/" + ticket.getId();
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/maintenance/new?roomId=" + roomId;
        }
    }

    @GetMapping("/{id}")
    public String viewTicket(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        try {
            MaintenanceTicket ticket = maintenanceUseCase.getTicket(id);
            Room room = roomUseCase.getRoom(ticket.getRoomId());
            Property property = propertyUseCase.getProperty(ticket.getPropertyId());

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("ticket", ticket);
            model.addAttribute("room", room);
            model.addAttribute("property", property);
            model.addAttribute("liableParties", LiableParty.values());
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("feeInvoice", invoiceUseCase.findFeeInvoiceForTicket(id).orElse(null));

            return "maintenance/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/maintenance";
        }
    }

    @PostMapping("/{id}/accept")
    public String acceptTicket(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            maintenanceUseCase.acceptTicket(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã tiếp nhận yêu cầu và chuyển sang trạng thái đang xử lý.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/waive-fee")
    public String waiveFee(
        @PathVariable UUID id,
        @RequestParam String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            maintenanceUseCase.waiveRepairFee(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã miễn khoản phí sửa chữa. Chủ trọ chịu chi phí.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/resolve")
    public String resolveTicket(
        @PathVariable UUID id,
        @RequestParam String resolutionNotes,
        @RequestParam(defaultValue = "0") BigDecimal repairCost,
        @RequestParam(name = "releaseRoomToAvailable", defaultValue = "true") boolean releaseRoomToAvailable,
        @RequestParam(required = false) LiableParty liableParty,
        RedirectAttributes redirectAttributes
    ) {
        try {
            MaintenanceTicket resolved = maintenanceUseCase.resolveTicket(new MaintenanceUseCase.ResolveMaintenanceTicketCommand(
                id, resolutionNotes, repairCost, releaseRoomToAvailable, liableParty
            ));
            String message = resolved.getStatus() == com.phonghub.domain.model.MaintenanceStatus.AWAITING_PAYMENT
                ? "Đã ghi nhận chi phí sửa chữa. Phiếu chuyển sang chờ người thuê thanh toán."
                : "Đã hoàn tất xử lý yêu cầu.";
            redirectAttributes.addFlashAttribute(
                "successMessage",
                message + (releaseRoomToAvailable ? " Phòng đã được chuyển về trạng thái trống." : "")
            );
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }
}
