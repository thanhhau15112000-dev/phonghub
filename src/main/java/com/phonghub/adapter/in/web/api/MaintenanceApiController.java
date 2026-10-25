package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.MaintenanceTicketResponse;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.application.port.in.MaintenanceSubmissionUseCase;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutPolicy;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class MaintenanceApiController {

    private final MaintenanceUseCase maintenanceUseCase;
    private final MaintenanceAssignmentTimeoutPolicy timeoutPolicy;
    private final MaintenanceSubmissionUseCase submissionUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    public MaintenanceApiController(
        MaintenanceUseCase maintenanceUseCase,
        MaintenanceAssignmentTimeoutPolicy timeoutPolicy,
        MaintenanceSubmissionUseCase submissionUseCase
    ) {
        this.maintenanceUseCase = maintenanceUseCase;
        this.timeoutPolicy = timeoutPolicy;
        this.submissionUseCase = submissionUseCase;
    }

    public MaintenanceApiController(
        MaintenanceUseCase maintenanceUseCase,
        MaintenanceAssignmentTimeoutPolicy timeoutPolicy
    ) {
        this(maintenanceUseCase, timeoutPolicy, null);
    }

    private MaintenanceTicketResponse response(MaintenanceTicket ticket) {
        return MaintenanceTicketResponse.from(ticket, timeoutPolicy);
    }

    @GetMapping("/properties/{propertyId}/maintenance")
    public ResponseEntity<List<MaintenanceTicketResponse>> listTickets(@PathVariable UUID propertyId) {
        List<MaintenanceTicketResponse> list = maintenanceUseCase.listTicketsForProperty(propertyId).stream()
            .map(MaintenanceTicketResponse::from)
            .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceTicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        MaintenanceTicket ticket = maintenanceUseCase.createTicket(new MaintenanceUseCase.CreateMaintenanceTicketCommand(
            request.roomId(),
            request.title(),
            request.description(),
            request.priority() != null ? request.priority() : MaintenancePriority.MEDIUM,
            request.setRoomMaintenance()
        ));
        MaintenanceTicketResponse response = response(ticket);
        return ResponseEntity.created(URI.create("/api/maintenance/" + response.id())).body(response);
    }

    @PostMapping(value = "/maintenance", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaintenanceTicketResponse> createTicketWithAttachments(
        @RequestParam UUID roomId,
        @RequestParam String title,
        @RequestParam String description,
        @RequestParam(required = false) MaintenancePriority priority,
        @RequestParam(defaultValue = "false") boolean setRoomMaintenance,
        @RequestParam(name = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        List<MaintenanceAttachmentUseCase.AttachmentFile> uploads = new java.util.ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    uploads.add(new MaintenanceAttachmentUseCase.AttachmentFile(file.getContentType(), file.getBytes()));
                }
            }
        }
        MaintenanceTicket ticket = submissionUseCase.createTicketWithAttachments(new MaintenanceUseCase.CreateMaintenanceTicketCommand(
            roomId, title, description, priority != null ? priority : MaintenancePriority.MEDIUM, setRoomMaintenance), uploads);
        MaintenanceTicketResponse response = MaintenanceTicketResponse.from(ticket);
        return ResponseEntity.created(URI.create("/api/maintenance/" + response.id())).body(response);
    }

    @PostMapping("/maintenance/{id}/accept")
    public ResponseEntity<MaintenanceTicketResponse> acceptTicket(@PathVariable UUID id) {
        MaintenanceTicket ticket = maintenanceUseCase.acceptTicket(id);
        return ResponseEntity.ok(response(ticket));
    }

    @PostMapping("/maintenance/{id}/assign")
    public ResponseEntity<MaintenanceTicketResponse> assignTicket(
        @PathVariable UUID id,
        @Valid @RequestBody AssignTicketRequest request
    ) {
        return ResponseEntity.ok(response(
            maintenanceUseCase.assignTechnician(id, request.technicianId(), request.reason())));
    }

    @PostMapping("/maintenance/{id}/reject")
    public ResponseEntity<MaintenanceTicketResponse> rejectTicket(
        @PathVariable UUID id,
        @Valid @RequestBody RejectTicketRequest request
    ) {
        return ResponseEntity.ok(response(
            maintenanceUseCase.rejectTicket(id, request.reason())));
    }

    /** Sửa phiếu chưa xử lý (#89): người báo phiếu hoặc người quản lý nhà trọ. */
    @org.springframework.web.bind.annotation.PatchMapping("/maintenance/{id}")
    public ResponseEntity<MaintenanceTicketResponse> updateTicket(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTicketRequest request
    ) {
        return ResponseEntity.ok(response(maintenanceUseCase.updateTicket(
            new MaintenanceUseCase.UpdateMaintenanceTicketCommand(
                id, request.title(), request.description(), request.priority()))));
    }

    /** Hủy phiếu chưa xử lý (#89); lý do không bắt buộc. */
    @PostMapping("/maintenance/{id}/cancel")
    public ResponseEntity<MaintenanceTicketResponse> cancelTicket(
        @PathVariable UUID id,
        @RequestBody(required = false) CancelTicketRequest request
    ) {
        return ResponseEntity.ok(response(
            maintenanceUseCase.cancelTicket(id, request == null ? null : request.reason())));
    }

    public record UpdateTicketRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Description is required") String description,
        com.phonghub.domain.model.MaintenancePriority priority
    ) {}

    public record CancelTicketRequest(String reason) {}

    @PostMapping("/maintenance/{id}/resolve")
    public ResponseEntity<MaintenanceTicketResponse> resolveTicket(
        @PathVariable UUID id,
        @Valid @RequestBody ResolveTicketRequest request
    ) {
        MaintenanceTicket ticket = maintenanceUseCase.resolveTicket(new MaintenanceUseCase.ResolveMaintenanceTicketCommand(
            id,
            request.resolutionNotes(),
            request.repairCost(),
            request.releaseRoomToAvailable()
        ));
        return ResponseEntity.ok(response(ticket));
    }

    /** Báo giá trước khi sửa (#83): kỹ thuật viên được giao, owner/staff quản lý nhà trọ hoặc admin. */
    @PostMapping("/maintenance/{id}/quote")
    public ResponseEntity<QuoteResponse> quoteTicket(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request) {
        MaintenanceTicket ticket = maintenanceUseCase.quoteTicket(id, request.amount(), request.note());
        return ResponseEntity.ok(QuoteResponse.from(ticket.getId(), ticket.getQuote()));
    }

    /** Báo giá người xem được thấy; 204 khi chưa có hoặc người thuê không phải bên chịu phí. */
    @GetMapping("/maintenance/{id}/quote")
    public ResponseEntity<QuoteResponse> getQuote(@PathVariable UUID id) {
        MaintenanceTicket.Quote quote = maintenanceUseCase.visibleQuote(id);
        return quote == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(QuoteResponse.from(id, quote));
    }

    /** Người thuê xác nhận đã sửa đạt. */
    @PostMapping("/maintenance/{id}/verify")
    public ResponseEntity<MaintenanceTicketResponse> verifyTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(response(maintenanceUseCase.verifyTicket(id)));
    }

    /** Người thuê phản hồi chưa đạt, phiếu mở lại để sửa tiếp. */
    @PostMapping("/maintenance/{id}/reopen")
    public ResponseEntity<MaintenanceTicketResponse> reopenTicket(
        @PathVariable UUID id,
        @Valid @RequestBody ReopenTicketRequest request
    ) {
        return ResponseEntity.ok(response(maintenanceUseCase.reopenTicket(id, request.feedback())));
    }

    /** Timeline trạng thái của phiếu: ai chuyển trạng thái nào, lúc nào. */
    @GetMapping("/maintenance/{id}/timeline")
    public ResponseEntity<List<TimelineEntry>> timeline(@PathVariable UUID id) {
        return ResponseEntity.ok(maintenanceUseCase.getTimeline(id).stream()
            .map(event -> new TimelineEntry(event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus().name(), event.actorId(), event.note(), event.at()))
            .toList());
    }

    @GetMapping("/maintenance/{id}")
    public ResponseEntity<MaintenanceTicketResponse> getTicket(@PathVariable UUID id) {
        MaintenanceTicket ticket = maintenanceUseCase.getTicket(id);
        return ResponseEntity.ok(response(ticket));
    }

    public record CreateTicketRequest(
        @NotNull(message = "Room ID is required")
        UUID roomId,

        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        MaintenancePriority priority,

        boolean setRoomMaintenance
    ) {}

    public record TimelineEntry(String from, String to, UUID actorId, String note, java.time.Instant at) {}

    public record AssignTicketRequest(
        @NotNull(message = "Technician ID is required") UUID technicianId,
        String reason
    ) {}

    public record RejectTicketRequest(@NotBlank(message = "Reason is required") String reason) {}

    public record QuoteRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0", message = "Amount cannot be negative")
        BigDecimal amount,

        String note
    ) {}

    public record QuoteResponse(UUID ticketId, BigDecimal amount, String note, UUID quotedBy, java.time.Instant quotedAt) {
        static QuoteResponse from(UUID ticketId, MaintenanceTicket.Quote quote) {
            return new QuoteResponse(ticketId, quote.amount(), quote.note(), quote.quotedBy(), quote.quotedAt());
        }
    }

    public record ReopenTicketRequest(@NotBlank(message = "Feedback is required") String feedback) {}

    public record ResolveTicketRequest(
        @NotBlank(message = "Resolution notes are required")
        String resolutionNotes,

        @DecimalMin(value = "0.0", message = "Repair cost cannot be negative")
        BigDecimal repairCost,

        boolean releaseRoomToAvailable
    ) {}
}
