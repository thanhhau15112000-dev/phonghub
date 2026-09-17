package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.MaintenanceTicketResponse;
import com.phonghub.application.port.in.MaintenanceUseCase;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MaintenanceApiController {

    private final MaintenanceUseCase maintenanceUseCase;

    public MaintenanceApiController(MaintenanceUseCase maintenanceUseCase) {
        this.maintenanceUseCase = maintenanceUseCase;
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
        MaintenanceTicketResponse response = MaintenanceTicketResponse.from(ticket);
        return ResponseEntity.created(URI.create("/api/maintenance/" + response.id())).body(response);
    }

    @PostMapping("/maintenance/{id}/accept")
    public ResponseEntity<MaintenanceTicketResponse> acceptTicket(@PathVariable UUID id) {
        MaintenanceTicket ticket = maintenanceUseCase.acceptTicket(id);
        return ResponseEntity.ok(MaintenanceTicketResponse.from(ticket));
    }

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
        return ResponseEntity.ok(MaintenanceTicketResponse.from(ticket));
    }

    @GetMapping("/maintenance/{id}")
    public ResponseEntity<MaintenanceTicketResponse> getTicket(@PathVariable UUID id) {
        MaintenanceTicket ticket = maintenanceUseCase.getTicket(id);
        return ResponseEntity.ok(MaintenanceTicketResponse.from(ticket));
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

    public record ResolveTicketRequest(
        @NotBlank(message = "Resolution notes are required")
        String resolutionNotes,

        @DecimalMin(value = "0.0", message = "Repair cost cannot be negative")
        BigDecimal repairCost,

        boolean releaseRoomToAvailable
    ) {}
}
