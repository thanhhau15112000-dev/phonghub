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

    // Câu báo lỗi hiển thị cho người dùng (giao diện và API): tiếng Việt, không kèm tên hay mã nội bộ.
    public static final String MSG_LOGIN_REQUIRED = "Vui lòng đăng nhập để tiếp tục.";
    public static final String MSG_ADMIN_ONLY = "Chỉ quản trị viên mới được thực hiện thao tác này.";
    public static final String MSG_CREATE_PROPERTY = "Chỉ chủ trọ hoặc quản trị viên mới được tạo nhà trọ.";
    public static final String MSG_PROPERTY_UNAVAILABLE =
        "Không tìm thấy nhà trọ hoặc bạn không có quyền truy cập nhà trọ này.";
    public static final String MSG_NOT_OWNER = "Bạn không phải chủ của nhà trọ này nên không thể thực hiện thao tác.";
    public static final String MSG_NOT_ASSIGNED = "Bạn chưa được phân công làm việc tại nhà trọ này.";
    public static final String MSG_NOT_RESIDENT = "Bạn không có hợp đồng thuê tại nhà trọ này.";
    public static final String MSG_ROLE_DENIED = "Vai trò của bạn không được thực hiện thao tác này.";
    public static final String MSG_OWNER_OR_ADMIN = "Chỉ chủ nhà trọ hoặc quản trị viên mới được thực hiện thao tác này.";
    public static final String MSG_TENANT_NOT_MANAGED =
        "Bạn không quản lý nhà trọ nào mà người thuê này đang hoặc đã từng cư trú.";
    public static final String ADMIN_FINANCE_DENIED = "Quản trị viên không xem số liệu tài chính của nhà trọ.";
    public static final String TECHNICIAN_FINANCE_DENIED = "Kỹ thuật viên không xem số liệu tài chính của nhà trọ.";
    public static final String STAFF_FINANCE_DENIED =
        "Bạn chưa được cấp quyền thu tiền hoặc quản lý hợp đồng tại nhà trọ này nên không xem được hóa đơn.";

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
            throw new UnauthorizedPropertyAccessException(MSG_ADMIN_ONLY);
        }
    }

    public void assertCanCreateProperty(CurrentUser user) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN || user.role() == UserRole.OWNER) {
            return;
        }
        throw new UnauthorizedPropertyAccessException(MSG_CREATE_PROPERTY);
    }

    public void assertCanAccessProperty(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Property prop = propertyRepositoryPort.findById(propertyId)
                .orElseThrow(() -> new UnauthorizedPropertyAccessException(MSG_PROPERTY_UNAVAILABLE));
            if (prop.ownerId() == null || !user.id().equals(prop.ownerId())) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_OWNER);
            }
            return;
        }
        if (user.role() == UserRole.STAFF || user.role() == UserRole.TECHNICIAN) {
            if (!assignmentPort.isUserAssignedToProperty(user.id(), propertyId)) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_ASSIGNED);
            }
            return;
        }
        if (user.role() == UserRole.TENANT) {
            // Tenant can only access the property of their contract
            Set<UUID> tenantPropertyIds = getTenantPropertyIds(user.id());
            if (!tenantPropertyIds.contains(propertyId)) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_RESIDENT);
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(MSG_ROLE_DENIED);
    }

    public void assertCanManageProperty(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Property prop = propertyRepositoryPort.findById(propertyId)
                .orElseThrow(() -> new UnauthorizedPropertyAccessException(MSG_PROPERTY_UNAVAILABLE));
            if (prop.ownerId() == null || !user.id().equals(prop.ownerId())) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_OWNER);
            }
            return;
        }
        if (user.role() == UserRole.STAFF) {
            if (!assignmentPort.isUserAssignedToProperty(user.id(), propertyId)) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_ASSIGNED);
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(MSG_ROLE_DENIED);
    }

    public void assertOwnerOrAdmin(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.OWNER) {
            Property prop = propertyRepositoryPort.findById(propertyId)
                .orElseThrow(() -> new UnauthorizedPropertyAccessException(MSG_PROPERTY_UNAVAILABLE));
            if (prop.ownerId() == null || !user.id().equals(prop.ownerId())) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_OWNER);
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(MSG_OWNER_OR_ADMIN);
    }

    public void assertCanUpdateTenant(CurrentUser user, UUID tenantId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.STAFF) {
            if (tenantPropertyIds(tenantId).stream()
                .anyMatch(pId -> assignmentPort.isUserAssignedToProperty(user.id(), pId))) {
                return;
            }
            throw new UnauthorizedPropertyAccessException(MSG_TENANT_NOT_MANAGED);
        }
        if (user.role() == UserRole.OWNER) {
            Set<UUID> propertyIds = tenantPropertyIds(tenantId);

            boolean ownsProperty = propertyIds.stream()
                .map(propertyRepositoryPort::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(p -> user.id().equals(p.ownerId()));

            if (ownsProperty) {
                return;
            }

            throw new UnauthorizedPropertyAccessException(MSG_TENANT_NOT_MANAGED);
        }

        throw new UnauthorizedPropertyAccessException(
            "Chỉ chủ nhà trọ, nhân viên phụ trách hoặc quản trị viên mới được cập nhật thông tin người thuê.");
    }

    public void assertCanAccessTenant(CurrentUser user, UUID tenantId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }

        Tenant tenant = tenantRepositoryPort.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy người thuê."));

        if (user.role() == UserRole.TENANT) {
            if (tenant.userId() != null && tenant.userId().equals(user.id())) {
                return;
            }
            throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được hồ sơ của chính mình.");
        }

        Set<UUID> propertyIds = tenantPropertyIds(tenantId);

        if (user.role() == UserRole.OWNER) {
            boolean ownsProperty = propertyIds.stream()
                .map(propertyRepositoryPort::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(p -> user.id().equals(p.ownerId()));
            if (ownsProperty) {
                return;
            }
            throw new UnauthorizedPropertyAccessException(MSG_TENANT_NOT_MANAGED);
        }

        if (user.role() == UserRole.STAFF) {
            boolean isAssigned = propertyIds.stream()
                .anyMatch(pId -> assignmentPort.isUserAssignedToProperty(user.id(), pId));
            if (isAssigned) {
                return;
            }
            throw new UnauthorizedPropertyAccessException(MSG_TENANT_NOT_MANAGED);
        }

        throw new UnauthorizedPropertyAccessException(MSG_ROLE_DENIED);
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
            .anyMatch(c -> c.isResiding() && c.getRoomId().equals(roomId));
    }

    /**
     * Hồ sơ giấy tờ: admin; owner/staff của nhà trọ; hoặc chính người thuê đó. Người thuê phải có hợp đồng
     * (kể cả DRAFT) hoặc là người ở cùng tại nhà trọ trong đường dẫn. Kỹ thuật viên không có quyền.
     */
    public void assertCanAccessTenantDocuments(CurrentUser user, UUID propertyId, UUID tenantId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (propertyId == null || tenantId == null) {
            throw new IllegalArgumentException("Property and tenant are required");
        }
        switch (user.role()) {
            case ADMIN -> { }
            case OWNER -> assertCanManageProperty(user, propertyId);
            case STAFF -> {
                assertCanManageProperty(user, propertyId);
                if (!assignmentPort.find(user.id(), propertyId).map(a -> a.canViewDocuments()).orElse(false)) {
                    throw new UnauthorizedPropertyAccessException(
                        "Bạn chưa được cấp quyền xem giấy tờ người thuê tại nhà trọ này.");
                }
            }
            case TENANT -> {
                Tenant self = tenantRepositoryPort.findByUserId(user.id()).orElse(null);
                if (self == null || !self.id().equals(tenantId)) {
                    throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được giấy tờ của chính mình.");
                }
            }
            default -> throw new UnauthorizedPropertyAccessException(MSG_ROLE_DENIED);
        }
        if (!isTenantLinkedToProperty(tenantId, propertyId)) {
            throw new UnauthorizedPropertyAccessException(MSG_NOT_RESIDENT);
        }
    }

    /** Xóa hồ sơ giấy tờ chỉ dành cho người quản lý nhà trọ; người thuê không tự xóa. */
    public void assertCanDeleteTenantDocuments(CurrentUser user, UUID propertyId, UUID tenantId) {
        if (user != null && user.role() == UserRole.TENANT) {
            throw new UnauthorizedPropertyAccessException("Người thuê không được xóa giấy tờ.");
        }
        assertCanAccessTenantDocuments(user, propertyId, tenantId);
    }

    private boolean isTenantLinkedToProperty(UUID tenantId, UUID propertyId) {
        return tenantPropertyIds(tenantId).contains(propertyId);
    }

    /** Nhà trọ mà tenant thuộc về: có hợp đồng (chính hoặc ở cùng) hoặc được gắn trực tiếp khi owner/staff thêm. */
    public Set<UUID> tenantPropertyIds(UUID tenantId) {
        Set<UUID> propertyIds = new HashSet<>();
        for (Contract c : contractRepositoryPort.findByPrimaryTenantId(tenantId)) {
            propertyIds.add(c.getPropertyId());
        }
        for (Contract c : contractRepositoryPort.findByOccupantTenantId(tenantId)) {
            propertyIds.add(c.getPropertyId());
        }
        propertyIds.addAll(tenantRepositoryPort.findLinkedPropertyIds(tenantId));
        return propertyIds;
    }

    /** Người thuê chỉ xem được dữ liệu của phòng mình đang ở (hợp đồng ACTIVE, người thuê chính hoặc người ở cùng). */
    public boolean canTenantViewRoom(CurrentUser user, UUID roomId) {
        return canTenantViewTicket(user, roomId, null);
    }

    /**
     * Số liệu tiền của nhà trọ (hóa đơn, giao dịch, doanh thu, công nợ) chỉ dành cho chủ trọ, nhân viên được giao và
     * người thuê (của chính mình). Quản trị viên vận hành nền tảng (#163) và kỹ thuật viên chỉ lo sửa chữa nên không
     * xem. Phạm vi nhà trọ do {@link #assertCanViewPropertyFinance} hoặc các kiểm tra khác đảm nhận.
     */
    public void assertCanViewFinance(CurrentUser user) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            throw new UnauthorizedPropertyAccessException(ADMIN_FINANCE_DENIED);
        }
        if (user.role() == UserRole.TECHNICIAN) {
            throw new UnauthorizedPropertyAccessException(TECHNICIAN_FINANCE_DENIED);
        }
    }

    /**
     * Xem số liệu tiền của một nhà trọ: chủ nhà trọ đó; nhân viên được giao có quyền thu tiền hoặc quản lý hợp đồng
     * (quản lý hợp đồng cần thấy hóa đơn mình phát hành). Người thuê do quy tắc xem hợp đồng của họ giới hạn.
     */
    public void assertCanViewPropertyFinance(CurrentUser user, UUID propertyId) {
        assertCanViewFinance(user);
        if (user.role() == UserRole.TENANT) {
            return;
        }
        assertCanAccessProperty(user, propertyId);
        if (user.role() == UserRole.STAFF && !assignmentPort.find(user.id(), propertyId)
            .map(assignment -> assignment.canCollectPayment() || assignment.canManageContracts()).orElse(false)) {
            throw new UnauthorizedPropertyAccessException(STAFF_FINANCE_DENIED);
        }
    }

    /** Xem hóa đơn: người thuê của hợp đồng (chính hoặc ở cùng), hoặc người xem được tiền của nhà trọ đó. */
    public void assertCanViewInvoice(CurrentUser user, com.phonghub.domain.model.Invoice invoice) {
        assertCanViewFinance(user);
        Contract contract = contractRepositoryPort.findById(invoice.getContractId())
            .orElseThrow(() -> new UnauthorizedPropertyAccessException(
                "Không tìm thấy hợp đồng hoặc bạn không có quyền truy cập."));
        if (user.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepositoryPort.findByUserId(user.id());
            boolean allowed = tenantOpt.isPresent() && (
                contract.getPrimaryTenantId().equals(tenantOpt.get().id())
                    || contract.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantOpt.get().id())));
            if (!allowed) {
                throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được hóa đơn của mình.");
            }
            return;
        }
        assertCanViewPropertyFinance(user, contract.getPropertyId());
    }

    /** Hợp đồng, bàn giao, phát hành hóa đơn: staff cần được owner cấp quyền can_manage_contracts. */
    public void assertCanManageContracts(CurrentUser user, UUID propertyId) {
        assertCanManageProperty(user, propertyId);
        if (user.role() == UserRole.STAFF && !assignmentPort.find(user.id(), propertyId)
            .map(assignment -> assignment.canManageContracts()).orElse(false)) {
            throw new UnauthorizedPropertyAccessException(
                "Bạn chưa được cấp quyền quản lý hợp đồng tại nhà trọ này.");
        }
    }

    /** Quản lý phòng/tài sản: owner/admin hoặc staff được owner cấp quyền riêng. */
    public void assertCanManageRoomsAndAssets(CurrentUser user, UUID propertyId) {
        assertCanManageProperty(user, propertyId);
        if (user.role() == UserRole.STAFF && !assignmentPort.find(user.id(), propertyId)
            .map(assignment -> assignment.canManageRoomsAndAssets()).orElse(false)) {
            throw new UnauthorizedPropertyAccessException(
                "Bạn chưa được cấp quyền quản lý phòng và tài sản tại nhà trọ này.");
        }
    }

    public boolean canManageRoomsAndAssets(CurrentUser user, UUID propertyId) {
        try {
            assertCanManageRoomsAndAssets(user, propertyId);
            return true;
        } catch (UnauthorizedPropertyAccessException ex) {
            return false;
        }
    }

    /** Thu tiền: staff cần quyền can_collect_payment; admin không xem tiền (#163). */
    public void assertCanCollectPayment(CurrentUser user, UUID propertyId) {
        assertCanViewFinance(user);
        assertCanManageProperty(user, propertyId);
        if (user.role() == UserRole.STAFF && !assignmentPort.find(user.id(), propertyId)
            .map(assignment -> assignment.canCollectPayment()).orElse(false)) {
            throw new UnauthorizedPropertyAccessException(
                "Bạn chưa được cấp quyền thu tiền tại nhà trọ này.");
        }
    }

    public void assertTechnicianCanWorkOnProperty(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (user.role() == UserRole.TECHNICIAN) {
            if (!assignmentPort.isUserAssignedToProperty(user.id(), propertyId)) {
                throw new UnauthorizedPropertyAccessException(MSG_NOT_ASSIGNED);
            }
            return;
        }
        throw new UnauthorizedPropertyAccessException(
            "Chỉ kỹ thuật viên được phân công hoặc quản trị viên mới được thực hiện thao tác này.");
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
            if (c.isResiding()) {
                propertyIds.add(c.getPropertyId());
            }
        }
        List<Contract> occupantContracts = contractRepositoryPort.findByOccupantTenantId(tenant.id());
        for (Contract c : occupantContracts) {
            if (c.isResiding()) {
                propertyIds.add(c.getPropertyId());
            }
        }
        return propertyIds;
    }
}
