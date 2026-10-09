package com.phonghub.application.service;

import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.model.Tenant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TenantService implements TenantUseCase {

    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort != null ? auditPort : event -> {};
    }

    /** Chỉ ghi tên các trường thay đổi, không ghi giá trị cá nhân vào nhật ký audit. */
    private static java.util.List<String> changedFields(Tenant before, Tenant after) {
        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(before.fullName(), after.fullName())) {
            changed.add("fullName");
        }
        if (!java.util.Objects.equals(before.identityCardNumber(), after.identityCardNumber())) {
            changed.add("identityCardNumber");
        }
        if (!java.util.Objects.equals(before.phone(), after.phone())) {
            changed.add("phone");
        }
        if (!java.util.Objects.equals(before.email(), after.email())) {
            changed.add("email");
        }
        if (!java.util.Objects.equals(before.permanentAddress(), after.permanentAddress())) {
            changed.add("permanentAddress");
        }
        return changed;
    }

    @Override
    public Optional<Tenant> getTenant(UUID tenantId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenant(currentUser, tenantId);
        return tenantRepository.findById(tenantId);
    }

    @Override
    public Tenant updateTenant(UpdateTenantCommand command) {
        Tenant tenant = tenantRepository.findById(command.tenantId())
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + command.tenantId()));

        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanUpdateTenant(currentUser, tenant.id());

        String newIdCard = command.identityCardNumber() == null || command.identityCardNumber().isBlank()
            || command.identityCardNumber().startsWith("****")
            ? tenant.identityCardNumber()
            : command.identityCardNumber().trim();
        Optional<Tenant> duplicateCccd = tenantRepository.findByIdentityCardNumber(newIdCard);
        if (duplicateCccd.isPresent() && !duplicateCccd.get().id().equals(tenant.id())) {
            throw new DuplicateIdentityCardException("Số CCCD đã được sử dụng bởi một người thuê khác.");
        }

        Tenant updated = tenant.updateInfo(
            command.fullName(),
            newIdCard,
            command.phone(),
            command.email(),
            command.permanentAddress()
        );

        Tenant saved = tenantRepository.save(updated);

        Map<String, Object> details = new HashMap<>();
        details.put("tenantId", saved.id().toString());
        details.put("identityCardNumber", saved.maskedIdentityCardNumber());
        details.put("changedFields", changedFields(tenant, saved));

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "TENANT_UPDATE",
            currentUser != null ? currentUser.id() : null,
            "TENANT",
            saved.id().toString(),
            details
        ));

        return saved;
    }
}
