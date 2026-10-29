package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.application.port.in.MaintenanceSubmissionUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutPolicy;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final MaintenanceAssignmentTimeoutPolicy timeoutPolicy;
    private final MaintenanceSubmissionUseCase submissionUseCase;

    public MaintenanceUiController(
        MaintenanceUseCase maintenanceUseCase,
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        CurrentUserPort currentUserPort,
        InvoiceUseCase invoiceUseCase,
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        MaintenanceAttachmentUseCase attachmentUseCase,
        MaintenanceAssignmentTimeoutPolicy timeoutPolicy,
        MaintenanceSubmissionUseCase submissionUseCase
    ) {
        this.timeoutPolicy = timeoutPolicy;
        this.attachmentUseCase = attachmentUseCase;
        this.submissionUseCase = submissionUseCase;
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
        model.addAttribute("roomNumbers", roomNumbers(visible));

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

    /** Số phòng của các phiếu đang hiển thị; phòng người xem không được xem thì để trống. */
    private java.util.Map<UUID, String> roomNumbers(List<MaintenanceTicket> tickets) {
        java.util.Map<UUID, String> numbers = new java.util.HashMap<>();
        for (UUID roomId : tickets.stream().map(MaintenanceTicket::getRoomId).distinct().toList()) {
            try {
                numbers.put(roomId, roomUseCase.getRoom(roomId).getRoomNumber());
            } catch (DomainException ex) {
                // không có quyền xem phòng này: hiển thị "—"
            }
        }
        return numbers;
    }

    @GetMapping("/new")
    public String newTicketForm(
        @RequestParam(required = false) UUID roomId,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!currentUser.role().canReportMaintenance()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kỹ thuật viên không tạo yêu cầu bảo trì; chỉ nhận và xử lý phiếu được giao.");
            return "redirect:/maintenance";
        }
        List<Property> properties = propertyUseCase.listAccessibleProperties();
        List<Room> rooms = new ArrayList<>();
        java.util.Map<UUID, String> propertyNames = new java.util.LinkedHashMap<>();
        for (Property p : properties) {
            List<Room> propertyRooms = roomUseCase.listRoomsForProperty(p.id());
            rooms.addAll(propertyRooms);
            if (!propertyRooms.isEmpty()) {
                propertyNames.put(p.id(), p.name());
            }
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("rooms", rooms);
        // Có phòng ở nhiều nhà trọ thì ghi kèm tên nhà để không nhầm các phòng trùng số
        model.addAttribute("propertyNames", propertyNames);
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
        @RequestParam(name = "files", required = false) List<MultipartFile> files,
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
            MaintenanceTicket ticket = submissionUseCase.createTicketWithAttachments(
                new MaintenanceUseCase.CreateMaintenanceTicketCommand(
                    roomId, title, fullDescription, priority, setRoomMaintenance, cause),
                toAttachmentFiles(files));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo yêu cầu bảo trì '" + ticket.getTitle() + "'.");
            return "redirect:/maintenance/" + ticket.getId();
        } catch (DomainException | IllegalArgumentException | java.io.IOException ex) {
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
            Instant now = Instant.now();
            model.addAttribute("technicianName", ticket.getAssignedTechnicianId() == null
                ? "Chưa phân công" : userRepository.findById(ticket.getAssignedTechnicianId())
                    .map(com.phonghub.domain.model.User::fullName).orElse("Không tìm thấy tài khoản"));
            model.addAttribute("room", room);
            model.addAttribute("property", property);
            List<com.phonghub.domain.model.User> technicians = assignmentPort.findByPropertyId(ticket.getPropertyId()).stream()
                .map(assignment -> userRepository.findById(assignment.staffUserId()).orElse(null))
                .filter(user -> user != null && user.role() == UserRole.TECHNICIAN
                    && user.status() == com.phonghub.domain.model.User.UserStatus.ACTIVE
                    && !user.id().equals(ticket.getAssignedTechnicianId()))
                .toList();
            model.addAttribute("technicians", technicians);
            model.addAttribute("assignmentElapsed", ticket.getAssignedAt() == null ? null
                : formatDuration(Duration.between(ticket.getAssignedAt(), now)));
            Instant assignmentDeadline = ticket.getAssignedAt() == null ? null
                : ticket.getAssignedAt().plus(timeoutPolicy.assignmentTimeout());
            model.addAttribute("assignmentDeadline", formatInstant(assignmentDeadline));
            boolean assignmentActive = ticket.getStatus() == com.phonghub.domain.model.MaintenanceStatus.ASSIGNED
                || ticket.getStatus() == com.phonghub.domain.model.MaintenanceStatus.IN_PROGRESS;
            model.addAttribute("assignmentActive", assignmentActive);
            model.addAttribute("assignmentTimeRemaining", assignmentDeadline == null || !assignmentActive ? null
                : !now.isBefore(assignmentDeadline)
                    ? "Đã quá mốc " + formatDuration(timeoutPolicy.assignmentTimeout())
                    : "Còn " + formatDuration(Duration.between(now, assignmentDeadline)));
            model.addAttribute("assignmentOverdue", assignmentDeadline != null && assignmentActive
                && !now.isBefore(assignmentDeadline));
            model.addAttribute("understaffedAt", formatInstant(ticket.getUnderstaffedNotifiedAt()));
            Instant autoCancelAt = ticket.getUnderstaffedNotifiedAt() == null || !assignmentActive ? null
                : ticket.getUnderstaffedNotifiedAt().plus(timeoutPolicy.understaffedCancelAfter());
            model.addAttribute("autoCancelAt", formatInstant(autoCancelAt));
            model.addAttribute("understaffedCancelAfter",
                formatDuration(timeoutPolicy.understaffedCancelAfter()));
            model.addAttribute("attachments", attachmentUseCase.list(id));
            model.addAttribute("deletableAttachmentIds", attachmentUseCase.deletableIds(id));
            // Phiếu do người thuê gửi: người quản lý và kỹ thuật viên chỉ xem nội dung (#228)
            model.addAttribute("requestLockedForViewer", ticket.getRequestedByTenantId() != null
                && currentUser.role() != UserRole.TENANT);
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
            model.addAttribute("quoteVersions", maintenanceUseCase.quoteHistory(id));
            model.addAttribute("maxNegotiationRounds", MaintenanceTicket.MAX_NEGOTIATION_ROUNDS);

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
        @RequestParam(name = "file", required = false) MultipartFile file,
        @RequestParam(name = "files", required = false) List<MultipartFile> files,
        RedirectAttributes redirectAttributes
    ) {
        try {
            List<MultipartFile> uploads = files == null ? new ArrayList<>() : new ArrayList<>(files);
            if (file != null && !file.isEmpty()) uploads.add(file);
            if (uploads.stream().noneMatch(upload -> upload != null && !upload.isEmpty())) {
                throw new IllegalArgumentException("Cần chọn ít nhất một tệp để tải lên.");
            }
            attachmentUseCase.uploadAll(id, toAttachmentFiles(uploads));
            redirectAttributes.addFlashAttribute("successMessage", "Đã đính kèm tệp.");
        } catch (DomainException | IllegalArgumentException | java.io.IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác với phiếu này."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    private static List<MaintenanceAttachmentUseCase.AttachmentFile> toAttachmentFiles(List<MultipartFile> files)
        throws java.io.IOException {
        List<MaintenanceAttachmentUseCase.AttachmentFile> result = new ArrayList<>();
        if (files == null) return result;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                result.add(new MaintenanceAttachmentUseCase.AttachmentFile(file.getContentType(), file.getBytes()));
            }
        }
        return result;
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        java.net.URI target = attachmentUseCase.signedUrl(id, attachmentId);
        if (!"https".equalsIgnoreCase(target.getScheme()) && !"http".equalsIgnoreCase(target.getScheme())) {
            // Kho trong bộ nhớ (docker/test) chỉ có link nội bộ memory://, trình duyệt không mở được
            MaintenanceAttachmentUseCase.AttachmentContent file = attachmentUseCase.content(id, attachmentId);
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(file.content());
        }
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
        @RequestParam(required = false) String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            maintenanceUseCase.assignTechnician(id, technicianId, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã giao yêu cầu cho kỹ thuật viên.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }

    private static String formatInstant(Instant value) {
        return value == null ? null : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(value);
    }

    private static String formatDuration(Duration value) {
        long totalMinutes = Math.max(0, value.toMinutes());
        long days = totalMinutes / (24 * 60);
        long hours = totalMinutes / 60 % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) {
            return hours == 0 ? days + " ngày" : days + " ngày " + hours + " giờ";
        }
        if (hours > 0) {
            return minutes == 0 ? hours + " giờ" : hours + " giờ " + minutes + " phút";
        }
        return Math.max(1, minutes) + " phút";
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

    /** Staff hoặc chủ trọ chốt/đổi nhánh: OWNER = hao mòn tự nhiên, TENANT = lỗi do người thuê (#202, #203). */
    @PostMapping("/{id}/branch")
    public String confirmBranch(
        @PathVariable UUID id,
        @RequestParam LiableParty branch,
        @RequestParam(required = false) String reason,
        RedirectAttributes redirectAttributes
    ) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.confirmBranch(id, branch, reason),
            branch == LiableParty.OWNER
                ? "Đã chốt nhánh hao mòn tự nhiên. Phiếu chờ chủ trọ duyệt."
                : "Đã chốt nhánh lỗi do người thuê. Hãy lập báo giá để người thuê phê duyệt.");
    }

    @PostMapping("/{id}/owner-approve")
    public String ownerApprove(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.ownerApprove(id),
            "Đã duyệt xử lý. Phiếu sẵn sàng giao kỹ thuật viên; chi phí do chủ trọ chịu.");
    }

    @PostMapping("/{id}/owner-reject")
    public String ownerReject(
        @PathVariable UUID id,
        @RequestParam String reason,
        RedirectAttributes redirectAttributes
    ) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.ownerReject(id, reason),
            "Đã từ chối phiếu và thông báo cho người thuê.");
    }

    /** Báo giá chi tiết: các cột itemNames/itemQuantities/itemPrices song song theo thứ tự dòng. */
    @PostMapping("/{id}/quote-detail")
    public String submitQuote(
        @PathVariable UUID id,
        @RequestParam(name = "itemNames", required = false) List<String> itemNames,
        @RequestParam(name = "itemQuantities", required = false) List<String> itemQuantities,
        @RequestParam(name = "itemPrices", required = false) List<String> itemPrices,
        @RequestParam(name = "laborAmount", required = false) BigDecimal laborAmount,
        @RequestParam(name = "note", required = false) String note,
        RedirectAttributes redirectAttributes
    ) {
        return act(id, redirectAttributes, () -> {
            List<MaintenanceQuoteVersion.Line> lines = new ArrayList<>();
            int rows = itemNames == null ? 0 : itemNames.size();
            for (int index = 0; index < rows; index++) {
                String name = itemNames.get(index);
                String quantity = index < itemQuantities.size() ? itemQuantities.get(index) : "";
                String price = index < itemPrices.size() ? itemPrices.get(index) : "";
                if ((name == null || name.isBlank()) && quantity.isBlank() && price.isBlank()) {
                    continue;
                }
                lines.add(new MaintenanceQuoteVersion.Line(name,
                    quantity.isBlank() ? 1 : Integer.parseInt(quantity.trim()),
                    price.isBlank() ? BigDecimal.ZERO : new BigDecimal(price.trim())));
            }
            maintenanceUseCase.submitQuote(new MaintenanceUseCase.SubmitQuoteCommand(id, lines, laborAmount, note));
        }, "Đã gửi báo giá cho người thuê phê duyệt.");
    }

    @PostMapping("/{id}/quote/approve")
    public String approveQuote(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.approveQuote(id),
            "Đã phê duyệt báo giá. Kỹ thuật viên có thể bắt đầu sửa.");
    }

    @PostMapping("/{id}/quote/negotiate")
    public String negotiateQuote(
        @PathVariable UUID id,
        @RequestParam String note,
        RedirectAttributes redirectAttributes
    ) {
        return act(id, redirectAttributes, () -> maintenanceUseCase.negotiateQuote(id, note),
            "Đã gửi đề nghị điều chỉnh báo giá.");
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
            String roomNote = "";
            if (releaseRoomToAvailable) {
                // Phòng còn người ở theo hợp đồng không bị đánh dấu trống: báo đúng trạng thái thật
                boolean released = roomUseCase.getRoom(resolved.getRoomId()).getStatus()
                    == com.phonghub.domain.model.RoomStatus.AVAILABLE;
                roomNote = released ? " Phòng đã được chuyển về trạng thái trống."
                    : " Phòng vẫn có người ở theo hợp đồng nên giữ nguyên trạng thái.";
            }
            redirectAttributes.addFlashAttribute("successMessage", message + roomNote);
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex instanceof UnauthorizedPropertyAccessException
                ? "Bạn chưa được cấp quyền thao tác tại nhà trọ này. Vui lòng liên hệ người quản lý."
                : ex.getMessage());
        }
        return "redirect:/maintenance/" + id;
    }
}
