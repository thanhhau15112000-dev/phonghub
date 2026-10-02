package com.phonghub.application.service;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuthorizationService {

    private final StaffPropertyAssignmentPort assignmentPort;
    private final PropertyRepositoryPort propertyRepositoryPort;
    private final TenantRepositoryPort tenantRepositoryPort;
    private final ContractRepositoryPort contractRepositoryPort;

    public AuthorizationService(
        StaffPropertyAssignmentPort assignmentPort,
        PropertyRepositoryPort propertyRepositoryPort,
        TenantRepositoryPort tenantRepositoryPort,
        ContractRepositoryPort contractRepositoryPort
    ) {
        this.assignmentPort = assignmentPort;
        this.propertyRepositoryPort = propertyRepositoryPort;
        this.tenantRepositoryPort = tenantRepositoryPort;
        this.contractRepositoryPort = contractRepositoryPort;
    }

    public void assertAdmin(CurrentUser user) {
        if (user == null || user.role() != UserRole.ADMIN) {
            throw new UnauthorizedPropertyAccessException("Action requires ADMIN role");
        }
    }

    public void assertCanCreateProperty(CurrentUser user) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN || user.role() == UserRole.OWNER) {
            return;
        }
        throw new UnauthorizedPropertyAccessException(String.format(
            "User %s with role %s cannot create property. Only Admin or Owner allowed.",
            user.fullName(), user.role()
        ));
    }

    public void assertCanAccessProperty(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Property prop = propertyRepositoryPort.findById(propertyId)
                .orElseThrow(() -> new UnauthorizedPropertyAccessException("Property not found: " + propertyId));
            if (prop.ownerId() == null || !user.id().equals(prop.ownerId())) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "Owner %s does not own property %s",
                    user.fullName(), propertyId
                ));
            }
            return;
        }
        if (user.role() == UserRole.STAFF || user.role() == UserRole.TECHNICIAN) {
            if (!assignmentPort.isUserAssignedToProperty(user.id(), propertyId)) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "User %s (%s) is not assigned to property %s",
                    user.fullName(), user.role(), propertyId
                ));
            }
            return;
        }
        if (user.role() == UserRole.TENANT) {
            // Tenant can only access the property of their contract
            Set<UUID> tenantPropertyIds = getTenantPropertyIds(user.id());
            if (!tenantPropertyIds.contains(propertyId)) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "Tenant %s does not have an active occupancy in property %s",
                    user.fullName(), propertyId
                ));
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException("Access denied for role " + user.role());
    }

    public void assertCanManageProperty(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Property prop = propertyRepositoryPort.findById(propertyId)
                .orElseThrow(() -> new UnauthorizedPropertyAccessException("Property not found: " + propertyId));
            if (prop.ownerId() == null || !user.id().equals(prop.ownerId())) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "Owner %s does not own property %s",
                    user.fullName(), propertyId
                ));
            }
            return;
        }
        if (user.role() == UserRole.STAFF) {
            if (!assignmentPort.isUserAssignedToProperty(user.id(), propertyId)) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "Staff %s is not assigned to manage property %s",
                    user.fullName(), propertyId
                ));
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(String.format(
            "User %s with role %s cannot manage property %s",
            user.fullName(), user.role(), propertyId
        ));
    }

    public void assertOwnerOrAdmin(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Property prop = propertyRepositoryPort.findById(propertyId)
                .orElseThrow(() -> new UnauthorizedPropertyAccessException("Property not found: " + propertyId));
            if (prop.ownerId() == null || !user.id().equals(prop.ownerId())) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "Owner %s does not own property %s",
                    user.fullName(), propertyId
                ));
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(String.format(
            "User %s with role %s is not permitted to perform this action. Only Owner or Admin allowed.",
            user.fullName(), user.role()
        ));
    }

    public void assertCanUpdateTenant(CurrentUser user, UUID tenantId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Set<UUID> propertyIds = new HashSet<>();
            for (Contract c : contractRepositoryPort.findByPrimaryTenantId(tenantId)) {
                propertyIds.add(c.getPropertyId());
            }
            for (Contract c : contractRepositoryPort.findByOccupantTenantId(tenantId)) {
                propertyIds.add(c.getPropertyId());
            }

            boolean ownsProperty = propertyIds.stream()
                .map(propertyRepositoryPort::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(p -> user.id().equals(p.ownerId()));

            if (ownsProperty) {
                return;
            }

            throw new UnauthorizedPropertyAccessException(String.format(
                "Chủ nhà trọ '%s' không quản lý nhà trọ nào mà người thuê '%s' đang hoặc đã từng cư trú.",
                user.fullName(), tenantId
            ));
        }

        throw new UnauthorizedPropertyAccessException(String.format(
            "Người dùng '%s' với vai trò '%s' không có quyền cập nhật thông tin người thuê. Chỉ Chủ nhà trọ liên quan hoặc Quản trị viên mới được phép.",
            user.fullName(), user.role()
        ));
    }

    public void assertCanAccessTenant(CurrentUser user, UUID tenantId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }

        Tenant tenant = tenantRepositoryPort.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + tenantId));

        if (user.role() == UserRole.TENANT) {
            if (tenant.userId() != null && tenant.userId().equals(user.id())) {
                return;
            }
            throw new UnauthorizedPropertyAccessException("Tenant can only view their own tenant profile");
        }

        Set<UUID> propertyIds = new HashSet<>();
        for (Contract c : contractRepositoryPort.findByPrimaryTenantId(tenantId)) {
            propertyIds.add(c.getPropertyId());
        }
        for (Contract c : contractRepositoryPort.findByOccupantTenantId(tenantId)) {
            propertyIds.add(c.getPropertyId());
        }

        if (user.role() == UserRole.OWNER) {
            boolean ownsProperty = propertyIds.stream()
                .map(propertyRepositoryPort::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(p -> user.id().equals(p.ownerId()));
            if (ownsProperty) {
                return;
            }
            throw new UnauthorizedPropertyAccessException(String.format(
                "Chủ nhà trọ '%s' không quản lý nhà trọ nào mà người thuê '%s' đang hoặc đã từng cư trú.",
                user.fullName(), tenantId
            ));
        }

        if (user.role() == UserRole.STAFF) {
            boolean isAssigned = propertyIds.stream()
                .anyMatch(pId -> assignmentPort.isUserAssignedToProperty(user.id(), pId));
            if (isAssigned) {
                return;
            }
            throw new UnauthorizedPropertyAccessException(String.format(
                "Nhân viên '%s' không phụ trách nhà trọ nào mà người thuê '%s' đang hoặc đã từng cư trú.",
                user.fullName(), tenantId
            ));
        }

        throw new UnauthorizedPropertyAccessException(String.format(
            "Người dùng '%s' với vai trò '%s' không có quyền xem thông tin người thuê.",
            user.fullName(), user.role()
        ));
    }

    /**
     * Người thuê chỉ xem được phiếu bảo trì của phòng mình đang ở (hợp đồng ACTIVE, người thuê chính
     * hoặc người ở cùng) hoặc phiếu do chính họ tạo.
     */
    public boolean canTenantViewTicket(CurrentUser user, UUID roomId, UUID requestedByTenantId) {
        Optional<Tenant> tenantOpt = tenantRepositoryPort.findByUserId(user.id());
        if (tenantOpt.isEmpty()) {
            return false;
        }
        UUID tenantId = tenantOpt.get().id();
        if (tenantId.equals(requestedByTenantId)) {
            return true;
        }
        return java.util.stream.Stream.concat(
                contractRepositoryPort.findByPrimaryTenantId(tenantId).stream(),
                contractRepositoryPort.findByOccupantTenantId(tenantId).stream()
            )
            .anyMatch(c -> c.isActive() && c.getRoomId().equals(roomId));
    }

    /** Xem hóa đơn: người thuê của hợp đồng (chính hoặc ở cùng), hoặc người có quyền với nhà trọ. */
    public void assertCanViewInvoice(CurrentUser user, com.phonghub.domain.model.Invoice invoice) {
        Contract contract = contractRepositoryPort.findById(invoice.getContractId())
            .orElseThrow(() -> new UnauthorizedPropertyAccessException("Contract not found: " + invoice.getContractId()));
        if (user.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepositoryPort.findByUserId(user.id());
            boolean allowed = tenantOpt.isPresent() && (
                contract.getPrimaryTenantId().equals(tenantOpt.get().id())
                    || contract.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantOpt.get().id())));
            if (!allowed) {
                throw new UnauthorizedPropertyAccessException("Tenant can only view their own invoice");
            }
            return;
        }
        assertCanAccessProperty(user, contract.getPropertyId());
    }

    public void assertCanManageContracts(CurrentUser user, UUID propertyId) {
        assertCanManageProperty(user, propertyId);
    }

    public void assertTechnicianCanWorkOnProperty(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.TECHNICIAN) {
            if (!assignmentPort.isUserAssignedToProperty(user.id(), propertyId)) {
                throw new UnauthorizedPropertyAccessException(String.format(
                    "Technician %s is not assigned to property %s",
                    user.fullName(), propertyId
                ));
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(String.format(
            "User %s with role %s cannot perform technician actions",
            user.fullName(), user.role()
        ));
    }

    public Set<UUID> getAccessiblePropertyIds(CurrentUser user) {
        if (user == null) {
            return Collections.emptySet();
        }
        if (user.role() == UserRole.ADMIN) {
            return propertyRepositoryPort.findAll().stream()
                .map(Property::id)
                .collect(Collectors.toSet());
        }
        if (user.role() == UserRole.OWNER) {
            return propertyRepositoryPort.findByOwnerId(user.id()).stream()
                .map(Property::id)
                .collect(Collectors.toSet());
        }
        if (user.role() == UserRole.STAFF || user.role() == UserRole.TECHNICIAN) {
            return assignmentPort.findPropertyIdsByUserId(user.id());
        }
        if (user.role() == UserRole.TENANT) {
            return getTenantPropertyIds(user.id());
        }
        return Collections.emptySet();
    }

    private Set<UUID> getTenantPropertyIds(UUID tenantUserId) {
        Optional<Tenant> tenantOpt = tenantRepositoryPort.findByUserId(tenantUserId);
        if (tenantOpt.isEmpty()) {
            return Collections.emptySet();
        }
        Tenant tenant = tenantOpt.get();
        List<Contract> contracts = contractRepositoryPort.findByPrimaryTenantId(tenant.id());
        Set<UUID> propertyIds = new HashSet<>();
        for (Contract c : contracts) {
            if (c.isActive()) {
                propertyIds.add(c.getPropertyId());
            }
        }
        List<Contract> occupantContracts = contractRepositoryPort.findByOccupantTenantId(tenant.id());
        for (Contract c : occupantContracts) {
            if (c.isActive()) {
                propertyIds.add(c.getPropertyId());
            }
        }
        return propertyIds;
    }
}
