package com.phonghub.application.service;

import com.phonghub.application.port.in.AuditLogUseCase;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.UserRole;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nhật ký chỉ xem, mỗi vai trò chỉ thấy sự kiện liên quan và trong quyền của mình (#250). Phạm vi được áp ở câu truy vấn,
 * còn chi tiết được che theo quyền (tiền, giấy tờ) sau khi đọc.
 */
public class AuditLogService implements AuditLogUseCase {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AuditQueryPort auditQueryPort;
    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final StaffPropertyAssignmentPort staffAssignments;

    public AuditLogService(
        AuditQueryPort auditQueryPort,
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        StaffPropertyAssignmentPort staffAssignments
    ) {
        this.auditQueryPort = auditQueryPort;
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.staffAssignments = staffAssignments;
    }

    @Override
    public List<PropertyOption> selectableProperties() {
        CurrentUser user = requireUser();
        return visibleProperties(user).stream().map(p -> new PropertyOption(p.id(), p.name())).toList();
    }

    @Override
    public List<AuditQueryPort.Entry> search(SearchCommand command) {
        CurrentUser user = requireUser();
        Set<UUID> allowed = visibleProperties(user).stream().map(Property::id).collect(Collectors.toSet());
        boolean selfOnly = user.role() == UserRole.TECHNICIAN || user.role() == UserRole.TENANT;
        if (command.propertyId() != null && user.role() != UserRole.ADMIN && !allowed.contains(command.propertyId())) {
            throw new UnauthorizedPropertyAccessException("Bạn chỉ được xem nhật ký của nhà trọ trong phạm vi của mình.");
        }
        if (selfOnly && command.actorId() != null && !command.actorId().equals(user.id())) {
            throw new UnauthorizedPropertyAccessException("Bạn chỉ được xem nhật ký thuộc phạm vi của mình.");
        }
        AuditQueryPort.Scope scope = switch (user.role()) {
            case ADMIN -> null;
            case OWNER, STAFF -> new AuditQueryPort.Scope(allowed, user.id(), null, null);
            case TECHNICIAN -> new AuditQueryPort.Scope(null, user.id(), user.id(), null);
            default -> new AuditQueryPort.Scope(null, null, null, user.id());
        };
        Set<UUID> propertyFilter = command.propertyId() != null ? Set.of(command.propertyId()) : null;
        int page = Math.max(0, command.page());
        List<AuditQueryPort.Entry> entries = auditQueryPort.search(new AuditQueryPort.Filter(
            propertyFilter,
            command.actorId(),
            command.targetType(),
            command.action(),
            command.from() == null ? null : command.from().atStartOfDay(DISPLAY_ZONE).toInstant(),
            command.to() == null ? null : command.to().plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant(),
            PAGE_SIZE + 1,
            page * PAGE_SIZE,
            scope
        ));
        return switch (user.role()) {
            case OWNER, TENANT -> entries;
            case STAFF -> maskForStaff(user, entries);
            case TECHNICIAN -> entries.stream()
                .map(e -> "MAINTENANCE_TICKET".equals(e.targetType()) ? e : withDetails(e, AuditSanitizer.maskMoney(e.details())))
                .toList();
            // Admin vẫn theo dõi được ai làm gì, nhưng không xem số liệu tiền của nhà trọ (#163).
            default -> entries.stream().map(e -> withDetails(e, AuditSanitizer.maskMoney(e.details()))).toList();
        };
    }

    /** Nhân viên thấy mọi sự kiện của nhà trọ được giao, nhưng chi tiết che theo ô quyền của từng nhà trọ. */
    private List<AuditQueryPort.Entry> maskForStaff(CurrentUser user, List<AuditQueryPort.Entry> entries) {
        Map<UUID, StaffPropertyAssignment> byProperty = new HashMap<>();
        staffAssignments.findByUserId(user.id()).forEach(a -> byProperty.put(a.propertyId(), a));
        return entries.stream().map(e -> {
            StaffPropertyAssignment grant = e.propertyId() == null ? null : byProperty.get(e.propertyId());
            String details = e.details();
            if (grant == null || !grant.canCollectPayment()) {
                details = AuditSanitizer.maskMoney(details);
            }
            if ("TENANT_DOCUMENT".equals(e.targetType()) && (grant == null || !grant.canViewDocuments())) {
                details = null;
            }
            return withDetails(e, details);
        }).toList();
    }

    private List<Property> visibleProperties(CurrentUser user) {
        return switch (user.role()) {
            case ADMIN -> propertyRepository.findAll();
            case OWNER -> propertyRepository.findByOwnerId(user.id());
            case STAFF -> staffAssignments.findPropertyIdsByUserId(user.id()).stream()
                .map(propertyRepository::findById).flatMap(java.util.Optional::stream).toList();
            default -> List.of();
        };
    }

    private static AuditQueryPort.Entry withDetails(AuditQueryPort.Entry e, String details) {
        return new AuditQueryPort.Entry(e.id(), e.action(), e.actorId(), e.actorName(), e.targetType(),
            e.targetId(), e.propertyId(), details, e.createdAt());
    }

    private CurrentUser requireUser() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        return user;
    }
}
