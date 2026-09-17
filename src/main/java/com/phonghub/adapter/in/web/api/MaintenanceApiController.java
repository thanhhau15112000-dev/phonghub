package com.phonghub.adapter.in.web.api;

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
    public ResponseEntity<List<MaintenanceTicket>> listTickets(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(maintenanceUseCase.listTicketsForProperty(propertyId));
    }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceTicket> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        MaintenanceTicket ticket = maintenanceUseCase.createTicket(new MaintenanceUseCase.CreateMaintenanceTicketCommand(
            request.roomId(),
            request.title(),
            request.description(),
            request.priority() != null ? request.priority() : MaintenancePriority.MEDIUM,
            request.setRoomMaintenance()
        ));
        return ResponseEntity.created(URI.create("/api/maintenance/" + ticket.getId())).body(ticket);
    }

    @PostMapping("/maintenance/{id}/accept")
    public ResponseEntity<MaintenanceTicket> acceptTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(maintenanceUseCase.acceptTicket(id));
    }

    @PostMapping("/maintenance/{id}/resolve")
    public ResponseEntity<MaintenanceTicket> resolveTicket(
        @PathVariable UUID id,
        @Valid @RequestBody ResolveTicketRequest request
    ) {
        return ResponseEntity.ok(maintenanceUseCase.resolveTicket(new MaintenanceUseCase.ResolveMaintenanceTicketCommand(
            id,
            request.resolutionNotes(),
            request.repairCost(),
            request.releaseRoomToAvailable()
        )));
    }

    @GetMapping("/maintenance/{id}")
    public ResponseEntity<MaintenanceTicket> getTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(maintenanceUseCase.getTicket(id));
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
