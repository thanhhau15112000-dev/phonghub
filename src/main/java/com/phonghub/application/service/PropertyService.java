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

public class PropertyService implements PropertyUseCase {

    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public PropertyService(
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
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
}
