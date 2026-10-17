package com.phonghub.application.service;

import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.MeterOcrPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.exception.DuplicateServiceRateException;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UtilityNotFoundException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UtilityService implements UtilityUseCase {

    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);

    private final MeterReadingRepositoryPort meterRepository;
    private final ServiceRateRepositoryPort rateRepository;
    private final RoomRepositoryPort roomRepository;
    private final ContractRepositoryPort contractRepository;
    private final HandoverRepositoryPort handoverRepository;
    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final MeterOcrPort ocrPort;
    private final BigDecimal ocrMinConfidence;
    private final long maxFileSizeBytes;

    public UtilityService(
        MeterReadingRepositoryPort meterRepository,
        ServiceRateRepositoryPort rateRepository,
        RoomRepositoryPort roomRepository,
        ContractRepositoryPort contractRepository,
        HandoverRepositoryPort handoverRepository,
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        MeterOcrPort ocrPort,
        BigDecimal ocrMinConfidence,
        long maxFileSizeBytes
    ) {
        this.meterRepository = meterRepository;
        this.rateRepository = rateRepository;
        this.roomRepository = roomRepository;
        this.contractRepository = contractRepository;
        this.handoverRepository = handoverRepository;
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.ocrPort = ocrPort;
        this.ocrMinConfidence = ocrMinConfidence;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public MeterReading recordReading(RecordReadingCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(command.roomId());
        authorizationService.assertCanManageProperty(user, room.getPropertyId());
        if (command.meterType() == null || command.currentValue() == null) {
            throw new IllegalArgumentException("Meter type and current value are required");
        }

        LocalDate today = LocalDate.now();
        LocalDate date = command.readingDate() == null ? today : command.readingDate();
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Ngày ghi chỉ số không được ở tương lai");
        }
        YearMonth period = YearMonth.from(date);

        Optional<MeterReading> latest = meterRepository.findLatest(room.getId(), command.meterType());
        if (latest.isPresent() && !period.isAfter(latest.get().period())) {
            throw new MeterReadingConflictException(String.format(
                "Phòng đã có chỉ số kỳ %s; chỉ ghi được kỳ sau kỳ gần nhất.", latest.get().period()));
        }

        Contract contract = contractRepository.findActiveByRoomId(room.getId()).orElse(null);
        BigDecimal previous = resolvePrevious(command, latest.orElse(null), contract);

        MeterReading.ReplacementInfo replacement = command.replacement() == null ? null
            : new MeterReading.ReplacementInfo(command.replacement().oldFinalValue(),
                command.replacement().newStartValue(), command.replacement().note());
        assertOcrAcceptable(command);
        MeterReading reading = MeterReading.record(room.getId(), contract == null ? null : contract.getId(),
            command.meterType(), date, previous, command.currentValue(), replacement,
            command.ocrValue(), command.ocrConfidence(), user.id());
        MeterReading saved = meterRepository.save(reading);
        auditPort.recordEvent(AuditPort.AuditEvent.of("METER_READING_RECORDED", user.id(), "ROOM",
            room.getId().toString(), Map.of("meterType", saved.meterType().name(), "period", saved.period().toString(),
                "consumption", saved.consumption().toPlainString(), "replaced", saved.replaced(),
                "ocr", saved.ocrValue() != null, "ocrCorrected", saved.ocrCorrected())));
        return saved;
    }

    @Override
    public OcrSuggestion readMeterPhoto(UUID roomId, MeterType meterType, String contentType, byte[] content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(roomId);
        authorizationService.assertCanManageProperty(user, room.getPropertyId());
        if (meterType == null) {
            throw new IllegalArgumentException("Meter type is required");
        }
        if (!FileSanitizer.isImage(contentType)) {
            throw new IllegalArgumentException("Meter photos must be jpeg, png or webp");
        }
        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize(contentType, content, maxFileSizeBytes);
        MeterOcrPort.OcrResult result = ocrPort.read(meterType, file.contentType(), file.content());
        BigDecimal confidence = result.confidence() == null ? BigDecimal.ZERO : result.confidence();
        boolean manual = result.value() == null || result.value().signum() < 0
            || confidence.compareTo(ocrMinConfidence) < 0;
        auditPort.recordEvent(AuditPort.AuditEvent.of("METER_OCR_REQUESTED", user.id(), "ROOM",
            room.getId().toString(), Map.of("meterType", meterType.name(), "engine", result.engine(),
                "confidence", confidence.toPlainString(), "manualEntry", manual)));
        return new OcrSuggestion(manual ? null : result.value(), confidence, manual, result.engine());
    }

    /** Giá trị OCR phải đi cùng độ tin cậy đạt ngưỡng; dưới ngưỡng thì chỉ được nhập tay (không gửi trường OCR). */
    private void assertOcrAcceptable(RecordReadingCommand command) {
        if (command.ocrValue() == null && command.ocrConfidence() == null) {
            return;
        }
        if (command.ocrValue() == null || command.ocrConfidence() == null) {
            throw new IllegalArgumentException("ocrValue và ocrConfidence phải đi cùng nhau");
        }
        if (command.ocrConfidence().compareTo(ocrMinConfidence) < 0) {
            throw new IllegalArgumentException(
                "Độ tin cậy OCR thấp hơn ngưỡng " + ocrMinConfidence.toPlainString() + "; hãy nhập tay chỉ số");
        }
    }

    private BigDecimal resolvePrevious(RecordReadingCommand command, MeterReading latest, Contract contract) {
        BigDecimal derived = null;
        if (latest != null) {
            derived = latest.currentValue();
        } else if (contract != null) {
            derived = handoverRepository.find(contract.getId(), HandoverType.CHECK_IN)
                .filter(handover -> handover.status() == HandoverStatus.APPROVED)
                .map(handover -> initialOf(handover, command.meterType())).orElse(null);
        }
        BigDecimal baseline = command.baselineValue();
        if (derived != null) {
            if (baseline != null && baseline.compareTo(derived) != 0) {
                throw new IllegalArgumentException(
                    "Chỉ số gốc được lấy từ kỳ trước hoặc biên bản check-in, không nhập tay");
            }
            return derived;
        }
        if (baseline == null) {
            throw new IllegalArgumentException(
                "Phòng chưa có chỉ số kỳ trước hay biên bản check-in đã duyệt; hãy khai báo chỉ số gốc (baselineValue)");
        }
        return baseline;
    }

    private static BigDecimal initialOf(Handover handover, MeterType type) {
        return type == MeterType.ELECTRIC ? handover.initialElectricMeter() : handover.initialWaterMeter();
    }

    @Override
    public List<MeterReading> listReadings(UUID roomId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(roomId);
        assertCanView(user, room);
        return meterRepository.findByRoomId(roomId);
    }

    @Override
    public MeterReading attachPhoto(UUID readingId, String contentType, byte[] content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MeterReading reading = findReading(readingId);
        Room room = findRoom(reading.roomId());
        authorizationService.assertCanManageProperty(user, room.getPropertyId());
        if (!FileSanitizer.isImage(contentType)) {
            throw new IllegalArgumentException("Meter photos must be jpeg, png or webp");
        }
        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize(contentType, content, maxFileSizeBytes);
        String key = PrivateFileStorageService.objectKey(room.getPropertyId(), "meters", file.extension());
        storagePort.put(key, file.contentType(), file.content());
        MeterReading saved;
        try {
            saved = meterRepository.save(reading.withPhoto(key, user.id()));
        } catch (RuntimeException ex) {
            deleteQuietly(key);
            throw ex;
        }
        if (reading.photoKey() != null) {
            deleteQuietly(reading.photoKey());
        }
        auditPort.recordEvent(AuditPort.AuditEvent.of("METER_PHOTO_ATTACHED", user.id(), "ROOM",
            room.getId().toString(), Map.of("meterType", saved.meterType().name(), "period", saved.period().toString())));
        return saved;
    }

    @Override
    public URI photoSignedUrl(UUID readingId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MeterReading reading = findReading(readingId);
        assertCanView(user, findRoom(reading.roomId()));
        if (reading.photoKey() == null) {
            throw new UtilityNotFoundException("Meter reading has no photo");
        }
        return storagePort.createSignedUrl(reading.photoKey(), SIGNED_URL_TTL);
    }

    @Override
    public ServiceRate createRate(RateCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(user, command.propertyId());
        String name = command.serviceName() == null ? "" : command.serviceName().trim();
        if (rateRepository.findByPropertyIdAndName(command.propertyId(), name).isPresent()) {
            throw new DuplicateServiceRateException(name);
        }
        Instant now = Instant.now();
        ServiceRate saved = rateRepository.save(new ServiceRate(UUID.randomUUID(), command.propertyId(), name,
            command.chargeType(), command.pricePerUnit(), now, user.id(), now, user.id()));
        auditRate("SERVICE_RATE_CREATED", user, saved);
        return saved;
    }

    @Override
    public ServiceRate updateRate(UUID rateId, RateCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        ServiceRate rate = rateRepository.findById(rateId)
            .orElseThrow(() -> new UtilityNotFoundException("Service rate not found: " + rateId));
        authorizationService.assertOwnerOrAdmin(user, rate.propertyId());
        String name = command.serviceName() == null ? "" : command.serviceName().trim();
        Optional<ServiceRate> sameName = rateRepository.findByPropertyIdAndName(rate.propertyId(), name);
        if (sameName.isPresent() && !sameName.get().id().equals(rate.id())) {
            throw new DuplicateServiceRateException(name);
        }
        ServiceRate saved = rateRepository.save(rate.update(name, command.chargeType(), command.pricePerUnit(),
            user.id()));
        auditRate("SERVICE_RATE_UPDATED", user, saved);
        return saved;
    }

    @Override
    public List<ServiceRate> listRates(UUID propertyId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(user, propertyId);
        return rateRepository.findByPropertyId(propertyId);
    }

    private void auditRate(String action, CurrentUser user, ServiceRate rate) {
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, user.id(), "SERVICE_RATE", rate.id().toString(),
            Map.of("propertyId", rate.propertyId().toString(), "serviceName", rate.serviceName(),
                "chargeType", rate.chargeType().name(), "pricePerUnit", rate.pricePerUnit().toPlainString())));
    }

    private void assertCanView(CurrentUser user, Room room) {
        if (user != null && user.isTenant()) {
            if (!authorizationService.canTenantViewRoom(user, room.getId())) {
                throw new UnauthorizedPropertyAccessException("Tenant has no active occupancy in room " + room.getId());
            }
            return;
        }
        authorizationService.assertCanAccessProperty(user, room.getPropertyId());
    }

    private Room findRoom(UUID roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room id is required");
        }
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));
    }

    private MeterReading findReading(UUID readingId) {
        return meterRepository.findById(readingId)
            .orElseThrow(() -> new UtilityNotFoundException("Meter reading not found: " + readingId));
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
