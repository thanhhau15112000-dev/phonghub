package com.phonghub.application.service;

import com.phonghub.domain.exception.ConflictException;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UserService implements UserUseCase {

    private final UserRepositoryPort userRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final AuditPort auditPort;

    public UserService(
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(userRepository, assignmentPort, currentUserPort, authorizationService, null, null, null);
    }

    public UserService(
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        AuditPort auditPort
    ) {
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.auditPort = auditPort != null ? auditPort : event -> {};
        this.userRepository = userRepository;
        this.assignmentPort = assignmentPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<User> listUsers() {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);
        return userRepository.findAll();
    }

    @Override
    public User getUser(UUID userId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(userId)) {
            throw new UnauthorizedPropertyAccessException("Cannot access other user's profile");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
    }

    @Override
    public StaffPropertyAssignment assignStaffToProperty(AssignStaffCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(currentUser);

        StaffPropertyAssignment assignment = new StaffPropertyAssignment(
            UUID.randomUUID(),
            command.staffUserId(),
            command.propertyId(),
            command.canCollectPayment(),
            command.canManageContracts(),
            Instant.now()
        );

        return assignmentPort.save(assignment);
    }

    @Override
    public List<StaffPropertyAssignment> getStaffAssignments(UUID staffUserId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(staffUserId)) {
            throw new UnauthorizedPropertyAccessException("Cannot access other user's assignments");
        }
        return assignmentPort.findByUserId(staffUserId);
    }

    @Override
    public Set<UUID> getAssignedPropertyIds(UUID userId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(userId)) {
            throw new UnauthorizedPropertyAccessException("Cannot access other user's assignments");
        }
        return assignmentPort.findPropertyIdsByUserId(userId);
    }

    @Override
    public User adminUpdateUser(AdminUpdateUserCommand command) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);
        if (propertyRepository == null || tenantRepository == null) {
            throw new IllegalStateException("UserService chưa được cấu hình kho nhà trọ/người thuê để sửa tài khoản");
        }
        User existing = userRepository.findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + command.userId()));

        if (caller.id().equals(existing.id())) {
            throw new ConflictException("Không tự sửa vai trò/trạng thái của chính mình; hãy dùng trang Hồ sơ để sửa thông tin.");
        }
        if (existing.role() == UserRole.ADMIN) {
            throw new ConflictException("Không sửa tài khoản quản trị viên khác tại đây.");
        }
        if (command.role() == UserRole.ADMIN) {
            throw new ConflictException("Không đổi tài khoản sang vai trò quản trị viên tại đây.");
        }
        if (command.fullName() == null || command.fullName().isBlank()) {
            throw new IllegalArgumentException("Họ và tên không được để trống.");
        }
        String phone = command.phone() == null || command.phone().isBlank() ? null : command.phone().trim();
        if (phone != null) {
            if (!phone.matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Số điện thoại không hợp lệ (phải gồm 10 chữ số bắt đầu bằng 0)");
            }
            boolean taken = userRepository.findAll().stream()
                .anyMatch(u -> !u.id().equals(existing.id()) && phone.equals(u.phone()));
            if (taken) {
                throw new ConflictException("Số điện thoại này đã được sử dụng bởi một tài khoản khác: " + phone);
            }
        }
        if (command.role() != existing.role()) {
            assertRoleCanChange(existing);
        }

        User updated = new User(existing.id(), existing.username(), existing.email(), command.fullName().trim(), phone,
            command.role(), command.status(), existing.mustChangePassword(), existing.createdAt());
        User saved = userRepository.save(updated);

        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!existing.fullName().equals(saved.fullName())) {
            changed.add("fullName");
        }
        if (!java.util.Objects.equals(existing.phone(), saved.phone())) {
            changed.add("phone");
        }
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("changedFields", changed);
        details.put("oldRole", existing.role().name());
        details.put("newRole", saved.role().name());
        details.put("oldStatus", existing.status().name());
        details.put("newStatus", saved.status().name());
        auditPort.recordEvent(AuditPort.AuditEvent.of("USER_UPDATED_BY_ADMIN", caller.id(), "USER",
            saved.id().toString(), details));
        return saved;
    }

    /** Dữ liệu gắn với vai trò cũ phải được gỡ trước, tránh tài khoản đổi vai trò mà vẫn sở hữu/được phân công. */
    private void assertRoleCanChange(User user) {
        switch (user.role()) {
            case OWNER -> {
                int owned = propertyRepository.findByOwnerId(user.id()).size();
                if (owned > 0) {
                    throw new ConflictException(String.format(
                        "Chủ trọ đang sở hữu %d nhà trọ; chuyển nhà trọ cho chủ khác trước khi đổi vai trò.", owned));
                }
            }
            case STAFF, TECHNICIAN -> {
                int assigned = assignmentPort.findByUserId(user.id()).size();
                if (assigned > 0) {
                    throw new ConflictException(String.format(
                        "Tài khoản đang được phân công ở %d nhà trọ; gỡ phân công trước khi đổi vai trò.", assigned));
                }
            }
            case TENANT -> {
                if (tenantRepository.findByUserId(user.id()).isPresent()) {
                    throw new ConflictException(
                        "Tài khoản đã gắn với hồ sơ người thuê (hợp đồng, giấy tờ); không đổi vai trò được.");
                }
            }
            default -> { }
        }
    }

    @Override
    public User updateProfile(UpdateProfileCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(command.userId())) {
            throw new UnauthorizedPropertyAccessException("Cannot modify other user's profile");
        }

        User existingUser = userRepository.findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + command.userId()));

        if (command.fullName() == null || command.fullName().isBlank()) {
            throw new IllegalArgumentException("Họ và tên không được để trống.");
        }

        String cleanPhone = (command.phone() != null && !command.phone().isBlank())
            ? command.phone().trim()
            : null;

        if (cleanPhone != null) {
            if (!cleanPhone.matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Số điện thoại không hợp lệ (phải gồm 10 chữ số bắt đầu bằng 0)");
            }
            boolean phoneTaken = userRepository.findAll().stream()
                .anyMatch(u -> !u.id().equals(command.userId()) && cleanPhone.equals(u.phone()));
            if (phoneTaken) {
                throw new IllegalArgumentException("Số điện thoại này đã được sử dụng bởi một tài khoản khác: " + cleanPhone);
            }
        }

        User updatedUser = new User(
            existingUser.id(),
            existingUser.username(),
            existingUser.email(),
            command.fullName().trim(),
            cleanPhone,
            existingUser.role(),
            existingUser.status(),
            existingUser.mustChangePassword(),
            existingUser.createdAt()
        );

        try {
            return userRepository.save(updatedUser);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không thể cập nhật hồ sơ: " + (ex.getMessage() != null ? ex.getMessage() : "Lỗi dữ liệu"));
        }
    }
}
