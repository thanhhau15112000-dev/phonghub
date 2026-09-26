package com.phonghub.application.service;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.model.Property;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.model.PropertyApprovalStatus;
import java.util.Map;

public class PropertyService implements PropertyUseCase {

    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public PropertyService(
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(propertyRepository, currentUserPort, authorizationService, event -> {});
    }

    public PropertyService(
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort != null ? auditPort : event -> {};
    }

    @Override
    public Property createProperty(CreatePropertyCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);

        Property property = new Property(
            UUID.randomUUID(),
            command.name().trim(),
            command.address().trim(),
            command.description(),
            command.totalRooms(),
            Instant.now()
        );

        return propertyRepository.save(property);
    }

    @Override
    public List<Property> listAccessibleProperties() {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Set<UUID> accessibleIds = authorizationService.getAccessiblePropertyIds(currentUser);
        return propertyRepository.findAllById(accessibleIds);
    }

    @Override
    public Property getProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, propertyId);

        return propertyRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));
    }

    @Override
    public Property verifyProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);

        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));

        if (property.approvalStatus() != PropertyApprovalStatus.PENDING) {
            throw new InvalidPropertyStatusException(String.format(
                "Chỉ có thể duyệt nhà trọ đang ở trạng thái 'PENDING'. Trạng thái hiện tại: '%s'.",
                property.approvalStatus()
            ));
        }

        Property verifiedProperty = new Property(
            property.id(),
            property.name(),
            property.address(),
            property.description(),
            property.totalRooms(),
            property.ownerId(),
            PropertyApprovalStatus.VERIFIED,
            null,
            property.createdAt()
        );

        Property saved = propertyRepository.save(verifiedProperty);

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "ADMIN_VERIFY_PROPERTY",
            currentUser.id(),
            "PROPERTY",
            property.id().toString(),
            Map.of(
                "oldStatus", PropertyApprovalStatus.PENDING.name(),
                "newStatus", PropertyApprovalStatus.VERIFIED.name(),
                "propertyName", property.name()
            )
        ));

        return saved;
    }
}
