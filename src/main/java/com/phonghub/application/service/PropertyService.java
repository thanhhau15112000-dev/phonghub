package com.phonghub.application.service;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.model.Property;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.UserRole;
import java.util.Map;

public class PropertyService implements PropertyUseCase {

    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final RoomRepositoryPort roomRepository;

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
        this(propertyRepository, currentUserPort, authorizationService, auditPort, null);
    }

    public PropertyService(
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        RoomRepositoryPort roomRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort != null ? auditPort : event -> {};
        this.roomRepository = roomRepository;
    }

    @Override
    public Property createProperty(CreatePropertyCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanCreateProperty(currentUser);

        UUID ownerId = currentUser.role() == UserRole.OWNER ? currentUser.id() : null;
        PropertyApprovalStatus approvalStatus = currentUser.role() == UserRole.OWNER
            ? PropertyApprovalStatus.PENDING
            : PropertyApprovalStatus.VERIFIED;

        Property property = new Property(
            UUID.randomUUID(),
            command.name().trim(),
            command.address().trim(),
            // Cùng quy tắc với updateProperty: mô tả trống lưu null để danh sách hiện "—" (#91).
            command.description() == null || command.description().isBlank() ? null : command.description().trim(),
            command.totalRooms(),
            ownerId,
            approvalStatus,
            null,
            Instant.now()
        );

        return propertyRepository.save(property);
    }

    @Override
    public Property updateProperty(UpdatePropertyCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(currentUser, command.propertyId());

        Property property = propertyRepository.findById(command.propertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + command.propertyId()));

        int totalRooms = command.totalRooms() == null ? property.totalRooms() : command.totalRooms();
        // Tổng số phòng là con số khai báo; không cho thấp hơn số phòng đã tạo để danh sách và thông tin khớp nhau.
        if (roomRepository != null && command.totalRooms() != null) {
            int existingRooms = roomRepository.findByPropertyId(property.id()).size();
            if (totalRooms < existingRooms) {
                throw new IllegalArgumentException(String.format(
                    "Tổng số phòng không được nhỏ hơn số phòng đã tạo (%d phòng).", existingRooms));
            }
        }
        String description = command.description() == null || command.description().isBlank()
            ? null : command.description().trim();
        boolean coreChanged = property.changesCoreInfo(command.name(), command.address());
        boolean unchanged = !coreChanged && java.util.Objects.equals(property.description(), description)
            && property.totalRooms() == totalRooms;
        if (unchanged) {
            return property;
        }

        PropertyApprovalStatus status = property.approvalStatus();
        String rejectionReason = property.rejectionReason();
        if (currentUser.role() == UserRole.OWNER) {
            boolean resubmit = status == PropertyApprovalStatus.REJECTED
                || (status == PropertyApprovalStatus.VERIFIED && coreChanged);
            if (resubmit) {
                status = PropertyApprovalStatus.PENDING;
                rejectionReason = null;
            }
        }
        Property saved = propertyRepository.save(property.updateInfo(command.name(), command.address(),
            description, totalRooms, status, rejectionReason));

        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!property.name().equals(saved.name())) {
            changed.add("name");
        }
        if (!property.address().equals(saved.address())) {
            changed.add("address");
        }
        if (!java.util.Objects.equals(property.description(), saved.description())) {
            changed.add("description");
        }
        if (property.totalRooms() != saved.totalRooms()) {
            changed.add("totalRooms");
        }
        auditPort.recordEvent(AuditPort.AuditEvent.of("PROPERTY_UPDATED", currentUser.id(), "PROPERTY",
            saved.id().toString(), Map.of("changedFields", changed,
                "oldStatus", property.approvalStatus().name(), "newStatus", saved.approvalStatus().name())));
        return saved;
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

    @Override
    public Property rejectProperty(UUID propertyId, String reason) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống");
        }

        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));

        if (property.approvalStatus() != PropertyApprovalStatus.PENDING) {
            throw new InvalidPropertyStatusException(String.format(
                "Chỉ có thể từ chối nhà trọ đang ở trạng thái 'PENDING'. Trạng thái hiện tại: '%s'.",
                property.approvalStatus()
            ));
        }

        Property rejectedProperty = new Property(
            property.id(),
            property.name(),
            property.address(),
            property.description(),
            property.totalRooms(),
            property.ownerId(),
            PropertyApprovalStatus.REJECTED,
            reason.trim(),
            property.createdAt()
        );

        Property saved = propertyRepository.save(rejectedProperty);

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "ADMIN_REJECT_PROPERTY",
            currentUser.id(),
            "PROPERTY",
            property.id().toString(),
            Map.of(
                "oldStatus", PropertyApprovalStatus.PENDING.name(),
                "newStatus", PropertyApprovalStatus.REJECTED.name(),
                "propertyName", property.name(),
                "reason", reason.trim()
            )
        ));

        return saved;
    }
}

