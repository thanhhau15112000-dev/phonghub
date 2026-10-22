package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Chỉ số điện/nước do người thuê gửi kèm ảnh đồng hồ. Server đối chiếu số khai với kết quả OCR và gắn
 * {@link MatchStatus}; chỉ khi owner/staff duyệt mới thành chỉ số chính thức.
 */
public record MeterSubmission(
    UUID id,
    UUID roomId,
    MeterType meterType,
    BigDecimal declaredValue,
    String photoKey,
    String photoContentType,
    BigDecimal ocrValue,
    BigDecimal ocrConfidence,
    String ocrEngine,
    MatchStatus matchStatus,
    Status status,
    UUID submittedBy,
    Instant submittedAt,
    UUID reviewedBy,
    Instant reviewedAt,
    String reviewNote,
    BigDecimal finalValue,
    UUID readingId
) {
    public enum Status { PENDING, ACCEPTED, REJECTED }

    /** MATCH: OCR đủ tin cậy và khớp số khai. MISMATCH: OCR đủ tin cậy nhưng lệch. UNVERIFIED: OCR không đọc được. */
    public enum MatchStatus { MATCH, MISMATCH, UNVERIFIED }

    public MeterSubmission {
        if (id == null || roomId == null || meterType == null || photoKey == null || photoKey.isBlank()
            || matchStatus == null || status == null) {
            throw new IllegalArgumentException("Submission requires id, room, type, photo, match status and status");
        }
        if (declaredValue == null || declaredValue.signum() < 0) {
            throw new IllegalArgumentException("Chỉ số khai báo không được âm.");
        }
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    public static MatchStatus match(BigDecimal declared, BigDecimal ocrValue, BigDecimal confidence,
                                    BigDecimal minConfidence, BigDecimal tolerance) {
        if (ocrValue == null || confidence == null || confidence.compareTo(minConfidence) < 0) {
            return MatchStatus.UNVERIFIED;
        }
        return declared.subtract(ocrValue).abs().compareTo(tolerance) <= 0 ? MatchStatus.MATCH : MatchStatus.MISMATCH;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new com.phonghub.domain.exception.MeterReadingConflictException(
                "Lượt gửi chỉ số này đã được xử lý (" + status + ")");
        }
    }

    public MeterSubmission accepted(UUID reviewer, Instant now, BigDecimal value, UUID readingId) {
        requirePending();
        return new MeterSubmission(id, roomId, meterType, declaredValue, photoKey, photoContentType, ocrValue,
            ocrConfidence, ocrEngine, matchStatus, Status.ACCEPTED, submittedBy, submittedAt, reviewer, now, null,
            value, readingId);
    }

    public MeterSubmission rejected(UUID reviewer, Instant now, String reason) {
        requirePending();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nêu lý do từ chối");
        }
        return new MeterSubmission(id, roomId, meterType, declaredValue, photoKey, photoContentType, ocrValue,
            ocrConfidence, ocrEngine, matchStatus, Status.REJECTED, submittedBy, submittedAt, reviewer, now,
            reason.trim(), null, null);
    }
}
