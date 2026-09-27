package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.ContractResponse;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.model.Contract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ContractApiController {

    private final ContractUseCase contractUseCase;
    private final CurrentUserPort currentUserPort;

    public ContractApiController(ContractUseCase contractUseCase, CurrentUserPort currentUserPort) {
        this.contractUseCase = contractUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping("/properties/{propertyId}/contracts")
    public ResponseEntity<List<ContractResponse>> listContracts(@PathVariable UUID propertyId) {
        List<ContractResponse> list = contractUseCase.listContractsForProperty(propertyId).stream()
            .map(ContractResponse::from)
            .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/contracts")
    public ResponseEntity<ContractResponse> createContract(@Valid @RequestBody CreateContractRequest request) {
        Contract contract = contractUseCase.createContract(new ContractUseCase.CreateContractCommand(
            request.propertyId(),
            request.roomId(),
            request.primaryTenantFullName(),
            request.primaryTenantIdCard(),
            request.primaryTenantPhone(),
            request.primaryTenantEmail(),
            request.primaryTenantUserId(),
            request.depositAmount(),
            request.rentAmount(),
            request.startDate(),
            request.endDate(),
            request.paymentDay()
        ));
        ContractResponse response = ContractResponse.from(contract);
        return ResponseEntity.created(URI.create("/api/contracts/" + response.id())).body(response);
    }

    @PostMapping("/contracts/{id}/activate")
    public ResponseEntity<ContractResponse> activateContract(@PathVariable UUID id) {
        Contract contract = contractUseCase.activateContract(id);
        return ResponseEntity.ok(ContractResponse.from(contract));
    }

    @PostMapping("/contracts/{id}/terminate")
    public ResponseEntity<ContractResponse> terminateContract(
        @PathVariable UUID id,
        @RequestParam(name = "requiresMaintenance", defaultValue = "false") boolean requiresMaintenance
    ) {
        Contract contract = contractUseCase.terminateContract(id, requiresMaintenance);
        return ResponseEntity.ok(ContractResponse.from(contract));
    }

    @GetMapping("/contracts/{id}")
    public ResponseEntity<ContractResponse> getContract(@PathVariable UUID id) {
        Contract contract = contractUseCase.getContract(id);
        return ResponseEntity.ok(ContractResponse.from(contract));
    }

    @GetMapping("/contracts/my-active")
    public ResponseEntity<ContractResponse> getMyActiveContract() {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        return contractUseCase.getActiveContractForTenant(currentUser.id())
            .map(ContractResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/contracts/{id}/occupants")
    public ResponseEntity<ContractResponse.ContractOccupantResponse> addOccupant(
        @PathVariable UUID id,
        @Valid @RequestBody AddOccupantRequest request
    ) {
        com.phonghub.domain.model.ContractOccupant occupant = contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
            id,
            request.fullName(),
            request.identityCardNumber(),
            request.phone(),
            request.email(),
            request.permanentAddress(),
            request.checkInDate() != null ? request.checkInDate() : LocalDate.now(),
            request.createAccount()
        ));
        ContractResponse.ContractOccupantResponse response = ContractResponse.ContractOccupantResponse.from(occupant);
        return ResponseEntity.created(URI.create("/api/contracts/" + id + "/occupants/" + response.id())).body(response);
    }

    public record AddOccupantRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        String fullName,

        @NotBlank(message = "Số CCCD không được để trống")
        String identityCardNumber,

        @NotBlank(message = "Số điện thoại không được để trống")
        String phone,

        String email,

        String permanentAddress,

        LocalDate checkInDate,

        boolean createAccount
    ) {}

    @PostMapping("/contracts/{contractId}/occupants/{tenantId}/check-out")
    public ResponseEntity<ContractResponse.ContractOccupantResponse> checkOutOccupant(
        @PathVariable UUID contractId,
        @PathVariable UUID tenantId,
        @RequestBody(required = false) CheckOutOccupantRequest request
    ) {
        LocalDate checkOutDate = request != null && request.checkOutDate() != null
            ? request.checkOutDate()
            : LocalDate.now();

        com.phonghub.domain.model.ContractOccupant occupant = contractUseCase.checkOutOccupant(
            new ContractUseCase.CheckOutOccupantCommand(contractId, tenantId, checkOutDate)
        );
        return ResponseEntity.ok(ContractResponse.ContractOccupantResponse.from(occupant));
    }

    public record CheckOutOccupantRequest(
        LocalDate checkOutDate
    ) {}

    public record CreateContractRequest(
        @NotNull(message = "Property ID is required")
        UUID propertyId,

        @NotNull(message = "Room ID is required")
        UUID roomId,

        @NotBlank(message = "Primary tenant name is required")
        String primaryTenantFullName,

        @NotBlank(message = "Primary tenant ID card is required")
        String primaryTenantIdCard,

        @NotBlank(message = "Primary tenant phone is required")
        String primaryTenantPhone,

        String primaryTenantEmail,

        UUID primaryTenantUserId,

        @NotNull(message = "Deposit amount is required")
        @DecimalMin(value = "0.0", message = "Deposit cannot be negative")
        BigDecimal depositAmount,

        @NotNull(message = "Rent amount is required")
        @DecimalMin(value = "0.01", message = "Rent must be greater than 0")
        BigDecimal rentAmount,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @Min(value = 1, message = "Payment day must be between 1 and 31")
        @Max(value = 31, message = "Payment day must be between 1 and 31")
        int paymentDay
    ) {}
}
