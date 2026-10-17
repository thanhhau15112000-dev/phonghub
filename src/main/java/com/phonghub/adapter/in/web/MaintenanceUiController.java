package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
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
    private final UserRepositoryPort userRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final MaintenanceAttachmentUseCase attachmentUseCase;

    public MaintenanceUiController(
        MaintenanceUseCase maintenanceUseCase,
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        CurrentUserPort currentUserPort,
        InvoiceUseCase invoiceUseCase,
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        MaintenanceAttachmentUseCase attachmentUseCase
    ) {
        this.attachmentUseCase = attachmentUseCase;
        this.assignmentPort = assignmentPort;
        this.invoiceUseCase = invoiceUseCase;
        this.userRepository = userRepository;
        this.maintenanceUseCase = maintenanceUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String listTickets(
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String propertyId,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Model model
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        List<MaintenanceTicket> tickets = new ArrayList<>();

        if (currentUser.role() == UserRole.TECHNICIAN) {
            // Kỹ thuật viên chỉ thấy phiếu được giao cho mình
            tickets.addAll(maintenanceUseCase.listAssignedTicketsForTechnician(currentUser.id()));
        } else {
            List<Property> properties = propertyUseCase.listAccessibleProperties();
            for (Property p : properties) {
                tickets.addAll(maintenanceUseCase.listTicketsForProperty(p.id()));
            }
        }

        MaintenanceListFilter filter = MaintenanceListFilter.parse(tab, status, propertyId, priority, from, to);
        List<MaintenanceTicket> common = tickets.stream().filter(filter::matchesCommon).toList();
        List<MaintenanceTicket> visible = common.stream()
            .filter(filter::matchesTab)
            .sorted(java.util.Comparator.comparing(MaintenanceTicket::getCreatedAt).reversed())
            .toList();

        // Số phiếu theo tab / trạng thái, tính theo cơ sở, mức độ, thời gian đang lọc
        List<MaintenanceTicket> openTickets = common.stream().filter(t -> !MaintenanceListFilter.isDone(t)).toList();
        java.util.Map<com.phonghub.domain.model.MaintenanceStatus, Long> openByStatus = openTickets.stream()
            .collect(java.util.stream.Collectors.groupingBy(MaintenanceTicket::getStatus, java.util.stream.Collectors.counting()));

        java.util.Map<UUID, com.phonghub.domain.model.Invoice> feeInvoices = new java.util.HashMap<>();
        for (MaintenanceTicket t : visible) {
            if (t.getStatus() == com.phonghub.domain.model.MaintenanceStatus.AWAITING_PAYMENT) {
                invoiceUseCase.findFeeInvoiceForTicket(t.getId()).ifPresent(inv -> feeInvoices.put(t.getId(), inv));
            }
        }

        List<Property> accessibleProperties = propertyUseCase.listAccessibleProperties();
        java.util.Map<UUID, String> propertyNames = new java.util.HashMap<>();
        accessibleProperties.forEach(p -> propertyNames.put(p.id(), p.name()));

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("tickets", visible);
        model.addAttribute("feeInvoices", feeInvoices);
        model.addAttribute("filter", filter);
        model.addAttribute("commonQuery", filter.commonQuery());
        model.addAttribute("properties", accessibleProperties);
        model.addAttribute("propertyNames", propertyNames);
        model.addAttribute("priorities", MaintenancePriority.values());
        model.addAttribute("openStatuses", MaintenanceListFilter.OPEN_STATUSES);
        model.addAttribute("now", java.time.Instant.now());
        model.addAttribute("openCount", openTickets.size());
        model.addAttribute("doneCount", common.size() - openTickets.size());
        model.addAttribute("openByStatus", openByStatus);
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
        @RequestParam(name = "setRoomMaintenance", defaultValue = "false") boolean setRoomMaintenance,
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
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
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
            model.addAttribute("technicianName", ticket.getAssignedTechnicianId() == null
                ? "Chưa phân công" : userRepository.findById(ticket.getAssignedTechnicianId())
                    .map(com.phonghub.domain.model.User::fullName).orElse("Không tìm thấy tài khoản"));
            model.addAttribute("room", room);
            model.addAttribute("property", property);
            model.addAttribute("technicians", assignmentPort.findByPropertyId(ticket.getPropertyId()).stream()
                .map(assignment -> userRepository.findById(assignment.staffUserId()).orElse(null))
                .filter(user -> user != null && user.role() == UserRole.TECHNICIAN)
                .toList());
            model.addAttribute("attachments", attachmentUseCase.list(id));
            java.util.List<com.phonghub.domain.model.MaintenanceTicketEvent> timeline = maintenanceUseCase.getTimeline(id);
            java.util.Map<UUID, String> actorNames = new java.util.HashMap<>();
            for (com.phonghub.domain.model.MaintenanceTicketEvent event : timeline) {
                if (event.actorId() != null && !actorNames.containsKey(event.actorId())) {
                    actorNames.put(event.actorId(), userRepository.findById(event.actorId())
                        .map(com.phonghub.domain.model.User::fullName).orElse("Tài khoản đã xóa"));
                }
            }
            model.addAttribute("timeline", timeline);
            model.addAttribute("actorNames", actorNames);
            model.addAttribute("now", java.time.Instant.now());
            model.addAttribute("liableParties", LiableParty.values());
            model.addAttribute("priorities", MaintenancePriority.values());
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("feeInvoice", invoiceUseCase.findFeeInvoiceForTicket(id).orElse(null));
            model.addAttribute("quote", maintenanceUseCase.visibleQuote(id));

            return "maintenance/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
            return "redirect:/maintenance";
        }
    }

    @PostMapping("/{id}/accept")
    public String acceptTicket(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            maintenanceUseCase.acceptTicket(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã tiếp nhận yêu cầu và chuyển sang trạng thái đang xử lý.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/attachments")
    public String uploadAttachment(
        @PathVariable UUID id,
        @RequestParam("file") MultipartFile file,
        RedirectAttributes redirectAttributes
    ) {
        try {
            attachmentUseCase.upload(id, file.getContentType(), file.getBytes());
            redirectAttributes.addFlashAttribute("successMessage", "Đã đính kèm tệp.");
        } catch (DomainException | IllegalArgumentException | java.io.IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác với phiếu này."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Void> downloadAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        java.net.URI target = attachmentUseCase.signedUrl(id, attachmentId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/{id}/attachments/{attachmentId}/delete")
    public String deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId, RedirectAttributes redirectAttributes) {
        try {
            attachmentUseCase.delete(id, attachmentId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa tệp đính kèm.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác với phiếu này."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/assign")
    public String assignTechnician(
        @PathVariable UUID id,
        @RequestParam UUID technicianId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            maintenanceUseCase.assignTechnician(id, technicianId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã giao yêu cầu cho kỹ thuật viên.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/reject")
    public String rejectTicket(
        @PathVariable UUID id,
        @RequestParam String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            maintenanceUseCase.rejectTicket(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu và thông báo cho người thuê.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/edit")
    public String updateTicket(
        @PathVariable UUID id,
        @RequestParam String title,
        @RequestParam String description,
        @RequestParam(required = false) com.phonghub.domain.model.MaintenancePriority priority,
        RedirectAttributes redirectAttributes
    ) {
        try {
            maintenanceUseCase.updateTicket(new MaintenanceUseCase.UpdateMaintenanceTicketCommand(
                id, title, description, priority));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật nội dung phiếu bảo trì.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn không có quyền sửa phiếu bảo trì này." : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancelTicket(
        @PathVariable UUID id,
        @RequestParam(required = false) String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            maintenanceUseCase.cancelTicket(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy phiếu bảo trì.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn không có quyền hủy phiếu bảo trì này." : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/quote")
    public String quoteTicket(
        @PathVariable UUID id,
        @RequestParam(required = false) BigDecimal amount,
        @RequestParam(required = false) String note,
        RedirectAttributes redirectAttributes
    ) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.quoteTicket(id, amount, note),
            "Đã lưu báo giá sửa chữa.");
    }

    @PostMapping("/{id}/verify")
    public String verifyTicket(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.verifyTicket(id),
            "Cảm ơn bạn đã xác nhận. Phiếu bảo trì đã đóng.");
    }

    @PostMapping("/{id}/reopen")
    public String reopenTicket(
        @PathVariable UUID id,
        @RequestParam(required = false) String feedback,
        RedirectAttributes redirectAttributes
    ) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.reopenTicket(id, feedback),
            "Đã gửi phản hồi chưa đạt. Phiếu được mở lại để sửa tiếp.");
    }

    /** Thực hiện một thao tác trên phiếu rồi quay lại trang chi tiết kèm thông báo thành công hoặc lỗi. */
    private String act(UUID id, RedirectAttributes redirectAttributes, Runnable action, String successMessage) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
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
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    @PostMapping("/{id}/resolve")
    public String resolveTicket(
        @PathVariable UUID id,
        @RequestParam String resolutionNotes,
        @RequestParam(defaultValue = "0") BigDecimal repairCost,
        @RequestParam(name = "releaseRoomToAvailable", defaultValue = "false") boolean releaseRoomToAvailable,
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
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }
}
