package com.phonghub.application;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Danh sách chọn người thuê chính khi tạo hợp đồng: admin chọn được người thuê của mọi nhà trọ, vai trò khác theo nhà trọ. */
class ContractServiceCrossPropertyTenantPickerUnitTest {

    private InMemoryContractRepository contractRepo;
    private InMemoryTenantRepository tenantRepo;
    private InMemoryUserRepository userRepo;
    private InMemoryRoomRepository roomRepo;
    private InMemoryStaffPropertyAssignmentRepository assignmentRepo;
    private LocalDemoAuthenticationAdapter auth;
    private ContractService contractService;

    private Property propertyA;
    private Property propertyB;
    private Room roomA;
    private Room roomB;

    @BeforeEach
    void setUp() {
        InMemoryPropertyRepository propertyRepo = new InMemoryPropertyRepository();
        roomRepo = new InMemoryRoomRepository();
        contractRepo = new InMemoryContractRepository();
        tenantRepo = new InMemoryTenantRepository();
        userRepo = new InMemoryUserRepository();
        assignmentRepo = new InMemoryStaffPropertyAssignmentRepository();
        auth = new LocalDemoAuthenticationAdapter();
        auth.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);
        AuthorizationService authService = new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);
        contractService = new ContractService(contractRepo, roomRepo, propertyRepo, tenantRepo, userRepo, null,
            auth, authService, null, new InMemoryHandoverRepository());

        propertyA = propertyRepo.save(property("Nhà A", LocalDemoAuthenticationAdapter.DEMO_OWNER_1.id()));
        propertyB = propertyRepo.save(property("Nhà B", LocalDemoAuthenticationAdapter.DEMO_OWNER_2.id()));
        roomA = roomRepo.save(room(propertyA, "A101"));
        roomB = roomRepo.save(room(propertyB, "B101"));
    }

    @Test
    @DisplayName("Admin thấy người thuê đã gắn với nhà trọ khác")
    void adminSeesTenantLinkedToAnotherProperty() {
        Tenant tenant = tenantAt(propertyB, "Tenant Một", User.UserStatus.ACTIVE);

        assertTrue(containsTenant(propertyA, tenant));
    }

    @Test
    @DisplayName("Admin thấy người thuê từng có hợp đồng đã kết thúc ở nhà trọ khác")
    void adminSeesTenantWithEndedContractElsewhere() {
        Tenant tenant = tenantAt(propertyB, "Tenant Hai", User.UserStatus.ACTIVE);
        Contract ended = Contract.create(propertyB.id(), roomB.getId(), tenant.id(), BigDecimal.ONE, BigDecimal.ONE,
            LocalDate.now().minusMonths(6), LocalDate.now().minusMonths(1), 5);
        contractRepo.save(ended);

        assertTrue(containsTenant(propertyA, tenant));
    }

    @Test
    @DisplayName("Chủ nhà trọ không thấy người thuê của chủ nhà trọ khác")
    void ownerStillCannotSeeTenantOfAnotherOwner() {
        Tenant tenant = tenantAt(propertyB, "Tenant Ba", User.UserStatus.ACTIVE);
        auth.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);

        assertFalse(containsTenant(propertyA, tenant));
    }

    @Test
    @DisplayName("Nhân viên không có quyền hợp đồng vẫn bị chặn")
    void staffWithoutContractPermissionIsRejected() {
        assignmentRepo.save(new StaffPropertyAssignment(UUID.randomUUID(),
            LocalDemoAuthenticationAdapter.DEMO_STAFF_1.id(), propertyA.id(), false, false, Instant.now()));
        auth.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> contractService.listAvailablePrimaryTenants(propertyA.id(), null));
    }

    @Test
    @DisplayName("Nhân viên có quyền hợp đồng chỉ chọn trong nhà trọ được phân công")
    void staffIsLimitedToAssignedProperty() {
        assignmentRepo.save(new StaffPropertyAssignment(UUID.randomUUID(),
            LocalDemoAuthenticationAdapter.DEMO_STAFF_1.id(), propertyA.id(), false, true, Instant.now()));
        Tenant inA = tenantAt(propertyA, "Tenant Mười", User.UserStatus.ACTIVE);
        Tenant inB = tenantAt(propertyB, "Tenant Mười Một", User.UserStatus.ACTIVE);
        auth.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);

        assertTrue(containsTenant(propertyA, inA));
        assertFalse(containsTenant(propertyA, inB));
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> contractService.listAvailablePrimaryTenants(propertyB.id(), null));
    }

    @Test
    @DisplayName("Người đang ở phòng khác vẫn bị loại với admin")
    void adminDoesNotSeeResidingTenant() {
        Tenant tenant = tenantAt(propertyB, "Tenant Bốn", User.UserStatus.ACTIVE);
        Contract active = Contract.create(propertyB.id(), roomB.getId(), tenant.id(), BigDecimal.ONE, BigDecimal.ONE,
            LocalDate.now().minusDays(10), LocalDate.now().plusMonths(6), 5);
        active.activate();
        contractRepo.save(active);

        assertFalse(containsTenant(propertyA, tenant));
    }

    @Test
    @DisplayName("Người bị ẩn khỏi nhà trọ này vẫn bị loại với admin")
    void adminDoesNotSeeTenantHiddenAtThisProperty() {
        Tenant tenant = tenantAt(propertyA, "Tenant Năm", User.UserStatus.ACTIVE);
        tenantRepo.setHidden(tenant.id(), propertyA.id(), Instant.now(), LocalDemoAuthenticationAdapter.DEMO_ADMIN.id());

        assertFalse(containsTenant(propertyA, tenant));
    }

    @Test
    @DisplayName("Tài khoản bị khóa hoặc hồ sơ chưa có tài khoản không được chọn")
    void adminDoesNotSeeSuspendedAccountOrProfileWithoutAccount() {
        Tenant suspended = tenantAt(propertyB, "Tenant Sáu", User.UserStatus.SUSPENDED);
        Tenant noAccount = tenantRepo.save(Tenant.create(null, "Tenant Bảy", "700000007", "0900000007", null, null));
        tenantRepo.linkToProperty(noAccount.id(), propertyB.id(), LocalDemoAuthenticationAdapter.DEMO_ADMIN.id());

        assertFalse(containsTenant(propertyA, suspended));
        assertFalse(containsTenant(propertyA, noAccount));
    }

    @Test
    @DisplayName("Admin tạo hợp đồng với người thuê của nhà trọ khác và hồ sơ được gắn vào nhà trọ này")
    void adminCanCreateContractForTenantOfAnotherProperty() {
        Tenant tenant = tenantAt(propertyB, "Tenant Tám", User.UserStatus.ACTIVE);

        Contract created = contractService.createContract(new ContractUseCase.CreateContractCommand(
            propertyA.id(), roomA.getId(), tenant.id(), new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5));

        assertTrue(created.getPrimaryTenantId().equals(tenant.id()));
        assertTrue(tenantRepo.findLinkedPropertyIds(tenant.id()).contains(propertyA.id()));
    }

    @Test
    @DisplayName("Chủ nhà trọ không tạo được hợp đồng với người thuê của chủ khác")
    void ownerCannotCreateContractForTenantOfAnotherOwner() {
        Tenant tenant = tenantAt(propertyB, "Tenant Chín", User.UserStatus.ACTIVE);
        auth.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);

        assertThrows(RuntimeException.class, () -> contractService.createContract(new ContractUseCase.CreateContractCommand(
            propertyA.id(), roomA.getId(), tenant.id(), new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5)));
    }

    private boolean containsTenant(Property property, Tenant tenant) {
        return contractService.listAvailablePrimaryTenants(property.id(), null).stream()
            .anyMatch(candidate -> candidate.id().equals(tenant.id()));
    }

    private Tenant tenantAt(Property property, String fullName, User.UserStatus status) {
        UUID userId = UUID.randomUUID();
        String slug = Integer.toString(userRepo.findAll().size() + 1);
        userRepo.save(new User(userId, "tenant" + slug, "tenant" + slug + "@test.local", fullName, "09000000" + slug,
            UserRole.TENANT, status, false, Instant.now()));
        Tenant tenant = tenantRepo.save(Tenant.create(userId, fullName, "10000000" + slug, "09000000" + slug,
            "tenant" + slug + "@test.local", null));
        tenantRepo.linkToProperty(tenant.id(), property.id(), LocalDemoAuthenticationAdapter.DEMO_ADMIN.id());
        return tenant;
    }

    private static Property property(String name, UUID ownerId) {
        return new Property(UUID.randomUUID(), name, "Địa chỉ", "", 5, ownerId, PropertyApprovalStatus.VERIFIED, null,
            Instant.now());
    }

    private static Room room(Property property, String number) {
        return Room.create(property.id(), number, 1, new BigDecimal("25.0"), new BigDecimal("3000000"), 2);
    }
}
