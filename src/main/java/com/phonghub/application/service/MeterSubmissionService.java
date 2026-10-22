package com.phonghub.application.service;

import com.phonghub.application.port.in.MeterSubmissionUseCase;
import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.MeterOcrPort;
import com.phonghub.application.port.out.MeterSubmissionRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UtilityNotFoundException;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MeterSubmissionService implements MeterSubmissionUseCase {

    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);

    private final MeterSubmissionRepositoryPort submissionRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final UserRepositoryPort userRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final UtilityUseCase utilityUseCase;
    private final MeterOcrPort ocrPort;
    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final BigDecimal minConfidence;
    private final BigDecimal tolerance;
    private final long maxFileSizeBytes;

    public MeterSubmissionService(
        MeterSubmissionRepositoryPort submissionRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        UserRepositoryPort userRepository,
        NotificationRepositoryPort notificationRepository,
        UtilityUseCase utilityUseCase,
        MeterOcrPort ocrPort,
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        BigDecimal minConfidence,
        BigDecimal tolerance,
        long maxFileSizeBytes
    ) {
        this.submissionRepository = submissionRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.utilityUseCase = utilityUseCase;
        this.ocrPort = ocrPort;
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.minConfidence = minConfidence;
        this.tolerance = tolerance;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public MeterSubmission submit(UUID roomId, MeterType meterType, BigDecimal declaredValue, String contentType,
                                  byte[] content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null || user.role() != UserRole.TENANT) {
            throw new UnauthorizedPropertyAccessException("Chỉ người thuê đang ở phòng mới gửi chỉ số");
        }
        Room room = findRoom(roomId);
        if (!authorizationService.canTenantViewRoom(user, roomId)) {
            throw new UnauthorizedPropertyAccessException("Bạn không ở phòng này");
        }
        if (meterType == null || declaredValue == null || declaredValue.signum() < 0) {
            throw new IllegalArgumentException("Cần chọn loại đồng hồ và nhập số chỉ số hợp lệ");
        }
        if (!FileSanitizer.isImage(contentType)) {
            throw new IllegalArgumentException("Ảnh đồng hồ phải là jpeg, png hoặc webp");
        }
        if (submissionRepository.findByRoomId(roomId).stream().anyMatch(s -> s.meterType() == meterType
            && s.status() == MeterSubmission.Status.PENDING)) {
            throw new MeterReadingConflictException("Đã có một lượt gửi chỉ số đang chờ duyệt cho đồng hồ này");
        }
        // Chỉ số không giảm: nếu đã thay đồng hồ thì người thuê báo chủ trọ, không tự gửi.
        utilityUseCase.listReadings(roomId).stream().filter(r -> r.meterType() == meterType).findFirst()
            .ifPresent(latest -> {
                if (declaredValue.compareTo(latest.currentValue()) < 0) {
                    throw new IllegalArgumentException("Chỉ số mới không được nhỏ hơn chỉ số kỳ trước ("
                        + latest.currentValue().stripTrailingZeros().toPlainString()
                        + "); nếu đã thay đồng hồ hãy báo chủ trọ");
                }
            });

        FileSanitizer.SanitizedFile photo = FileSanitizer.sanitize(contentType, content, maxFileSizeBytes);
        MeterOcrPort.OcrResult ocr = ocrPort.read(meterType, photo.contentType(), photo.content());
        BigDecimal confidence = ocr.confidence() == null ? BigDecimal.ZERO : ocr.confidence();
        MeterSubmission.MatchStatus match = MeterSubmission.match(declaredValue, ocr.value(), confidence,
            minConfidence, tolerance);
        String key = PrivateFileStorageService.objectKey(room.getPropertyId(), "meter-submissions", photo.extension());
        storagePort.put(key, photo.contentType(), photo.content());
        MeterSubmission saved;
        try {
            saved = submissionRepository.save(new MeterSubmission(UUID.randomUUID(), roomId, meterType, declaredValue,
                key, photo.contentType(), ocr.value(), confidence, ocr.engine(), match,
                MeterSubmission.Status.PENDING, user.id(), null, null, null, null, null, null));
        } catch (RuntimeException ex) {
            deleteQuietly(key);
            throw ex;
        }
        auditPort.recordEvent(AuditPort.AuditEvent.of("METER_SUBMISSION_CREATED", user.id(), "ROOM", roomId.toString(),
            Map.of("meterType", meterType.name(), "match", match.name(), "engine", String.valueOf(ocr.engine()))));
        notifyOwner(room, saved);
        return saved;
    }

    @Override
    public List<MeterSubmission> listForRoom(UUID roomId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(roomId);
        if (user != null && user.role() == UserRole.TENANT) {
            if (!authorizationService.canTenantViewRoom(user, roomId)) {
                throw new UnauthorizedPropertyAccessException("Bạn không ở phòng này");
            }
        } else {
            authorizationService.assertCanManageProperty(user, room.getPropertyId());
        }
        return submissionRepository.findByRoomId(roomId);
    }

    @Override
    public List<MeterSubmission> listForProperty(UUID propertyId, MeterSubmission.Status status) {
        authorizationService.assertCanManageProperty(currentUserPort.getCurrentUser(), propertyId);
        List<UUID> roomIds = roomRepository.findByPropertyId(propertyId).stream().map(Room::getId).toList();
        return submissionRepository.findByRoomIds(roomIds).stream()
            .filter(s -> status == null || s.status() == status).toList();
    }

    @Override
    public MeterReading accept(UUID submissionId, AcceptCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MeterSubmission submission = find(submissionId);
        Room room = findRoom(submission.roomId());
        authorizationService.assertCanManageProperty(user, room.getPropertyId());
        if (submission.status() != MeterSubmission.Status.PENDING) {
            throw new MeterReadingConflictException("Lượt gửi chỉ số này đã được xử lý (" + submission.status() + ")");
        }
        BigDecimal value = command.finalValue() != null ? command.finalValue() : submission.declaredValue();
        boolean valueChanged = value.compareTo(submission.declaredValue()) != 0;
        if (submission.matchStatus() == MeterSubmission.MatchStatus.MISMATCH && !valueChanged
            && !command.acknowledgeMismatch()) {
            throw new MeterReadingConflictException("Số khai lệch với kết quả đọc ảnh; hãy nhập số chốt hoặc xác nhận chấp nhận số khai");
        }

        // Số OCR do server tự đọc nên đáng tin; chỉ lưu khi đủ tin cậy (UNVERIFIED thì không có).
        boolean trustedOcr = submission.matchStatus() != MeterSubmission.MatchStatus.UNVERIFIED;
        MeterReading reading = utilityUseCase.recordReading(new UtilityUseCase.RecordReadingCommand(
            submission.roomId(), submission.meterType(), value, java.time.LocalDate.now(), command.baselineValue(),
            null, trustedOcr ? submission.ocrValue() : null, trustedOcr ? submission.ocrConfidence() : null));
        try {
            reading = utilityUseCase.attachPhoto(reading.id(), submission.photoContentType(),
                storagePort.get(submission.photoKey()));
        } catch (RuntimeException ex) {
            // Chỉ số đã ghi hợp lệ; thiếu ảnh không chặn việc duyệt.
        }
        submissionRepository.save(submission.accepted(user.id(), Instant.now(), value, reading.id()));
        deleteQuietly(submission.photoKey());
        auditPort.recordEvent(AuditPort.AuditEvent.of("METER_SUBMISSION_ACCEPTED", user.id(), "ROOM",
            submission.roomId().toString(), Map.of("meterType", submission.meterType().name(),
                "match", submission.matchStatus().name(), "valueChanged", valueChanged)));
        return reading;
    }

    @Override
    public MeterSubmission reject(UUID submissionId, String reason) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MeterSubmission submission = find(submissionId);
        Room room = findRoom(submission.roomId());
        authorizationService.assertCanManageProperty(user, room.getPropertyId());
        MeterSubmission saved = submissionRepository.save(submission.rejected(user.id(), Instant.now(), reason));
        deleteQuietly(submission.photoKey());
        auditPort.recordEvent(AuditPort.AuditEvent.of("METER_SUBMISSION_REJECTED", user.id(), "ROOM",
            submission.roomId().toString(), Map.of("meterType", submission.meterType().name())));
        notifySubmitter(saved, room);
        return saved;
    }

    @Override
    public URI photoSignedUrl(UUID submissionId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MeterSubmission submission = find(submissionId);
        Room room = findRoom(submission.roomId());
        if (user != null && user.role() == UserRole.TENANT) {
            if (!user.id().equals(submission.submittedBy())) {
                throw new UnauthorizedPropertyAccessException("Bạn chỉ xem được ảnh do chính mình gửi");
            }
        } else {
            authorizationService.assertCanManageProperty(user, room.getPropertyId());
        }
        if (submission.status() != MeterSubmission.Status.PENDING) {
            throw new UtilityNotFoundException("Ảnh đã được chuyển vào chỉ số chính thức hoặc đã xóa");
        }
        return storagePort.createSignedUrl(submission.photoKey(), SIGNED_URL_TTL);
    }

    private void notifyOwner(Room room, MeterSubmission submission) {
        if (notificationRepository == null) {
            return;
        }
        propertyRepository.findById(room.getPropertyId()).map(Property::ownerId)
            .flatMap(userRepository::findById).ifPresent(owner -> notificationRepository.save(
                Notification.createGeneral("Có chỉ số điện nước chờ duyệt",
                    "Phòng " + room.getRoomNumber() + " vừa gửi chỉ số " + submission.meterType()
                        + (submission.matchStatus() == MeterSubmission.MatchStatus.MISMATCH
                            ? " (lệch với kết quả đọc ảnh)" : "") + ".",
                    owner.id(), owner.username(), owner.fullName(), owner.role())));
    }

    private void notifySubmitter(MeterSubmission submission, Room room) {
        if (notificationRepository == null || submission.submittedBy() == null) {
            return;
        }
        userRepository.findById(submission.submittedBy()).ifPresent(tenantUser -> notificationRepository.save(
            Notification.createGeneral("Chỉ số điện nước bị từ chối",
                "Chỉ số " + submission.meterType() + " phòng " + room.getRoomNumber() + " bị từ chối. Lý do: "
                    + submission.reviewNote(),
                tenantUser.id(), tenantUser.username(), tenantUser.fullName(), tenantUser.role())));
    }

    private MeterSubmission find(UUID id) {
        return submissionRepository.findById(id)
            .orElseThrow(() -> new UtilityNotFoundException("Không tìm thấy chỉ số đã gửi: " + id));
    }

    private Room findRoom(UUID roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + roomId));
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
