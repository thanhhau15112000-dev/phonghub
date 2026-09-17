package com.phonghub.adapter.in.web.api;

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
    public ResponseEntity<List<Contract>> listContracts(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(contractUseCase.listContractsForProperty(propertyId));
    }

    @PostMapping("/contracts")
    public ResponseEntity<Contract> createContract(@Valid @RequestBody CreateContractRequest request) {
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
        return ResponseEntity.created(URI.create("/api/contracts/" + contract.getId())).body(contract);
    }

    @PostMapping("/contracts/{id}/activate")
    public ResponseEntity<Contract> activateContract(@PathVariable UUID id) {
        return ResponseEntity.ok(contractUseCase.activateContract(id));
    }

    @PostMapping("/contracts/{id}/terminate")
    public ResponseEntity<Contract> terminateContract(
        @PathVariable UUID id,
        @RequestParam(name = "requiresMaintenance", defaultValue = "false") boolean requiresMaintenance
    ) {
        return ResponseEntity.ok(contractUseCase.terminateContract(id, requiresMaintenance));
    }

    @GetMapping("/contracts/{id}")
    public ResponseEntity<Contract> getContract(@PathVariable UUID id) {
        return ResponseEntity.ok(contractUseCase.getContract(id));
    }

    @GetMapping("/contracts/my-active")
    public ResponseEntity<Contract> getMyActiveContract() {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        return contractUseCase.getActiveContractForTenant(currentUser.id())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

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
