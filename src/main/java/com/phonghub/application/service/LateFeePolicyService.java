package com.phonghub.application.service;

import com.phonghub.application.port.in.LateFeePolicyUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.LateFeePolicyRepositoryPort;
import com.phonghub.domain.model.LateFeePolicy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class LateFeePolicyService implements LateFeePolicyUseCase {

    private final LateFeePolicyRepositoryPort policyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public LateFeePolicyService(
        LateFeePolicyRepositoryPort policyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.policyRepository = policyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
    }

    @Override
    public LateFeePolicy getPolicy(UUID propertyId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(user, propertyId);
        return policyRepository.findByPropertyId(propertyId).orElseGet(() -> LateFeePolicy.disabled(propertyId));
    }

    @Override
    public LateFeePolicy updatePolicy(UpdateCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(user, command.propertyId());

        LateFeePolicy before = policyRepository.findByPropertyId(command.propertyId())
            .orElseGet(() -> LateFeePolicy.disabled(command.propertyId()));
        LateFeePolicy updated = new LateFeePolicy(
            command.propertyId(), command.enabled(), command.feeType(), command.feeValue(),
            command.graceDays(), command.capAmount(), Instant.now(), user.id());
        LateFeePolicy saved = policyRepository.save(updated);

        auditPort.recordEvent(AuditPort.AuditEvent.of("LATE_FEE_POLICY_UPDATED", user.id(), "PROPERTY",
            command.propertyId().toString(), Map.of("before", describe(before), "after", describe(saved))));
        return saved;
    }

    private static Map<String, Object> describe(LateFeePolicy policy) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("enabled", policy.enabled());
        state.put("feeType", policy.feeType().name());
        state.put("feeValue", policy.feeValue().toPlainString());
        state.put("graceDays", policy.graceDays());
        state.put("capAmount", policy.capAmount() == null ? null : policy.capAmount().toPlainString());
        return state;
    }
}
