package com.phonghub.application.service;

import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Gửi thông báo cá nhân theo vai trò và nhà trọ (#86): người thuê của hợp đồng, hoặc người quản lý nhà trọ (chủ trọ và
 * staff được phân công có quyền quản lý hợp đồng). Mỗi người chỉ nhận một bản dù khớp nhiều vai trò.
 */
public class NotificationDispatcher {

    private final NotificationRepositoryPort notificationRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;

    public NotificationDispatcher(
        NotificationRepositoryPort notificationRepository,
        PropertyRepositoryPort propertyRepository,
        StaffPropertyAssignmentPort assignmentPort,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository
    ) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
        this.assignmentPort = Objects.requireNonNull(assignmentPort, "assignmentPort");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    /**
     * Người thuê của hợp đồng có tài khoản: người thuê chính, và nếu {@code includeOccupants} thì cả người ở cùng chưa
     * rời đi. Trả về số người nhận.
     */
    public int notifyTenants(Contract contract, boolean includeOccupants, String title, String message, String link) {
        Stream<UUID> tenantIds = Stream.of(contract.getPrimaryTenantId());
        if (includeOccupants) {
            tenantIds = Stream.concat(tenantIds, contract.getOccupants().stream()
                .filter(o -> o.checkOutDate() == null)
                .map(ContractOccupant::tenantId));
        }
        Map<UUID, User> recipients = new LinkedHashMap<>();
        tenantIds.filter(Objects::nonNull).distinct()
            .map(tenantRepository::findById).flatMap(Optional::stream)
            .map(Tenant::userId).filter(Objects::nonNull)
            .map(userRepository::findById).flatMap(Optional::stream)
            .forEach(user -> recipients.putIfAbsent(user.id(), user));
        return send(recipients, title, message, link);
    }

    /**
     * Người quản lý nhà trọ: chủ trọ, và nếu {@code includeStaff} thì staff được phân công có quyền quản lý hợp đồng.
     * Trả về số người nhận.
     */
    public int notifyManagers(UUID propertyId, boolean includeStaff, String title, String message, String link) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        propertyRepository.findById(propertyId).map(Property::ownerId)
            .flatMap(userRepository::findById)
            .ifPresent(owner -> recipients.put(owner.id(), owner));
        if (includeStaff) {
            assignmentPort.findByPropertyId(propertyId).stream()
                .filter(StaffPropertyAssignment::canManageContracts)
                .map(StaffPropertyAssignment::staffUserId)
                .map(userRepository::findById).flatMap(Optional::stream)
                .forEach(staff -> recipients.putIfAbsent(staff.id(), staff));
        }
        return send(recipients, title, message, link);
    }

    private int send(Map<UUID, User> recipients, String title, String message, String link) {
        for (User user : recipients.values()) {
            notificationRepository.save(Notification.createForUser(title, message, user, link));
        }
        return recipients.size();
    }
}
