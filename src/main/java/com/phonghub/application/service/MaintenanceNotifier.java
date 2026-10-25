package com.phonghub.application.service;

import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gửi thông báo trong hệ thống theo từng bước của phiếu bảo trì (#204). Mọi thông báo trỏ về {@code /maintenance/{id}}.
 * Thiếu kho dữ liệu cần thiết thì không gửi gì, để việc chuyển trạng thái phiếu không bị ảnh hưởng.
 */
public class MaintenanceNotifier {

    private final PropertyRepositoryPort propertyRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final NotificationRepositoryPort notificationRepository;

    public MaintenanceNotifier(
        PropertyRepositoryPort propertyRepository,
        StaffPropertyAssignmentPort assignmentPort,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        NotificationRepositoryPort notificationRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.assignmentPort = assignmentPort;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    /** Người báo phiếu (người thuê). */
    public int notifyTenant(MaintenanceTicket ticket, String title, String message) {
        if (notificationRepository == null || tenantRepository == null || userRepository == null
            || ticket.getRequestedByTenantId() == null) {
            return 0;
        }
        Optional<User> recipient = tenantRepository.findById(ticket.getRequestedByTenantId())
            .map(Tenant::userId)
            .filter(Objects::nonNull)
            .flatMap(userRepository::findById);
        recipient.ifPresent(user -> send(user, title, message, ticket));
        return recipient.isPresent() ? 1 : 0;
    }

    /** Chủ trọ của nhà trọ có phiếu. */
    public int notifyOwner(MaintenanceTicket ticket, String title, String message) {
        if (notificationRepository == null || propertyRepository == null || userRepository == null) {
            return 0;
        }
        Optional<User> owner = propertyRepository.findById(ticket.getPropertyId()).map(Property::ownerId)
            .filter(Objects::nonNull)
            .flatMap(userRepository::findById);
        owner.ifPresent(user -> send(user, title, message, ticket));
        return owner.isPresent() ? 1 : 0;
    }

    /** Chủ trọ và nhân viên được phân công vào nhà trọ. */
    public int notifyManagers(MaintenanceTicket ticket, String title, String message) {
        Map<UUID, User> recipients = managerRecipients(ticket);
        recipients.values().forEach(user -> send(user, title, message, ticket));
        return recipients.size();
    }

    /** Kỹ thuật viên đang được giao phiếu. */
    public int notifyTechnician(MaintenanceTicket ticket, String title, String message) {
        if (notificationRepository == null || userRepository == null || ticket.getAssignedTechnicianId() == null) {
            return 0;
        }
        Optional<User> technician = userRepository.findById(ticket.getAssignedTechnicianId());
        technician.ifPresent(user -> send(user, title, message, ticket));
        return technician.isPresent() ? 1 : 0;
    }

    /** Một người dùng cụ thể (ví dụ người lập báo giá). */
    public int notifyUser(MaintenanceTicket ticket, UUID userId, String title, String message) {
        if (notificationRepository == null || userRepository == null || userId == null) {
            return 0;
        }
        Optional<User> user = userRepository.findById(userId);
        user.ifPresent(found -> send(found, title, message, ticket));
        return user.isPresent() ? 1 : 0;
    }

    private Map<UUID, User> managerRecipients(MaintenanceTicket ticket) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        if (propertyRepository == null || userRepository == null || assignmentPort == null) {
            return recipients;
        }
        propertyRepository.findById(ticket.getPropertyId()).map(Property::ownerId)
            .filter(Objects::nonNull)
            .flatMap(userRepository::findById)
            .ifPresent(owner -> recipients.put(owner.id(), owner));
        assignmentPort.findByPropertyId(ticket.getPropertyId()).stream()
            .map(StaffPropertyAssignment::staffUserId)
            .map(userRepository::findById)
            .flatMap(Optional::stream)
            .filter(user -> user.role() == UserRole.STAFF && user.status() == User.UserStatus.ACTIVE)
            .forEach(staff -> recipients.putIfAbsent(staff.id(), staff));
        return recipients;
    }

    private void send(User user, String title, String message, MaintenanceTicket ticket) {
        if (notificationRepository == null) {
            return;
        }
        notificationRepository.save(Notification.createForUser(title, message, user,
            "/maintenance/" + ticket.getId()));
    }
}
