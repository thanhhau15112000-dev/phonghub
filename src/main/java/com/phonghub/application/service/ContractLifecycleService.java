package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractLifecycleUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractReminderPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class ContractLifecycleService implements ContractLifecycleUseCase {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContractRepositoryPort contractRepository;
    private final ContractReminderPort reminderPort;
    private final PropertyRepositoryPort propertyRepository;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final NotificationRepositoryPort notificationRepository;
    private final AuditPort auditPort;
    private final int reminderDays;

    public ContractLifecycleService(
        ContractRepositoryPort contractRepository,
        ContractReminderPort reminderPort,
        PropertyRepositoryPort propertyRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        NotificationRepositoryPort notificationRepository,
        AuditPort auditPort,
        int reminderDays
    ) {
        if (reminderDays < 0) {
            throw new IllegalArgumentException("Số ngày nhắc trước hạn không được âm");
        }
        this.contractRepository = contractRepository;
        this.reminderPort = reminderPort;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.assignmentPort = assignmentPort;
        this.notificationRepository = notificationRepository;
        this.auditPort = auditPort;
        this.reminderDays = reminderDays;
    }

    @Override
    public RunResult runDaily(LocalDate today) {
        int expired = 0;
        int reminded = 0;
        for (Contract contract : contractRepository.findAll()) {
            if (contract.isPastEndDate(today)) {
                contract.expire();
                contractRepository.save(contract);
                expired++;
                if (auditPort != null) {
                    auditPort.recordEvent(AuditPort.AuditEvent.of("CONTRACT_EXPIRED", null, "CONTRACT",
                        contract.getId().toString(), Map.of("endDate", contract.getEndDate().toString())));
                }
                notify(contract, "Hợp đồng đã hết hạn",
                    "Hợp đồng " + describe(contract) + " đã hết hạn sau ngày " + contract.getEndDate().format(DATE)
                        + ". Phòng vẫn giữ nguyên cho đến khi hoàn tất check-out.");
            } else if (contract.isActive() && withinReminderWindow(contract.getEndDate(), today)
                && reminderPort.markExpiryReminder(contract.getId(), contract.getEndDate())) {
                reminded++;
                long days = ChronoUnit.DAYS.between(today, contract.getEndDate());
                notify(contract, "Hợp đồng sắp hết hạn",
                    "Hợp đồng " + describe(contract) + " kết thúc ngày " + contract.getEndDate().format(DATE)
                        + (days == 0 ? " (hôm nay)" : " (còn " + days + " ngày)")
                        + ". Vui lòng gia hạn hoặc chuẩn bị check-out.");
            }
        }
        return new RunResult(expired, reminded);
    }

    private boolean withinReminderWindow(LocalDate endDate, LocalDate today) {
        return endDate != null && !endDate.isBefore(today) && !endDate.isAfter(today.plusDays(reminderDays));
    }

    private String describe(Contract contract) {
        String room = roomRepository.findById(contract.getRoomId()).map(Room::getRoomNumber).orElse("?");
        String property = propertyRepository.findById(contract.getPropertyId()).map(Property::name).orElse("?");
        return "phòng " + room + " - " + property;
    }

    /** Người nhận: chủ nhà, staff được quản lý hợp đồng tại nhà trọ, người thuê chính và người ở cùng có tài khoản. */
    private void notify(Contract contract, String title, String message) {
        if (notificationRepository == null) {
            return;
        }
        Map<UUID, User> recipients = new LinkedHashMap<>();
        propertyRepository.findById(contract.getPropertyId())
            .map(Property::ownerId)
            .flatMap(userRepository::findById)
            .ifPresent(owner -> recipients.put(owner.id(), owner));
        assignmentPort.findByPropertyId(contract.getPropertyId()).stream()
            .filter(StaffPropertyAssignment::canManageContracts)
            .map(StaffPropertyAssignment::staffUserId)
            .map(userRepository::findById)
            .flatMap(Optional::stream)
            .forEach(staff -> recipients.putIfAbsent(staff.id(), staff));
        Stream.concat(Stream.of(contract.getPrimaryTenantId()),
                contract.getOccupants().stream().filter(o -> o.checkOutDate() == null).map(ContractOccupant::tenantId))
            .filter(Objects::nonNull)
            .distinct()
            .map(tenantRepository::findById)
            .flatMap(Optional::stream)
            .map(Tenant::userId)
            .filter(Objects::nonNull)
            .map(userRepository::findById)
            .flatMap(Optional::stream)
            .forEach(tenant -> recipients.putIfAbsent(tenant.id(), tenant));

        for (User user : recipients.values()) {
            notificationRepository.save(Notification.createGeneral(title, message, user.id(), user.username(),
                user.fullName(), user.role()));
        }
    }
}
