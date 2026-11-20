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
 * Giới hạn danh bạ chat theo nhà trọ (#345): mỗi người chỉ liên hệ được người cùng nhà trọ với mình, gồm chủ trọ, nhân
 * viên và kỹ thuật viên được phân công, và người thuê đang ở (chủ hợp đồng hoặc người ở cùng). Quan hệ này đối xứng nên
 * dùng được cho cả việc gửi lẫn nhận. Quản trị viên liên hệ được mọi người và mọi người liên hệ được quản trị viên.
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
        Set<UUID> tenantIds = new HashSet<>();
        for (UUID propertyId : propertyIdsOf(viewer)) {
            properties.findById(propertyId).map(Property::ownerId).ifPresent(reachable::add);
            for (StaffPropertyAssignment assignment : assignments.findByPropertyId(propertyId)) {
                reachable.add(assignment.staffUserId());
            }
            collectResidentTenantIds(propertyId, tenantIds);
        }
        if (!tenantIds.isEmpty()) {
            for (Tenant tenant : tenants.findAllByIds(tenantIds)) {
                if (tenant.userId() != null) {
                    reachable.add(tenant.userId());
                }
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

    private void collectResidentTenantIds(UUID propertyId, Set<UUID> tenantIds) {
        for (Contract contract : contracts.findByPropertyId(propertyId)) {
            if (!contract.isResiding()) {
                continue;
            }
            if (contract.getPrimaryTenantId() != null) {
                tenantIds.add(contract.getPrimaryTenantId());
            }
            for (ContractOccupant occupant : contract.getOccupants()) {
                if (occupant.tenantId() != null) {
                    tenantIds.add(occupant.tenantId());
                }
            }
        }
    }
}
