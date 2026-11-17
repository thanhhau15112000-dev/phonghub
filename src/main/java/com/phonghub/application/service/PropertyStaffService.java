package com.phonghub.application.service;

import com.phonghub.application.port.in.PropertyStaffUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.StaffAssignmentConflictException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PropertyStaffService implements PropertyStaffUseCase {

    private final StaffPropertyAssignmentPort assignmentPort;
    private final UserRepositoryPort userRepository;
    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public PropertyStaffService(
        StaffPropertyAssignmentPort assignmentPort,
        UserRepositoryPort userRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.assignmentPort = assignmentPort;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
    }

    @Override
    public List<Member> listMembers(UUID propertyId) {
        authorizationService.assertOwnerOrAdmin(currentUserPort.getCurrentUser(), propertyId);
        return assignmentPort.findByPropertyId(propertyId).stream()
            .map(assignment -> userRepository.findById(assignment.staffUserId())
                .map(user -> new Member(user, assignment)).orElse(null))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((Member m) -> m.user().role().name())
                .thenComparing(m -> m.user().fullName() == null ? "" : m.user().fullName()))
            .toList();
    }

    @Override
    public List<Candidate> listCandidates(UUID propertyId, String query, int limit) {
        authorizationService.assertOwnerOrAdmin(currentUserPort.getCurrentUser(), propertyId);
        Set<UUID> assigned = assignedUserIds(propertyId);
        return assignableUsers()
            .filter(user -> !assigned.contains(user.id()))
            .filter(user -> PersonLookup.matches(user, query))
            .sorted(Comparator.comparing((User user) -> user.role() == UserRole.STAFF ? 0 : 1)
                .thenComparing(user -> PersonLookup.fold(user.fullName()))
                .thenComparing(User::id))
            .limit(PersonLookup.clampLimit(limit))
            .map(user -> new Candidate(user.id(), user.fullName(), user.role(), PersonLookup.maskedContact(user)))
            .toList();
    }

    @Override
    public Member assign(AssignCommand command) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(actor, command.propertyId());

        User target = command.userId() != null
            ? selectedCandidate(command.propertyId(), command.userId())
            : typedAccount(command.usernameOrEmail());

        boolean technician = target.role() == UserRole.TECHNICIAN;
        Optional<StaffPropertyAssignment> existing = assignmentPort.find(target.id(), command.propertyId());
        StaffPropertyAssignment saved = assignmentPort.save(new StaffPropertyAssignment(
            existing.map(StaffPropertyAssignment::id).orElseGet(UUID::randomUUID),
            target.id(),
            command.propertyId(),
            !technician && command.canCollectPayment(),
            !technician && command.canManageContracts(),
            !technician && command.canViewDocuments(),
            !technician && command.canManageRoomsAndAssets(),
            existing.map(StaffPropertyAssignment::assignedAt).orElseGet(Instant::now)));
        audit(existing.isPresent() ? "STAFF_PERMISSIONS_UPDATED" : "STAFF_ASSIGNED", actor, saved, target);
        return new Member(target, saved);
    }

    @Override
    public Member updatePermissions(UUID propertyId, UUID userId, boolean canCollectPayment, boolean canManageContracts,
                                    boolean canViewDocuments, boolean canManageRoomsAndAssets) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(actor, propertyId);
        StaffPropertyAssignment current = assignmentPort.find(userId, propertyId)
            .orElseThrow(() -> new UserNotFoundException("Nhân sự này chưa được phân công vào nhà trọ"));
        User target = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản: " + userId));
        if (target.role() == UserRole.TECHNICIAN
            && (canCollectPayment || canManageContracts || canViewDocuments || canManageRoomsAndAssets)) {
            throw new IllegalArgumentException("Kỹ thuật viên không có các quyền quản lý phòng, hợp đồng, thu tiền hay giấy tờ");
        }
        StaffPropertyAssignment saved = assignmentPort.save(new StaffPropertyAssignment(
            current.id(), userId, propertyId, canCollectPayment, canManageContracts, canViewDocuments,
            canManageRoomsAndAssets, current.assignedAt()));
        audit("STAFF_PERMISSIONS_UPDATED", actor, saved, target);
        return new Member(target, saved);
    }

    @Override
    public void unassign(UUID propertyId, UUID userId) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(actor, propertyId);
        StaffPropertyAssignment current = assignmentPort.find(userId, propertyId)
            .orElseThrow(() -> new UserNotFoundException("Nhân sự này chưa được phân công vào nhà trọ"));
        User target = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản: " + userId));
        if (target.role() == UserRole.TECHNICIAN) {
            boolean hasOpenTickets = ticketRepository.findByAssignedTechnicianId(userId).stream()
                .anyMatch(ticket -> propertyId.equals(ticket.getPropertyId())
                    && (ticket.getStatus() == MaintenanceStatus.ASSIGNED
                        || ticket.getStatus() == MaintenanceStatus.IN_PROGRESS
                        || ticket.getStatus() == MaintenanceStatus.PENDING_TENANT_APPROVAL));
            if (hasOpenTickets) {
                throw new StaffAssignmentConflictException(
                    "Kỹ thuật viên còn phiếu bảo trì đang giao hoặc đang xử lý tại nhà trọ này; hãy giao lại phiếu trước khi gỡ.");
            }
        }
        assignmentPort.deleteByUserIdAndPropertyId(userId, propertyId);
        audit("STAFF_UNASSIGNED", actor, current, target);
    }

    /** Người chọn từ danh sách: phải đúng là một dòng của {@link #listCandidates}, không tin id từ trình duyệt. */
    private User selectedCandidate(UUID propertyId, UUID userId) {
        User target = userRepository.findById(userId)
            .filter(PropertyStaffService::isAssignable)
            .orElseThrow(() -> new IllegalArgumentException(
                "Người được chọn không có trong danh sách nhân viên hoặc kỹ thuật viên có thể phân công"));
        if (assignedUserIds(propertyId).contains(target.id())) {
            throw new StaffAssignmentConflictException(
                "Nhân sự này đã được phân công vào nhà trọ; chỉnh quyền ở bảng danh sách nhân sự.");
        }
        return target;
    }

    private User typedAccount(String usernameOrEmail) {
        String key = usernameOrEmail.trim();
        // Thông báo chung cho mọi trường hợp không hợp lệ để không dò được tài khoản nào tồn tại.
        return userRepository.findByUsername(key).or(() -> userRepository.findByEmail(key))
            .filter(PropertyStaffService::isAssignable)
            .orElseThrow(() -> new IllegalArgumentException(
                "Không tìm thấy tài khoản nhân viên hoặc kỹ thuật viên đang hoạt động với thông tin này"));
    }

    private Stream<User> assignableUsers() {
        return Stream.concat(userRepository.findByRole(UserRole.STAFF).stream(),
                userRepository.findByRole(UserRole.TECHNICIAN).stream())
            .filter(PropertyStaffService::isAssignable);
    }

    private static boolean isAssignable(User user) {
        return user.status() == User.UserStatus.ACTIVE
            && (user.role() == UserRole.STAFF || user.role() == UserRole.TECHNICIAN);
    }

    private Set<UUID> assignedUserIds(UUID propertyId) {
        return assignmentPort.findByPropertyId(propertyId).stream()
            .map(StaffPropertyAssignment::staffUserId)
            .collect(Collectors.toSet());
    }

    private void audit(String action, CurrentUser actor, StaffPropertyAssignment assignment, User target) {
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, actor.id(), "PROPERTY", assignment.propertyId().toString(),
            Map.of("userId", target.id().toString(), "role", target.role().name(),
                "canCollectPayment", assignment.canCollectPayment(),
                "canManageContracts", assignment.canManageContracts(),
                "canViewDocuments", assignment.canViewDocuments(),
                "canManageRoomsAndAssets", assignment.canManageRoomsAndAssets())));
    }
}
