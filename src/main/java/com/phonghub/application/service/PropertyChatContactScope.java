package com.phonghub.application.service;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Giới hạn danh bạ chat theo quan hệ trong nhà trọ (#345), cùng phạm vi dữ liệu với {@code docs/use-cases.md} §2.1:
 *
 * <ul>
 *   <li>Quản trị viên: mọi người. Mọi vai trò đều liên hệ được quản trị viên.</li>
 *   <li>Người thuê: chủ trọ, nhân viên và kỹ thuật viên của nhà trọ đang ở.</li>
 *   <li>Kỹ thuật viên: chủ trọ và nhân viên của nhà trọ được phân công.</li>
 *   <li>Nhân viên: chủ trọ, nhân viên, kỹ thuật viên và người thuê của nhà trọ được phân công.</li>
 *   <li>Chủ trọ: nhân viên, kỹ thuật viên và người thuê của nhà trọ mình.</li>
 * </ul>
 */
public class PropertyChatContactScope implements ChatContactScope {

    private final UserRepositoryPort users;
    private final PropertyRepositoryPort properties;
    private final StaffPropertyAssignmentPort assignments;
    private final ContractRepositoryPort contracts;
    private final TenantRepositoryPort tenants;

    public PropertyChatContactScope(
        UserRepositoryPort users,
        PropertyRepositoryPort properties,
        StaffPropertyAssignmentPort assignments,
        ContractRepositoryPort contracts,
        TenantRepositoryPort tenants
    ) {
        this.users = users;
        this.properties = properties;
        this.assignments = assignments;
        this.contracts = contracts;
        this.tenants = tenants;
    }

    @Override
    public Set<UUID> reachableUserIds(CurrentUser viewer) {
        List<User> all = users.findAll();
        Map<UUID, User> byId = all.stream().collect(Collectors.toMap(User::id, Function.identity(), (a, b) -> a));
        if (viewer.role() == UserRole.ADMIN) {
            Set<UUID> everyone = new HashSet<>(byId.keySet());
            everyone.remove(viewer.id());
            return everyone;
        }
        Set<UUID> reachable = new HashSet<>();
        all.stream().filter(u -> u.role() == UserRole.ADMIN).forEach(u -> reachable.add(u.id()));
        for (UUID propertyId : propertyIdsOf(viewer)) {
            if (viewer.role() != UserRole.OWNER) {
                properties.findById(propertyId).map(Property::ownerId).ifPresent(reachable::add);
            }
            for (StaffPropertyAssignment assignment : assignments.findByPropertyId(propertyId)) {
                User member = byId.get(assignment.staffUserId());
                // Kỹ thuật viên chỉ làm việc với chủ trọ và nhân viên, không nhắn riêng cho kỹ thuật viên khác.
                if (member != null && !(viewer.role() == UserRole.TECHNICIAN && member.role() == UserRole.TECHNICIAN)) {
                    reachable.add(member.id());
                }
            }
            if (viewer.role() == UserRole.OWNER || viewer.role() == UserRole.STAFF) {
                addResidents(propertyId, reachable);
            }
        }
        reachable.remove(viewer.id());
        reachable.retainAll(byId.keySet());
        return reachable;
    }

    private Set<UUID> propertyIdsOf(CurrentUser viewer) {
        return switch (viewer.role()) {
            case OWNER -> properties.findByOwnerId(viewer.id()).stream().map(Property::id).collect(Collectors.toSet());
            case STAFF, TECHNICIAN -> assignments.findPropertyIdsByUserId(viewer.id());
            case TENANT -> tenants.findByUserId(viewer.id()).map(this::residingPropertyIds).orElse(Set.of());
            default -> Set.of();
        };
    }

    private Set<UUID> residingPropertyIds(Tenant tenant) {
        Set<UUID> ids = new HashSet<>();
        for (Contract contract : contracts.findByPrimaryTenantId(tenant.id())) {
            if (contract.isResiding()) {
                ids.add(contract.getPropertyId());
            }
        }
        for (Contract contract : contracts.findByOccupantTenantId(tenant.id())) {
            if (contract.isResiding()) {
                ids.add(contract.getPropertyId());
            }
        }
        return ids;
    }

    private void addResidents(UUID propertyId, Set<UUID> reachable) {
        for (Contract contract : contracts.findByPropertyId(propertyId)) {
            if (!contract.isResiding()) {
                continue;
            }
            addTenantAccount(contract.getPrimaryTenantId(), reachable);
            for (ContractOccupant occupant : contract.getOccupants()) {
                addTenantAccount(occupant.tenantId(), reachable);
            }
        }
    }

    private void addTenantAccount(UUID tenantId, Set<UUID> reachable) {
        if (tenantId == null) {
            return;
        }
        tenants.findById(tenantId).map(Tenant::userId).ifPresent(reachable::add);
    }
}
